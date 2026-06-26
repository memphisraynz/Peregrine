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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class PeregrineMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var imageLoader: ImageLoader

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
        val alertOnce = data["alert_once"]?.toBoolean() ?: false

        createNotificationChannel()

        val actions = mutableListOf<NotificationAction>()
        for (i in 1..3) {
            val label = data["button_$i"]
            val actionUrl = data["url_$i"]
            if (!label.isNullOrBlank() && !actionUrl.isNullOrBlank()) {
                actions.add(NotificationAction(label, actionUrl))
            }
        }

        // Fetch bitmap with a strict timeout to avoid service termination
        val bitmap = imageUrl?.let {
            runBlocking {
                try {
                    withTimeoutOrNull(6000) {
                        val request = ImageRequest.Builder(this@PeregrineMessagingService)
                            .data(it)
                            .build()
                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) result.image.toBitmap() else null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Image fetch failed for $it", e)
                    null
                }
            }
        }

        sendRichNotification(title, body, url, bitmap, actions, tag, alertOnce)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.default_notification_channel_id)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Frigate Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Frigate event notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun sendRichNotification(
        title: String,
        body: String,
        url: String?,
        bitmap: Bitmap?,
        actions: List<NotificationAction>,
        tag: String?,
        alertOnce: Boolean
    ) {
        val channelId = getString(R.string.default_notification_channel_id)
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

        // Use the 'tag' from payload as the notification ID to allow overwriting/updating
        val notificationId = tag?.hashCode() ?: Random.nextInt()
        
        Log.d(TAG, "Posting notification: $title (ID: $notificationId, Tag: $tag, Image: ${bitmap != null})")
        notificationManager.notify(notificationId, builder.build())
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New Token: $token")
    }

    data class NotificationAction(val label: String, val url: String)

    companion object {
        private const val TAG = "PeregrineFCM"
    }
}
