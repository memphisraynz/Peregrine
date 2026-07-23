package com.rayner.peregrine.data.remote.messaging

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.toBitmap
import com.rayner.peregrine.R
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

@HiltWorker
class NotificationImageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val imageLoader: ImageLoader,
    private val repository: FrigateRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val url = inputData.getString(KEY_URL)
        val tag = inputData.getString(KEY_TAG)
        val channelId = inputData.getString(KEY_CHANNEL_ID) ?: return Result.failure()
        val eventId = inputData.getString(KEY_EVENT_ID)
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)
        val imageCandidates = inputData.getStringArray(KEY_IMAGE_CANDIDATES) ?: emptyArray()

        if (imageCandidates.isEmpty()) return Result.success()

        Log.d(TAG, "Starting image download worker for $title (Tag: $tag)")

        try {
            repository.restorePersistedAuthCookie()
            val config = repository.getServerConfig().firstOrNull()
            val baseUrl = config?.serverUrl?.removeSuffix("/")
            val authCookie = config?.authCookie
            
            Log.d(TAG, "Auth cookie present: ${authCookie != null}")

            var bitmap: Bitmap? = null
            var lastError: String? = null
            val maxRetries = 4
            
            for (retry in 0 until maxRetries) {
                val rawImageUrl = imageCandidates[retry % imageCandidates.size]
                val fullImageUrl = if (!rawImageUrl.startsWith("http") && baseUrl != null) {
                    "$baseUrl${if (rawImageUrl.startsWith("/")) "" else "/"}$rawImageUrl"
                } else {
                    rawImageUrl
                }

                if (retry > 0) {
                    // Adaptive delay: 1.5s if the first try failed, then 500ms for subsequent tries
                    val delayMs = if (retry == 1) 1500.milliseconds else 500.milliseconds
                    kotlinx.coroutines.delay(delayMs)
                }

                Log.d(TAG, "Attempting fetch in worker: $fullImageUrl (Attempt ${retry + 1})")
                val startTime = System.currentTimeMillis()

                bitmap = withTimeoutOrNull(6000.milliseconds) { // 6s balanced timeout
                    try {
                        val requestBuilder = ImageRequest.Builder(applicationContext)
                            .data(fullImageUrl)
                            .allowHardware(false)
                            .size(640, 640)    // Smaller target size for faster processing
                        
                        if (authCookie != null) {
                            val headers = NetworkHeaders.Builder()
                                .add("Cookie", "frigate_token=$authCookie")
                                .build()
                            requestBuilder.httpHeaders(headers)
                        }
                        
                        val result = imageLoader.execute(requestBuilder.build())
                        val duration = System.currentTimeMillis() - startTime
                        
                        if (result is SuccessResult) {
                            Log.d(TAG, "Image fetch succeeded in ${duration}ms (Attempt ${retry + 1})")
                            result.image.toBitmap()
                        } else {
                            val error = (result as? coil3.request.ErrorResult)?.throwable?.message ?: "Unknown error"
                            lastError = error
                            Log.w(TAG, "Image fetch failed in ${duration}ms (Attempt ${retry + 1}): $error")
                            null
                        }
                    } catch (e: Exception) {
                        lastError = e.message
                        Log.e(TAG, "Exception during image fetch after ${System.currentTimeMillis() - startTime}ms (Attempt ${retry + 1})", e)
                        null
                    }
                }

                if (bitmap != null) break
            }

            if (bitmap != null) {
                Log.d(TAG, "Worker downloaded image successfully, updating notification")
                updateNotification(notificationId, tag, title, body, url, bitmap, channelId, eventId)
                return Result.success()
            } else {
                Log.e(TAG, "Worker failed to download image after retries. Last error: $lastError")
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker encountered exception", e)
            return Result.failure()
        }
    }

    private fun updateNotification(
        id: Int,
        tag: String?,
        title: String,
        body: String,
        url: String?,
        bitmap: Bitmap,
        channelId: String,
        eventId: String?
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val stillActive = notificationManager.activeNotifications.any { 
            if (tag != null) it.tag == tag else it.id == id 
        }
        
        if (!stillActive) {
            Log.d(TAG, "Notification no longer active, skipping update")
            return
        }

        NotificationHelper.sendRichNotification(
            context = applicationContext,
            notificationId = id,
            title = title,
            body = body,
            url = url,
            bitmap = bitmap,
            actions = emptyList(),
            tag = tag,
            alertOnce = true,
            channelId = channelId,
            eventId = eventId
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "frigate_alerts")
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Downloading notification image...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "NotificationWorker"
        private const val NOTIFICATION_ID = 999
        
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_URL = "url"
        const val KEY_TAG = "tag"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val KEY_IMAGE_CANDIDATES = "image_candidates"

        fun createInputData(
            title: String,
            body: String,
            url: String?,
            tag: String?,
            channelId: String,
            eventId: String?,
            notificationId: Int,
            imageCandidates: List<String>
        ): Data {
            return Data.Builder()
                .putString(KEY_TITLE, title)
                .putString(KEY_BODY, body)
                .putString(KEY_URL, url)
                .putString(KEY_TAG, tag)
                .putString(KEY_CHANNEL_ID, channelId)
                .putString(KEY_EVENT_ID, eventId)
                .putInt(KEY_NOTIFICATION_ID, notificationId)
                .putStringArray(KEY_IMAGE_CANDIDATES, imageCandidates.toTypedArray())
                .build()
        }
    }
}
