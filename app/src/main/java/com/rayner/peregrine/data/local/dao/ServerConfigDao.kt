package com.rayner.peregrine.data.local.dao

import androidx.room.*
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config WHERE id = 0")
    fun getServerConfig(): Flow<ServerConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerConfig(config: ServerConfigEntity)

    @Query("DELETE FROM server_config")
    suspend fun clearConfig()
}
