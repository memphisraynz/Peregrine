package com.rayner.peregrine.data.remote.api

import android.util.Log
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ceiling on how far in the future a persisted auth cookie's expiry is trusted,
 * regardless of what the server's Set-Cookie header claims. Guards against an
 * anomalously long-lived cookie (observed: a Frigate session cookie claiming an
 * expiry ~56 years out, despite `session_length` being configured to 7 days)
 * leaving the app trusting a stale/invalid session indefinitely instead of
 * re-authenticating.
 */
internal const val MAX_TRUSTED_COOKIE_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000L

/**
 * A [CookieJar] backed directly by [ServerConfigDao]. There is exactly one
 * server config record and it is the single source of truth for the auth
 * cookie — OkHttp reads and writes it straight from/to the database instead of
 * keeping a separate in-memory copy that has to be manually kept in sync.
 */
@Singleton
class DatabaseBackedCookieJar @Inject constructor(
    private val serverConfigDao: ServerConfigDao
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val authCookie = cookies.find { it.name == "frigate_token" } ?: return

        runBlocking {
            try {
                val config = serverConfigDao.getServerConfig().firstOrNull() ?: return@runBlocking
                if (config.serverUrl.toHttpUrlOrNull()?.host != url.host) return@runBlocking

                val cappedExpiresAt = minOf(authCookie.expiresAt, System.currentTimeMillis() + MAX_TRUSTED_COOKIE_LIFETIME_MS)
                if (config.authCookie != authCookie.value || config.authCookieExpiresAt != cappedExpiresAt) {
                    Log.d("CookieJar", "Persisting auth cookie, expires at ${java.time.Instant.ofEpochMilli(cappedExpiresAt)}")
                    serverConfigDao.insertServerConfig(
                        config.copy(
                            authCookie = authCookie.value,
                            authCookieExpiresAt = cappedExpiresAt,
                            isLoggedIn = true
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("CookieJar", "Failed to persist auth cookie", e)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return runBlocking {
            val config = serverConfigDao.getServerConfig().firstOrNull() ?: return@runBlocking emptyList()
            if (config.serverUrl.toHttpUrlOrNull()?.host != url.host) return@runBlocking emptyList()

            val tokenValue = config.authCookie ?: return@runBlocking emptyList()
            val expiresAt = config.authCookieExpiresAt ?: return@runBlocking emptyList()
            val now = System.currentTimeMillis()
            // Reject not just cookies that have expired, but ones already stored with an
            // implausibly distant expiry (e.g. from before this cap existed, or a future
            // recurrence of the same server-side bug) - don't wait for a fresh write to fix it.
            if (expiresAt <= now || expiresAt - now > MAX_TRUSTED_COOKIE_LIFETIME_MS) {
                Log.w("CookieJar", "Rejecting untrusted persisted cookie (expiresAt=${java.time.Instant.ofEpochMilli(expiresAt)})")
                return@runBlocking emptyList()
            }

            val cookieBuilder = Cookie.Builder()
                .name("frigate_token")
                .value(tokenValue)
                .expiresAt(expiresAt)
                .httpOnly()
                .path("/")
                .hostOnlyDomain(url.host)

            if (url.isHttps) cookieBuilder.secure()

            listOf(cookieBuilder.build())
        }
    }
}
