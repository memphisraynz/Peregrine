package com.rayner.peregrine.data.remote.api

import android.util.Log
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authenticator that handles 401 Unauthorized errors by attempting to re-login
 * using stored credentials.
 */
@Singleton
class FrigateAuthenticator @Inject constructor(
    private val serverConfigDao: ServerConfigDao,
    private val serverUrlManager: ServerUrlManager,
    private val cookieJar: CookieJar
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only try to authenticate if we haven't already tried for this request
        if (response.priorResponse != null) {
            return null
        }

        return runBlocking {
            val config = serverConfigDao.getServerConfig().firstOrNull() ?: return@runBlocking null
            val username = config.username ?: return@runBlocking null
            val password = config.encryptedPassword ?: return@runBlocking null
            val baseUrl = serverUrlManager.getUrl() ?: config.serverUrl

            Log.d("FrigateAuth", "Attempting auto-login for 401 response")

            val success = attemptLogin(baseUrl, username, password)
            if (success) {
                Log.d("FrigateAuth", "Auto-login successful, persisting cookie")
                persistAuthCookie(baseUrl)
                response.request.newBuilder().build()
            } else {
                Log.e("FrigateAuth", "Auto-login failed")
                null
            }
        }
    }

    private fun attemptLogin(baseUrl: String, user: String, pass: String): Boolean {
        try {
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(true)
                .build()

            val json = JSONObject().put("user", user).put("password", pass).toString()
            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl.removeSuffix("/")}/api/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FrigateAuth", "Login error: ${e.message}")
            return false
        }
    }

    private suspend fun persistAuthCookie(baseUrl: String) {
        try {
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
        } catch (e: Exception) {
            Log.e("FrigateAuth", "Failed to persist auth cookie", e)
        }
    }
}
