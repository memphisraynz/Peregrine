package com.rayner.peregrine.domain.repository

import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import kotlinx.coroutines.flow.Flow

interface FrigateRepository {
    fun getServerConfig(): Flow<ServerConfigEntity?>
    suspend fun updateServerConfig(config: ServerConfigEntity)
    suspend fun getVersion(): Result<String>
    suspend fun getEvents(): Result<List<Map<String, Any>>>
    suspend fun getCameras(): Result<List<com.rayner.peregrine.domain.model.Camera>>
}
