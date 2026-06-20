package com.rayner.peregrine.data.repository

import android.util.Log
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.data.remote.api.FrigateApiService
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
    private val okHttpClient: OkHttpClient,
    private val cookieJar: CookieJar,
    private val serverUrlManager: com.rayner.peregrine.data.remote.api.ServerUrlManager
) : FrigateRepository {

    override fun getServerConfig(): Flow<ServerConfigEntity?> {
        return serverConfigDao.getServerConfig()
    }

    override suspend fun updateServerConfig(config: ServerConfigEntity) {
        serverConfigDao.insertServerConfig(config)
    }

    override suspend fun clearServerConfig() {
        serverConfigDao.clearConfig()
    }

    suspend fun restorePersistedAuthCookie() {
        val config = serverConfigDao.getServerConfig().firstOrNull() ?: return
        val baseUrl = config.serverUrl.toHttpUrlOrNull() ?: return
        val tokenCookie = config.authCookie ?: return
        val expiresAt = config.authCookieExpiresAt ?: return
        if (expiresAt <= System.currentTimeMillis()) return

        val cookie = Cookie.Builder()
            .name("frigate_token")
            .value(tokenCookie)
            .expiresAt(expiresAt)
            .httpOnly()
            .path("/")
            .hostOnlyDomain(baseUrl.host)
            .build()

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
            Log.e("FrigateRepo", "Login error", e)
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

    override suspend fun getEvents(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try { Result.success(apiService.getEvents()) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getReviewItems(): Result<List<Map<String, Any>>> = withContext(Dispatchers.IO) {
        try { Result.success(apiService.getReviewItems()) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getCameras(): Result<List<Camera>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val config = apiService.getConfig()
            val camerasMap = config["cameras"] as? Map<String, Any> ?: emptyMap()

            val cameras = camerasMap.mapNotNull { (cameraName, cameraConfigAny) ->
                val cameraConfig = cameraConfigAny as? Map<*, *> ?: return@mapNotNull null
                val live = cameraConfig["live"] as? Map<*, *>
                val streams = live?.get("streams") as? Map<*, *>
                val preferredStream = when {
                    streams?.containsKey("SD") == true -> streams["SD"] as? String
                    streams?.containsKey("HD") == true -> streams["HD"] as? String
                    else -> null
                }

                Camera(
                    name = cameraName,
                    mjpegUrl = "$baseUrl/api/go2rtc/streams/${preferredStream ?: cameraName}",
                    snapshotUrl = "$baseUrl/api/$cameraName/latest.jpg",
                    hlsUrl = preferredStream?.let { "$baseUrl/live/webrtc/api/stream.m3u8?src=$it" },
                    mseUrl = preferredStream?.let { "$baseUrl/live/mse/api/ws?src=$it" }
                )
            }
            Result.success(cameras)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getServerLogs(service: String): Result<String> = withContext(Dispatchers.IO) {
        try { Result.success(apiService.getLogs(service)) } catch (e: Exception) { Result.failure(e) }
    }
}
