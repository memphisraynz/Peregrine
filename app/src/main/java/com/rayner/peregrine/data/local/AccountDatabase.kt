package com.rayner.peregrine.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import com.rayner.peregrine.data.local.entity.ServerConfigEntity

@Database(
    entities = [ServerConfigEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AccountDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
}
