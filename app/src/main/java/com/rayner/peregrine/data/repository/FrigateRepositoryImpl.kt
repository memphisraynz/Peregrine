package com.rayner.peregrine.data.repository

import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.data.remote.api.FrigateApiService
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.domain.repository.FrigateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrigateRepositoryImpl @Inject constructor(
    private val apiService: FrigateApiService,
    private val serverConfigDao: ServerConfigDao
) : FrigateRepository {

    override fun getServerConfig(): Flow<ServerConfigEntity?> {
        return serverConfigDao.getServerConfig()
    }

    override suspend fun updateServerConfig(config: ServerConfigEntity) {
        serverConfigDao.insertServerConfig(config)
    }

    override suspend fun getVersion(): Result<String> {
        return try {
            Result.success(apiService.getVersion())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEvents(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getEvents())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCameras(): Result<List<Camera>> {
        return try {
            val config = apiService.getConfig()
            val camerasMap = config["cameras"] as? Map<String, Any> ?: emptyMap()
            val baseUrl = serverConfigDao.getServerConfig().firstOrNull()?.serverUrl ?: ""
            
            val cameras = camerasMap.keys.map { cameraName ->
                Camera(
                    name = cameraName,
                    mjpegUrl = "$baseUrl/api/$cameraName",
                    snapshotUrl = "$baseUrl/api/$cameraName/latest.jpg",
                    hlsUrl = "$baseUrl/live/jsmpeg/$cameraName/index.m3u8" // Example HLS path
                )
            }
            Result.success(cameras)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
