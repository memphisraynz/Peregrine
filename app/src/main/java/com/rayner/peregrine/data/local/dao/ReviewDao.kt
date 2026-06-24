package com.rayner.peregrine.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_items ORDER BY startTime DESC")
    fun getReviewItems(): Flow<List<ReviewItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewItems(items: List<ReviewItemEntity>)

    @Query("DELETE FROM review_items")
    suspend fun clearAll()

    @Query("UPDATE review_items SET hasBeenReviewed = :reviewed WHERE id IN (:ids)")
    suspend fun updateReviewedStatus(ids: List<String>, reviewed: Boolean)
}
