package com.rayner.peregrine.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExploreDao {
    @Query("SELECT * FROM explore_events ORDER BY startTime DESC")
    fun getExploreEvents(): Flow<List<ExploreEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExploreEvents(events: List<ExploreEventEntity>)

    @Query("DELETE FROM explore_events")
    suspend fun clearAll()
}
