package com.rayner.peregrine.data.repository

import com.rayner.peregrine.data.local.dao.CameraDao
import com.rayner.peregrine.data.local.dao.ExploreDao
import com.rayner.peregrine.data.local.dao.ReviewDao
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.CameraEntity
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.data.remote.api.FrigateApiService
import com.rayner.peregrine.data.remote.model.ReviewViewedRequest
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.domain.repository.FrigateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrigateRepositoryImpl @Inject constructor(
    private val apiService: FrigateApiService,
    private val serverConfigDao: ServerConfigDao,
    private val reviewDao: ReviewDao,
    private val exploreDao: ExploreDao,
    private val cameraDao: CameraDao,
    private val preferenceDao: com.rayner.peregrine.data.local.dao.PreferenceDao,
    private val okHttpClient: OkHttpClient,
    private val cookieJar: CookieJar,
    private val serverUrlManager: com.rayner.peregrine.data.remote.api.ServerUrlManager
) : FrigateRepository {

    override fun getServerConfig(): Flow<ServerConfigEntity?> {
        return serverConfigDao.getServerConfig()
    }

    override fun getPreferencesFlow(): Flow<com.rayner.peregrine.data.local.entity.PreferenceEntity?> = preferenceDao.getPreferences()

    override suspend fun updatePreferences(prefs: com.rayner.peregrine.data.local.entity.PreferenceEntity) {
        preferenceDao.insertPreferences(prefs)
    }

    override suspend fun updateServerConfig(config: ServerConfigEntity) {
        serverConfigDao.insertServerConfig(config)
    }

    override suspend fun clearServerConfig() {
        serverConfigDao.clearConfig()
        reviewDao.clearAll()
        exploreDao.clearAll()
        cameraDao.clearAll()
    }

    suspend fun restorePersistedAuthCookie() {
        val config = serverConfigDao.getServerConfig().firstOrNull() ?: return
        serverUrlManager.setUrl(config.serverUrl)
        val baseUrl = config.serverUrl.toHttpUrlOrNull() ?: return
        val tokenCookie = config.authCookie ?: return
        val expiresAt = config.authCookieExpiresAt ?: return
        if (expiresAt <= System.currentTimeMillis()) return

        val cookieBuilder = Cookie.Builder()
            .name("frigate_token")
            .value(tokenCookie)
            .expiresAt(expiresAt)
            .httpOnly()
            .path("/")
            .hostOnlyDomain(baseUrl.host)
            
        if (baseUrl.isHttps) {
            cookieBuilder.secure()
        }
        
        val cookie = cookieBuilder.build()

        cookieJar.saveFromResponse(baseUrl, listOf(cookie))
    }

    private suspend fun getBaseUrl(): String {
        val dbUrl = serverConfigDao.getServerConfig().firstOrNull()?.serverUrl?.removeSuffix("/")
        return dbUrl ?: serverUrlManager.getUrl()?.removeSuffix("/") ?: ""
    }

    private suspend fun persistAuthCookie(baseUrl: String) {
        val existingConfig = serverConfigDao.getServerConfig().firstOrNull()
        val normalizedBaseUrl = baseUrl.removeSuffix("/")
        val url = normalizedBaseUrl.toHttpUrlOrNull() ?: return
        val cookies = cookieJar.loadForRequest(url)
        val authCookie = cookies.firstOrNull { it.name == "frigate_token" } ?: return
        val config = existingConfig ?: ServerConfigEntity(serverUrl = normalizedBaseUrl)

        serverConfigDao.insertServerConfig(
            config.copy(
                serverUrl = normalizedBaseUrl,
                authCookie = authCookie.value,
                authCookieExpiresAt = authCookie.expiresAt,
                isLoggedIn = true
            )
        )
    }

    override suspend fun login(user: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("Server URL not configured"))

            val json = JSONObject().put("user", user).put("password", password).toString()
            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/login")
                .post(body)
                .header("User-Agent", "FrigateViewer/1.0 AuthManager")
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.use { r ->
                if (r.isSuccessful) {
                    persistAuthCookie(baseUrl)
                    Result.success(Unit)
                } else {
                    val errorMsg = r.body?.string() ?: ""
                    Result.failure(Exception("HTTP ${r.code}: $errorMsg"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVersion(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val request = Request.Builder().url("$baseUrl/api/version").build()
            okHttpClient.newCall(request).execute().use { r ->
                if (r.isSuccessful) Result.success(r.body?.string() ?: "")
                else Result.failure(Exception("HTTP ${r.code}"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun getReviewItemsFlow(): Flow<List<ReviewItemEntity>> = reviewDao.getReviewItems()

    override suspend fun refreshReviewItems(
        limit: Int?,
        severity: String?,
        reviewed: Int?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val items = apiService.getReviewItems(
                limit = limit,
                severity = severity,
                reviewed = reviewed
            )
            val entities = items.map { item ->
                val data = item["data"] as? Map<*, *>
                val objects = (data?.get("objects") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val subLabels = (data?.get("sub_labels") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                
                val hasBeenReviewed = when {
                    item["has_been_reviewed"] is Boolean -> item["has_been_reviewed"] as Boolean
                    item["has_been_reviewed"] is Number -> (item["has_been_reviewed"] as Number).toInt() != 0
                    item["reviewed"] is Boolean -> item["reviewed"] as Boolean
                    item["reviewed"] is Number -> (item["reviewed"] as Number).toInt() != 0
                    else -> false // Default to false so new alerts show up if field is missing
                }
                
                ReviewItemEntity(
                    id = item["id"] as? String ?: "",
                    camera = item["camera"] as? String ?: "",
                    severity = item["severity"] as? String ?: "",
                    startTime = (item["start_time"] as? Number)?.toDouble() ?: 0.0,
                    endTime = (item["end_time"] as? Number)?.toDouble(),
                    thumbPath = item["thumb_path"] as? String ?: "",
                    hasBeenReviewed = hasBeenReviewed,
                    primaryLabel = objects.firstOrNull(),
                    objects = objects,
                    subLabels = subLabels
                )
            }
            reviewDao.insertReviewItems(entities)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun getExploreEventsFlow(): Flow<List<ExploreEventEntity>> = exploreDao.getExploreEvents()

    override suspend fun refreshExploreEvents(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val events = apiService.getEvents()
            val baseUrl = getBaseUrl()
            val entities = events.map { event ->
                val id = event["id"] as? String ?: ""
                ExploreEventEntity(
                    id = id,
                    camera = event["camera"] as? String ?: "",
                    label = event["label"] as? String ?: "",
                    startTime = (event["start_time"] as? Number)?.toDouble() ?: 0.0,
                    endTime = (event["end_time"] as? Number)?.toDouble(),
                    thumbUrl = "$baseUrl/api/events/$id/thumbnail.jpg"
                )
            }
            exploreDao.insertExploreEvents(entities)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun getCamerasFlow(): Flow<List<CameraEntity>> = cameraDao.getCameras()

    override suspend fun refreshCameras(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prefs = preferenceDao.getPreferences().firstOrNull()
            val defaultUseHls = prefs?.defaultPlayerType == "hls"
            
            val baseUrl = getBaseUrl()
            val config = apiService.getConfig()
            val camerasMap = config["cameras"] as? Map<String, Any> ?: emptyMap()

            val entities = camerasMap.mapNotNull { (cameraName, cameraConfigAny) ->
                val cameraConfig = cameraConfigAny as? Map<*, *> ?: return@mapNotNull null
                val live = cameraConfig["live"] as? Map<*, *>
                val streams = live?.get("streams") as? Map<*, *>
                val preferredStream = streams?.values?.firstOrNull() as? String ?: cameraName

                val detect = cameraConfig["detect"] as? Map<*, *>
                val width = (detect?.get("width") as? Number)?.toInt() ?: 1920
                val height = (detect?.get("height") as? Number)?.toInt() ?: 1080

                CameraEntity(
                    name = cameraName,
                    width = width,
                    height = height,
                    mjpegUrl = "$baseUrl/api/go2rtc/streams/$preferredStream",
                    snapshotUrl = "$baseUrl/api/$cameraName/latest.jpg",
                    hlsUrl = "$baseUrl/api/go2rtc/api/stream.m3u8?src=$preferredStream",
                    mseUrl = "$baseUrl/live/mse/api/ws?src=$preferredStream",
                    useHls = defaultUseHls
                )
            }
            cameraDao.insertCameras(entities)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getEvents(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try { Result.success(apiService.getEvents()) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getReviewItems(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try { Result.success(apiService.getReviewItems()) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getCameras(): Result<List<Camera>> = withContext(Dispatchers.IO) {
        try {
            val prefs = preferenceDao.getPreferences().firstOrNull()
            val defaultUseHls = prefs?.defaultPlayerType == "hls"
            
            val baseUrl = getBaseUrl()
            val config = apiService.getConfig()
            val camerasMap = config["cameras"] as? Map<String, Any> ?: emptyMap()

            val cameras = camerasMap.mapNotNull { (cameraName, cameraConfigAny) ->
                val cameraConfig = cameraConfigAny as? Map<*, *> ?: return@mapNotNull null
                val live = cameraConfig["live"] as? Map<*, *>
                val streams = live?.get("streams") as? Map<*, *>
                
                // Frigate logic: Use first stream in 'live.streams', or fallback to camera name
                val preferredStream = streams?.values?.firstOrNull() as? String ?: cameraName

                val detect = cameraConfig["detect"] as? Map<*, *>
                val width = (detect?.get("width") as? Number)?.toInt() ?: 1920
                val height = (detect?.get("height") as? Number)?.toInt() ?: 1080

                Camera(
                    name = cameraName,
                    width = width,
                    height = height,
                    mjpegUrl = "$baseUrl/api/go2rtc/streams/$preferredStream",
                    snapshotUrl = "$baseUrl/api/$cameraName/latest.jpg",
                    hlsUrl = "$baseUrl/api/go2rtc/api/stream.m3u8?src=$preferredStream",
                    mseUrl = "$baseUrl/live/mse/api/ws?src=$preferredStream",
                    useHls = defaultUseHls
                )
            }
            Result.success(cameras)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getServerLogs(service: String): Result<String> = withContext(Dispatchers.IO) {
        try { Result.success(apiService.getLogs(service)) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun markReviewed(ids: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.markReviewed(ReviewViewedRequest(ids = ids, reviewed = true))
            if (response.isSuccessful) {
                reviewDao.updateReviewedStatus(ids, true)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark as reviewed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
