package com.rayner.peregrine.data.remote.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rayner.peregrine.MainActivity
import com.rayner.peregrine.R
import com.rayner.peregrine.data.local.entity.PreferenceEntity
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class PeregrineMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var repository: FrigateRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        try {
            val app = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase initialized: ${app.name}, Project: ${app.options.projectId}")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization check failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        Log.d(TAG, "Message received! From: ${remoteMessage.from}")
        Log.d(TAG, "Data payload: $data")

        // Doze can suspend network access (including DNS) for a background service shortly
        // after FCM wakes the device for onMessageReceived. Hold the CPU awake for the whole
        // operation - not just the initial wake-up - so the image-fetch retry loop (which can
        // run for tens of seconds between timeouts and delays) doesn't get cut off mid-flight.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Peregrine:fcmMessage")
        wakeLock.acquire(60_000L)

        // We must use runBlocking here because FirebaseMessagingService may destroy
        // the service as soon as onMessageReceived returns.
        // runBlocking ensures the service stays alive until the notification is posted and images are fetched.
        runBlocking {
            try {
                handleMessage(remoteMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle message", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private suspend fun handleMessage(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        
        // Ensure ServerUrlManager knows the current server URL immediately; the auth
        // cookie itself is read directly from the database by the shared CookieJar,
        // so there's nothing else to restore here.
        repository.restoreServerUrl()

        val title = data["title"] ?: remoteMessage.notification?.title ?: "Frigate Alert"
        val body = data["message"] ?: data["body"] ?: remoteMessage.notification?.body ?: "Detection"
        val url = data["url"] ?: data["click_action"]
        val tag = data["tag"]
        val group = data["group"]
        val status = data["status"]
        val alertOnce = data["alert_once"]?.toBoolean() ?: false

        val prefs = repository.getPreferencesFlow().firstOrNull() ?: PreferenceEntity()

        val channelId = group ?: getString(R.string.default_notification_channel_id)

        // Check if this is an update and if the notification is still active
        if (status != null && status != "new" && tag != null) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isActive = if (prefs.showLatestOnly) {
                notificationManager.activeNotifications.any {
                    it.tag == channelId && it.notification.extras.getString("frigate_event_id") == tag
                }
            } else {
                notificationManager.activeNotifications.any { it.tag == tag }
            }

            if (!isActive) {
                Log.d(TAG, "Ignoring update for tag $tag as it's not the active notification for $channelId")
                return
            }
        }

        val channelName = formatChannelName(group)
        createNotificationChannel(channelId, channelName)

        val actions = mutableListOf<NotificationAction>()
        for (i in 1..3) {
            val label = data["button_$i"]
            val actionUrl = data["url_$i"]
            if (!label.isNullOrBlank() && !actionUrl.isNullOrBlank()) {
                actions.add(NotificationAction(label, actionUrl))
            }
        }

        // Determine the ID and Tag for the notification
        // We now always use the event tag if available to ensure each event's image download
        // can complete independently. Native grouping handles the "latest only" UX.
        val (notificationTag, notificationId) = when {
            tag != null -> tag to 0
            else -> null to Random.nextInt()
        }

        // 1. Show the notification immediately without an image to ensure the user gets the alert ASAP
        sendRichNotification(notificationId, title, body, url, null, actions, notificationTag, alertOnce, channelId, tag, prefs.showLatestOnly)

        // 2. If there's an image, attempt to download it and update the notification
        val imageCandidates = listOfNotNull(
            data["image"],
            data["photo"],
            data["thumbnail"],
            data["snapshot"],
            data["image_url"],
            remoteMessage.notification?.imageUrl?.toString()
        ).distinct()

        if (imageCandidates.isNotEmpty()) {
            try {
                val baseUrl = repository.getServerConfig().firstOrNull()?.serverUrl?.removeSuffix("/")
                
                var bitmap: Bitmap? = null
                var lastError: String? = null
                val maxRetries = 3 // Increased retries to try different candidates
                
                for (retry in 0 until maxRetries) {
                    // Pick a candidate URL. Try to cycle through them if there are multiple.
                    val rawImageUrl = imageCandidates[retry % imageCandidates.size]
                    val fullImageUrl = if (!rawImageUrl.startsWith("http") && baseUrl != null) {
                        "$baseUrl${if (rawImageUrl.startsWith("/")) "" else "/"}$rawImageUrl"
                    } else {
                        rawImageUrl
                    }

                    if (retry > 0) {
                        val delayMs = 1500L * retry
                        Log.d(TAG, "Retry $retry for image $fullImageUrl after ${delayMs}ms")
                        kotlinx.coroutines.delay(delayMs)
                    }

                    Log.d(TAG, "Attempting to fetch image: $fullImageUrl (Attempt ${retry + 1})")

                    val startTime = System.currentTimeMillis()
                    // 20s for the first attempt to allow for cold radio + potential auto-login
                    val timeout = if (retry == 0) 20000L else 12000L
                    
                    bitmap = withTimeoutOrNull(timeout) {
                        try {
                            val request = ImageRequest.Builder(this@PeregrineMessagingService)
                                .data(fullImageUrl)
                                .allowHardware(false) // Required for RemoteViews
                                .size(1024, 1024)    // Reasonable limit for notifications
                                .build()
                            val result = imageLoader.execute(request)
                            val duration = System.currentTimeMillis() - startTime
                            if (result is SuccessResult) {
                                Log.d(TAG, "Image fetch successful (Attempt ${retry + 1}) in ${duration}ms")
                                result.image.toBitmap()
                            } else {
                                val errorResult = result as? coil3.request.ErrorResult
                                val throwable = errorResult?.throwable
                                val error = throwable?.message ?: "Unknown error"
                                lastError = error

                                Log.w(TAG, "Image fetch failed (Attempt ${retry + 1}) after ${duration}ms: $error")
                                if (throwable is coil3.network.HttpException) {
                                    // Identify who actually answered (Server/Via/CF-RAY/Date headers),
                                    // since a clean HTTP status means some server completed the
                                    // request - this pins down which one.
                                    Log.w(TAG, "Response headers for failed fetch: ${throwable.response.headers}")
                                    // requestMillis == -1 is OkHttp's own hard-coded marker for a response
                                    // it fabricated locally (e.g. an only-if-cached miss) - no request was
                                    // ever sent. Any other value means a real round trip actually happened.
                                    Log.w(TAG, "requestMillis=${throwable.response.requestMillis} responseMillis=${throwable.response.responseMillis}")
                                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                    val activeNetwork = cm.activeNetwork
                                    val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
                                    Log.w(TAG, "ConnectivityManager: activeNetwork=$activeNetwork hasInternetCapability=${caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)} hasValidated=${caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
                                }
                                null
                            }
                        } catch (e: Exception) {
                            val duration = System.currentTimeMillis() - startTime
                            lastError = e.message
                            Log.e(TAG, "Exception during image fetch (Attempt ${retry + 1}) after ${duration}ms", e)
                            null
                        }
                    }

                    if (bitmap != null) break
                }

                if (bitmap != null) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val stillActive = notificationManager.activeNotifications.any { it.tag == notificationTag }

                    if (stillActive) {
                        Log.d(TAG, "Image downloaded successfully, updating notification")
                        sendRichNotification(notificationId, title, body, url, bitmap, actions, notificationTag, alertOnce, channelId, tag, prefs.showLatestOnly)
                    } else {
                        Log.d(TAG, "Notification was dismissed or replaced while downloading image, skipping update")
                    }
                } else {
                    Log.e(TAG, "Image download failed after $maxRetries attempts. Last error: $lastError")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image fetch orchestration failed", e)
            }
        }
    }

    private fun createNotificationChannel(channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Frigate event notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun formatChannelName(group: String?): String {
        if (group == null) return getString(R.string.default_notification_channel_name)

        return group.removeSuffix("-frigate-notification")
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun sendRichNotification(
        notificationId: Int,
        title: String,
        body: String,
        url: String?,
        bitmap: Bitmap?,
        actions: List<NotificationAction>,
        tag: String?,
        alertOnce: Boolean,
        channelId: String,
        eventId: String?,
        showLatestOnly: Boolean
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (url != null) data = url.toUri()
        }

        // Use a unique request code to prevent intent reuse issues
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(this, R.color.purple_500))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(alertOnce)
            .setGroup(channelId) // Native grouping

        if (showLatestOnly) {
            // When showLatestOnly is enabled, individual notifications should not make noise
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }

        if (eventId != null) {
            builder.addExtras(android.os.Bundle().apply {
                putString("frigate_event_id", eventId)
            })
        }

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?))
        }

        actions.forEachIndexed { index, action ->
            val actionIntent = Intent(Intent.ACTION_VIEW, action.url.toUri()).apply {
                `package` = packageName
            }
            val actionPendingIntent = PendingIntent.getActivity(
                this,
                action.url.hashCode() + index,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, action.label, actionPendingIntent)
        }

        Log.d(TAG, "Posting notification: $title (ID: $notificationId, Tag: $tag, EventId: $eventId, Image: ${bitmap != null})")
        notificationManager.notify(tag, notificationId, builder.build())

        // Post/Update the summary notification for the group
        val summaryBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(this, R.color.purple_500))
            .setContentTitle(title)
            .setContentText(if (showLatestOnly) body else "New detections in $channelId")
            .setGroup(channelId)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(alertOnce)

        if (showLatestOnly) {
            summaryBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }

        notificationManager.notify(channelId, 0, summaryBuilder.build())
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New Token: $token")
    }

    data class NotificationAction(val label: String, val url: String)

    companion object {
        private const val TAG = "PeregrineFCM"
    }
}
