package com.rayner.peregrine.data.remote.messaging

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.rayner.peregrine.MainActivity
import com.rayner.peregrine.R
import kotlin.random.Random

object NotificationHelper {

    fun sendRichNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        url: String?,
        bitmap: Bitmap?,
        actions: List<PeregrineMessagingService.NotificationAction>,
        tag: String?,
        alertOnce: Boolean,
        channelId: String,
        eventId: String?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (url != null) data = url.toUri()
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setColor(ContextCompat.getColor(context, R.color.purple_500))
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
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as Bitmap?))
        }

        actions.forEachIndexed { index, action ->
            val actionIntent = Intent(Intent.ACTION_VIEW, action.url.toUri()).apply {
                `package` = context.packageName
            }
            val actionPendingIntent = PendingIntent.getActivity(
                context,
                action.url.hashCode() + index,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, action.label, actionPendingIntent)
        }

        notificationManager.notify(tag, notificationId, builder.build())
    }
}
