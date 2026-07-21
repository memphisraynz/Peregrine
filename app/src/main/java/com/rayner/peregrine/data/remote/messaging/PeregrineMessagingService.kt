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
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
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
import kotlinx.coroutines.withTimeoutOrNull
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

        val title = data["title"] ?: remoteMessage.notification?.title ?: "Frigate Alert"
        val body = data["message"] ?: data["body"] ?: remoteMessage.notification?.body ?: "Detection"
        val url = data["url"] ?: data["click_action"]
        val imageUrl = data["image"] ?: data["photo"] ?: data["thumbnail"]
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
        // If showLatestOnly is true, we use the channelId as a tag to overwrite any existing notification in that channel
        val (notificationTag, notificationId) = when {
            prefs.showLatestOnly -> channelId to 0
            tag != null -> tag to 0
            else -> null to Random.nextInt()
        }

        // 1. Show the notification immediately without an image to ensure the user gets the alert ASAP
        sendRichNotification(notificationId, title, body, url, null, actions, notificationTag, alertOnce, channelId, tag)

        // 2. If there's an image, attempt to download it and update the notification
        if (imageUrl != null) {
            runBlocking {
                try {
                    // Ensure the repository is initialized with the correct URL and auth cookies
                    // This is crucial if the app process was cold-started by this FCM message
                    repository.restorePersistedAuthCookie()

                    val baseUrl = repository.getServerConfig().firstOrNull()?.serverUrl?.removeSuffix("/")
                    val fullImageUrl = if (!imageUrl.startsWith("http") && baseUrl != null) {
                        "$baseUrl${if (imageUrl.startsWith("/")) "" else "/"}$imageUrl"
                    } else {
                        imageUrl
                    }

                    var bitmap: Bitmap? = null
                    var lastError: String? = null
                    val maxRetries = 3
                    
                    for (retry in 0 until maxRetries) {
                        if (retry > 0) {
                            val delayMs = 2000L * retry
                            Log.d(TAG, "Retry $retry for image $fullImageUrl after ${delayMs}ms")
                            kotlinx.coroutines.delay(delayMs)
                        }

                        bitmap = withTimeoutOrNull(10000) { // Increased timeout to 10s
                            try {
                                val request = ImageRequest.Builder(this@PeregrineMessagingService)
                                    .data(fullImageUrl)
                                    .build()
                                val result = imageLoader.execute(request)
                                if (result is SuccessResult) {
                                    result.image.toBitmap()
                                } else {
                                    val error = (result as? coil3.request.ErrorResult)?.throwable?.message ?: "Unknown error"
                                    lastError = error
                                    Log.w(TAG, "Image fetch failed (Attempt ${retry + 1}): $error")
                                    null
                                }
                            } catch (e: Exception) {
                                lastError = e.message
                                Log.e(TAG, "Exception during image fetch (Attempt ${retry + 1})", e)
                                null
                            }
                        }

                        if (bitmap != null) break
                    }

                    if (bitmap != null) {
                        Log.d(TAG, "Image downloaded successfully, updating notification")
                        sendRichNotification(notificationId, title, body, url, bitmap, actions, notificationTag, alertOnce, channelId, tag)
                    } else {
                        Log.e(TAG, "Image download failed after $maxRetries attempts. Last error: $lastError")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Image fetch orchestration failed", e)
                }
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

        if (eventId != null) {
            builder.addExtras(android.os.Bundle().apply {
                putString("frigate_event_id", eventId)
            })
        }

        if (bitmap != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
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
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New Token: $token")
    }

    data class NotificationAction(val label: String, val url: String)

    companion object {
        private const val TAG = "PeregrineFCM"
    }
}
