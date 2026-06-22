package com.rayner.peregrine.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rayner.peregrine.data.local.AppDatabase
import com.rayner.peregrine.data.local.dao.CameraDao
import com.rayner.peregrine.data.local.dao.ExploreDao
import com.rayner.peregrine.data.local.dao.ReviewDao
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

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS review_items (id TEXT NOT NULL, camera TEXT NOT NULL, severity TEXT NOT NULL, startTime REAL NOT NULL, endTime REAL, thumbPath TEXT NOT NULL, hasBeenReviewed INTEGER NOT NULL, primaryLabel TEXT, PRIMARY KEY(id))")
            database.execSQL("CREATE TABLE IF NOT EXISTS explore_events (id TEXT NOT NULL, camera TEXT NOT NULL, label TEXT NOT NULL, startTime REAL NOT NULL, thumbUrl TEXT NOT NULL, PRIMARY KEY(id))")
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS cameras (name TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, mjpegUrl TEXT NOT NULL, snapshotUrl TEXT NOT NULL, hlsUrl TEXT, mseUrl TEXT, useHls INTEGER NOT NULL, PRIMARY KEY(name))")
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
            .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideServerConfigDao(database: AppDatabase): ServerConfigDao {
        return database.serverConfigDao()
    }

    @Provides
    fun provideReviewDao(database: AppDatabase): ReviewDao {
        return database.reviewDao()
    }

    @Provides
    fun provideExploreDao(database: AppDatabase): ExploreDao {
        return database.exploreDao()
    }

    @Provides
    fun provideCameraDao(database: AppDatabase): CameraDao {
        return database.cameraDao()
    }
}
