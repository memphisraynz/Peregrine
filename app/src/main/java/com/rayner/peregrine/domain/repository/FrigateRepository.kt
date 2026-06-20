package com.rayner.peregrine.domain.repository

import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.domain.model.Camera
import kotlinx.coroutines.flow.Flow

interface FrigateRepository {
    fun getServerConfig(): Flow<ServerConfigEntity?>
    suspend fun updateServerConfig(config: ServerConfigEntity)
    suspend fun clearServerConfig()
    suspend fun login(user: String, password: String): Result<Unit>
    suspend fun getVersion(): Result<String>
    suspend fun getEvents(): Result<List<Map<String, Any>>>
    suspend fun getReviewItems(): Result<List<Map<String, Any>>>
    suspend fun getCameras(): Result<List<Camera>>
    suspend fun getServerLogs(service: String = "frigate"): Result<String>
}
