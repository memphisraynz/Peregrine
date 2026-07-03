package com.rayner.peregrine.data.remote.api

import android.util.Log
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.*
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
    private val serverUrlManager: ServerUrlManager
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
                Log.d("FrigateAuth", "Auto-login successful")
                // The CookieJar will have the new cookie from the successful login response
                // through the CookiePersistenceInterceptor (if we use the same client)
                // Actually, Authenticator runs AFTER the response that triggered it.
                // We need to make sure the original request is retried with the new cookies.
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
}
