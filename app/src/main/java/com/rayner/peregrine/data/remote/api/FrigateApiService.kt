package com.rayner.peregrine.data.remote.api

import com.rayner.peregrine.data.remote.model.LoginRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FrigateApiService {
    @POST("api/login/")
    suspend fun login(@Body body: LoginRequest): Response<Unit>

    @GET("api/version")
    suspend fun getVersion(): String

    @GET("api/stats")
    suspend fun getStats(): Map<String, Any>

    @GET("api/config")
    suspend fun getConfig(): Map<String, Any>

    @GET("api/events")
    suspend fun getEvents(
        @Query("camera") camera: String? = null,
        @Query("label") label: String? = null,
        @Query("zone") zone: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("include_thumbnails") includeThumbnails: Int? = 1
    ): List<Map<String, Any>>

    @GET("api/review")
    suspend fun getReviewItems(): List<Map<String, Any>>

    @GET("api/logs/{service}")
    suspend fun getLogs(@retrofit2.http.Path("service") service: String): String
}
