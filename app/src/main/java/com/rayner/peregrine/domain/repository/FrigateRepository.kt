package com.rayner.peregrine.domain.repository

import com.rayner.peregrine.data.local.entity.CameraEntity
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import com.rayner.peregrine.data.local.entity.PreferenceEntity
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.domain.model.Camera
import kotlinx.coroutines.flow.Flow

interface FrigateRepository {
    fun getServerConfig(): Flow<ServerConfigEntity?>
    suspend fun updateServerConfig(config: ServerConfigEntity)
    suspend fun clearServerConfig()
    suspend fun restorePersistedAuthCookie()
    suspend fun login(user: String, password: String): Result<Unit>
    suspend fun getVersion(): Result<String>
    
    fun getReviewItemsFlow(): Flow<List<ReviewItemEntity>>
    suspend fun refreshReviewItems(
        limit: Int? = null,
        severity: String? = null,
        reviewed: Int? = null,
    ): Result<Unit>
    
    fun getExploreEventsFlow(): Flow<List<ExploreEventEntity>>
    suspend fun refreshExploreEvents(): Result<Unit>

    fun getPreferencesFlow(): Flow<PreferenceEntity?>
    suspend fun updatePreferences(prefs: PreferenceEntity)

    fun getCamerasFlow(): Flow<List<CameraEntity>>
    suspend fun refreshCameras(): Result<Unit>

    suspend fun getEvents(): Result<List<Map<String, Any>>>
    suspend fun getReviewItems(): Result<List<Map<String, Any>>>
    suspend fun getCameras(): Result<List<Camera>>
    suspend fun getServerLogs(service: String = "frigate"): Result<String>

    suspend fun markReviewed(ids: List<String>): Result<Unit>
}
