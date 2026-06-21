package com.rayner.peregrine.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rayner.peregrine.data.local.AppDatabase
import com.rayner.peregrine.data.local.dao.ServerConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE server_config ADD COLUMN authCookie TEXT")
            database.execSQL("ALTER TABLE server_config ADD COLUMN authCookieExpiresAt INTEGER")
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE server_config ADD COLUMN defaultPlayerType TEXT NOT NULL DEFAULT 'hls'")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "peregrine_db"
        )
            .addMigrations(migration1To2, migration2To3)
            .build()
    }

    @Provides
    fun provideServerConfigDao(database: AppDatabase): ServerConfigDao {
        return database.serverConfigDao()
    }
}
