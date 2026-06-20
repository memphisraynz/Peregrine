package com.rayner.peregrine.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.gson.GsonBuilder
import com.rayner.peregrine.data.remote.api.DynamicBaseUrlInterceptor
import com.rayner.peregrine.data.remote.api.FrigateApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCookieJar(): CookieJar {
        return object : CookieJar {
            private val cookieStore = mutableMapOf<String, List<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val existingCookies = cookieStore[url.host].orEmpty()
                    .associateBy { it.name }
                    .toMutableMap()
                cookies.forEach { cookie ->
                    existingCookies[cookie.name] = cookie
                }
                cookieStore[url.host] = existingCookies.values.toList()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val now = System.currentTimeMillis()
                return cookieStore[url.host]
                    ?.filter { it.expiresAt > now }
                    .orEmpty()
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        baseUrlInterceptor: DynamicBaseUrlInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(loggingInterceptor)
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideFrigateApiService(okHttpClient: OkHttpClient): FrigateApiService {
        val gson = GsonBuilder().disableHtmlEscaping().create()
        return Retrofit.Builder()
            .baseUrl("https://frigate.local/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FrigateApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}
