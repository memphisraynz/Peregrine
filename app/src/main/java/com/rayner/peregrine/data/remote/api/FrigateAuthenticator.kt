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
    private val serverUrlManager: ServerUrlManager,
    private val cookieJar: CookieJar
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only try to authenticate if we haven't already tried for this request
        if (response.priorResponse != null) {
            Log.w("FrigateAuth", "Already tried auto-login for this request, giving up")
            return null
        }

        return runBlocking {
            val config = serverConfigDao.getServerConfig().firstOrNull() ?: run {
                Log.w("FrigateAuth", "Auto-login failed: No server config in DB")
                return@runBlocking null
            }

            val username = config.username
            val password = config.encryptedPassword

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                Log.w("FrigateAuth", "Auto-login failed: Credentials not found in DB")
                return@runBlocking null
            }

            val baseUrl = serverUrlManager.getUrl() ?: config.serverUrl

            Log.d("FrigateAuth", "Attempting auto-login for 401 response at $baseUrl")

            val success = attemptLogin(baseUrl, username, password)
            if (success) {
                Log.d("FrigateAuth", "Auto-login successful")
                response.request.newBuilder().build()
            } else {
                Log.e("FrigateAuth", "Auto-login failed for user: $username")
                null
            }
        }
    }

    private fun attemptLogin(baseUrl: String, user: String, pass: String): Boolean {
        try {
            // Uses the same shared cookieJar as the rest of the app, so a successful
            // login's Set-Cookie response is persisted automatically - no separate
            // manual "save the cookie" step needed here.
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(true)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val json = JSONObject().put("user", user).put("password", pass).toString()
            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl.removeSuffix("/")}/api/login")
                .post(body)
                .header("User-Agent", "FrigateViewer/1.0 Authenticator")
                .build()

            client.newCall(request).execute().use { response ->
                Log.d("FrigateAuth", "Login response code: ${response.code}")
                return response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("FrigateAuth", "Login exception", e)
            return false
        }
    }
}
