package com.rayner.peregrine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rayner.peregrine.data.local.dao.CameraDao
import com.rayner.peregrine.data.local.dao.ExploreDao
import com.rayner.peregrine.data.local.dao.PreferenceDao
import com.rayner.peregrine.data.local.dao.ReviewDao
import com.rayner.peregrine.data.local.entity.CameraEntity
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import com.rayner.peregrine.data.local.entity.PreferenceEntity
import com.rayner.peregrine.data.local.entity.ReviewItemConverters
import com.rayner.peregrine.data.local.entity.ReviewItemEntity

@Database(
    entities = [
        ReviewItemEntity::class,
        ExploreEventEntity::class,
        CameraEntity::class,
        PreferenceEntity::class,
    ],
    version = 4, // Increment for PreferenceEntity alertsFilterDays
    exportSchema = false,
)
@TypeConverters(ReviewItemConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao
    abstract fun exploreDao(): ExploreDao
    abstract fun cameraDao(): CameraDao
    abstract fun preferenceDao(): PreferenceDao
}
