package com.rayner.peregrine.data.remote.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that replaces the base URL of outgoing requests with the
 * user-configured server URL from [ServerUrlManager].
 */
@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverUrlManager: ServerUrlManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configUrlString = serverUrlManager.getUrl()

        if (configUrlString.isNullOrBlank()) {
            return chain.proceed(request)
        }

        val configUrl = configUrlString.toHttpUrlOrNull() ?: return chain.proceed(request)
        
        // Reconstruct the URL using the user-provided server details
        val newFullUrl = request.url.newBuilder()
            .scheme(configUrl.scheme)
            .host(configUrl.host)
            .port(configUrl.port)
            .build()
            
        return chain.proceed(
            request.newBuilder()
                .url(newFullUrl)
                .build()
        )
    }
}
