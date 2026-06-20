package com.rayner.peregrine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.ServerConfigEntity

@Database(entities = [ServerConfigEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
}
