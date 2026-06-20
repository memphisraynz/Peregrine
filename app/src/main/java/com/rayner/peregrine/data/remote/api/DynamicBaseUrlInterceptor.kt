package com.rayner.peregrine.data.remote.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverUrlManager: ServerUrlManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val newUrlString = serverUrlManager.getUrl()

        if (newUrlString != null && newUrlString.isNotBlank()) {
            val newUrl = newUrlString.toHttpUrlOrNull()
            if (newUrl != null) {
                val currentUrl = request.url
                // Only intercept if the host is our placeholder or if we have a valid override
                if (currentUrl.host == "frigate.local") {
                    val newFullUrl = currentUrl.newBuilder()
                        .scheme(newUrl.scheme)
                        .host(newUrl.host)
                        .port(newUrl.port)
                        .build()
                    request = request.newBuilder()
                        .url(newFullUrl)
                        .build()
                }
            }
        }
        return chain.proceed(request)
    }
}
