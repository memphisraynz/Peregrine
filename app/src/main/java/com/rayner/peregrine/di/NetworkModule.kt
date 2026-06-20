package com.rayner.peregrine.di

import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.remote.api.FrigateApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(serverConfigDao: ServerConfigDao): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val config = runBlocking { serverConfigDao.getServerConfig().firstOrNull() }
                val request = if (config != null) {
                    chain.request().newBuilder()
                        // Add auth headers if needed, for now just ensuring the URL is correct
                        // Frigate might use cookies for auth, which OkHttp handles with a CookieJar
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideFrigateApiService(okHttpClient: OkHttpClient, serverConfigDao: ServerConfigDao): FrigateApiService {
        // This is a bit tricky because the base URL depends on the config.
        // We'll use a placeholder and update it or use a dynamic base URL interceptor.
        // For simplicity, we'll fetch the URL from DB.
        val config = runBlocking { serverConfigDao.getServerConfig().firstOrNull() }
        val baseUrl = config?.serverUrl ?: "http://localhost:5000/"
        
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FrigateApiService::class.java)
    }
}
