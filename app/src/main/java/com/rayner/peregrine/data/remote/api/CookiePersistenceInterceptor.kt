package com.rayner.peregrine.data.remote.api

import com.rayner.peregrine.data.local.dao.ServerConfigDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that monitors responses for cookie changes and persists the
 * frigate_token cookie to the database.
 */
@Singleton
class CookiePersistenceInterceptor @Inject constructor(
    private val serverConfigDao: ServerConfigDao,
    private val cookieJar: CookieJar,
    private val serverUrlManager: ServerUrlManager
) : Interceptor {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        // Check for cookie updates in the response
        val url = serverUrlManager.getUrl()?.toHttpUrlOrNull()
        if (url != null) {
            val cookies = cookieJar.loadForRequest(url)
            val authCookie = cookies.find { it.name == "frigate_token" }
            
            if (authCookie != null) {
                scope.launch {
                    val config = serverConfigDao.getServerConfig().firstOrNull()
                    if (config != null && (config.authCookie != authCookie.value || config.authCookieExpiresAt != authCookie.expiresAt)) {
                        serverConfigDao.insertServerConfig(
                            config.copy(
                                authCookie = authCookie.value,
                                authCookieExpiresAt = authCookie.expiresAt,
                                isLoggedIn = true
                            )
                        )
                    }
                }
            }
        }
        
        return response
    }
}
