package com.rayner.peregrine.data.remote.api

import com.rayner.peregrine.data.remote.model.LoginRequest
import com.rayner.peregrine.data.remote.model.ReviewViewedRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path

interface FrigateApiService {
    @POST("api/login/")
    suspend fun login(@Body body: LoginRequest): Response<Unit>

    @GET("api/config")
    suspend fun getConfig(): Map<String, Any>

    @GET("api/events")
    suspend fun getEvents(
        @Query("camera") camera: String? = null,
        @Query("label") label: String? = null,
        @Query("zone") zone: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("include_thumbnails") includeThumbnails: Int? = 1,
    ): List<Map<String, Any>>

    @GET("api/review")
    suspend fun getReviewItems(
        @Query("limit") limit: Int? = null,
        @Query("severity") severity: String? = null,
        @Query("reviewed") reviewed: Int? = null,
        @Query("camera") camera: String? = null,
    ): List<Map<String, Any>>

    @POST("api/reviews/viewed")
    suspend fun markReviewed(@Body body: ReviewViewedRequest): Response<Unit>

    @GET("api/logs/{service}")
    suspend fun getLogs(@Path("service") service: String): String
}
