package com.rayner.peregrine

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.rayner.peregrine.data.repository.FrigateRepositoryImpl
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PeregrineApp : Application() {

    @Inject
    lateinit var repository: FrigateRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        CoroutineScope(Dispatchers.IO).launch {
            repository.restorePersistedAuthCookie()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.default_notification_channel_id)
            val name = getString(R.string.default_notification_channel_name)
            val descriptionText = getString(R.string.default_notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
