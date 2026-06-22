package com.rayner.peregrine.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rayner.peregrine.data.local.entity.CameraEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY name ASC")
    fun getCameras(): Flow<List<CameraEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCameras(cameras: List<CameraEntity>)

    @Query("DELETE FROM cameras")
    suspend fun clearAll()
}
