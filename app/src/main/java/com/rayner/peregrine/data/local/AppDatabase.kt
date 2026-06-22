package com.rayner.peregrine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rayner.peregrine.data.local.dao.CameraDao
import com.rayner.peregrine.data.local.dao.ExploreDao
import com.rayner.peregrine.data.local.dao.ReviewDao
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.CameraEntity
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.local.entity.ServerConfigEntity

@Database(
    entities = [
        ServerConfigEntity::class,
        ReviewItemEntity::class,
        ExploreEventEntity::class,
        CameraEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun reviewDao(): ReviewDao
    abstract fun exploreDao(): ExploreDao
    abstract fun cameraDao(): CameraDao
}
