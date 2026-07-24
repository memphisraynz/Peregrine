package com.rayner.peregrine.di

import android.content.Context
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.memory.MemoryCache
import coil3.network.ConnectivityChecker
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.gson.GsonBuilder
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.remote.api.DatabaseBackedCookieJar
import com.rayner.peregrine.data.remote.api.DynamicBaseUrlInterceptor
import com.rayner.peregrine.data.remote.api.FrigateApiService
import com.rayner.peregrine.data.remote.api.FrigateAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideCookieJar(serverConfigDao: ServerConfigDao): CookieJar {
        return DatabaseBackedCookieJar(serverConfigDao)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        baseUrlInterceptor: DynamicBaseUrlInterceptor,
        frigateAuthenticator: FrigateAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .authenticator(frigateAuthenticator)
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
        // The base URL here is a required placeholder; the DynamicBaseUrlInterceptor
        // will replace it with the user-configured server URL for every request.
        return Retrofit.Builder()
            .baseUrl("https://placeholder.api/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(FrigateApiService::class.java)
    }

    @OptIn(ExperimentalCoilApi::class)
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { okHttpClient },
                        // Coil's default connectivity pre-check asks ConnectivityManager
                        // whether this process currently has internet access before it will
                        // even attempt a request. That check is background-restriction-aware:
                        // it can report no connectivity for a background FCM service during
                        // Doze/sleep even though the network is genuinely up, causing Coil to
                        // skip the network entirely and fail locally with a synthetic
                        // "504 Unsatisfiable Request" - no request is ever sent. Notifications
                        // must fetch images from a background service, so that pre-check does
                        // more harm than good here; always attempt the real request instead.
                        connectivityChecker = { ConnectivityChecker.ONLINE }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .build()
    }
}
