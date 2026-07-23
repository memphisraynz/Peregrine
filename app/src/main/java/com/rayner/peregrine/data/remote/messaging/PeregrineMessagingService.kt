package com.rayner.peregrine.data.remote.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rayner.peregrine.MainActivity
import com.rayner.peregrine.R
import com.rayner.peregrine.data.local.entity.PreferenceEntity
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class PeregrineMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var repository: FrigateRepository

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        Log.d(TAG, "Message received! From: ${remoteMessage.from}")
        Log.d(TAG, "Data payload: $data")

        // Ensure the repository is initialized with the correct URL and auth cookies immediately.
        runBlocking {
            repository.restorePersistedAuthCookie()
        }

        val title = data["title"] ?: remoteMessage.notification?.title ?: "Frigate Alert"
        val body = data["message"] ?: data["body"] ?: remoteMessage.notification?.body ?: "Detection"
        val url = data["url"] ?: data["click_action"]
        val tag = data["tag"]
        val group = data["group"]
        val status = data["status"]
        val alertOnce = data["alert_once"]?.toBoolean() ?: false

        val prefs = runBlocking { repository.getPreferencesFlow().firstOrNull() ?: PreferenceEntity() }

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
        val (notificationTag, notificationId) = when {
            prefs.showLatestOnly -> channelId to 0
            tag != null -> tag to 0
            else -> null to Random.nextInt()
        }

        // 1. Determine image candidates and handle first attempt immediately for zero latency
        val imageCandidates = listOfNotNull(
            data["image"],
            data["thumbnail"],
            data["photo"],
            data["snapshot"],
            data["image_url"],
            remoteMessage.notification?.imageUrl?.toString()
        ).distinct()

        var firstAttemptSuccess = false
        if (imageCandidates.isNotEmpty()) {
            runBlocking {
                try {
                    val config = repository.getServerConfig().firstOrNull()
                    val baseUrl = config?.serverUrl?.removeSuffix("/")
                    val authCookie = config?.authCookie
                    
                    val rawImageUrl = imageCandidates.first()
                    val fullImageUrl = if (!rawImageUrl.startsWith("http") && baseUrl != null) {
                        "$baseUrl${if (rawImageUrl.startsWith("/")) "" else "/"}$rawImageUrl"
                    } else {
                        rawImageUrl
                    }

                    Log.d(TAG, "Attempting instant direct fetch: $fullImageUrl")
                    val startTime = System.currentTimeMillis()

                    val requestBuilder = ImageRequest.Builder(this@PeregrineMessagingService)
                        .data(fullImageUrl)
                        .allowHardware(false)
                        .size(640, 640)
                    
                    if (authCookie != null) {
                        val headers = NetworkHeaders.Builder()
                            .add("Cookie", "frigate_token=$authCookie")
                            .build()
                        requestBuilder.httpHeaders(headers)
                    }
                    
                    val result = imageLoader.execute(requestBuilder.build())
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                        Log.d(TAG, "Instant fetch succeeded in ${System.currentTimeMillis() - startTime}ms")
                        sendRichNotification(notificationId, title, body, url, bitmap, actions, notificationTag, alertOnce, channelId, tag)
                        firstAttemptSuccess = true
                    } else {
                        Log.w(TAG, "Instant fetch failed, will fallback to worker")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Instant fetch exception", e)
                }
            }
        }

        // 2. If no image or first attempt failed, show text-only alert immediately
        if (!firstAttemptSuccess) {
            sendRichNotification(notificationId, title, body, url, null, actions, notificationTag, alertOnce, channelId, tag)
            
            // 3. Offload to WorkManager if there are candidates left to try
            if (imageCandidates.isNotEmpty()) {
                val workRequest = OneTimeWorkRequestBuilder<NotificationImageWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(
                        NotificationImageWorker.createInputData(
                            title = title,
                            body = body,
                            url = url,
                            tag = notificationTag,
                            channelId = channelId,
                            eventId = tag,
                            notificationId = notificationId,
                            imageCandidates = imageCandidates
                        )
                    )
                    .build()

                WorkManager.getInstance(this).enqueue(workRequest)
                Log.d(TAG, "Scheduled fallback image download worker for $title")
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
        eventId: String?
    ) {
        NotificationHelper.sendRichNotification(
            context = this,
            notificationId = notificationId,
            title = title,
            body = body,
            url = url,
            bitmap = bitmap,
            actions = actions,
            tag = tag,
            alertOnce = alertOnce,
            channelId = channelId,
            eventId = eventId
        )
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New Token: $token")
    }

    data class NotificationAction(val label: String, val url: String)

    companion object {
        private const val TAG = "PeregrineFCM"
    }
}
