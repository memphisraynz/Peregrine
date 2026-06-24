package com.rayner.peregrine.di

import android.content.Context
import androidx.room.Room
import com.rayner.peregrine.data.local.AccountDatabase
import com.rayner.peregrine.data.local.AppDatabase
import com.rayner.peregrine.data.local.dao.CameraDao
import com.rayner.peregrine.data.local.dao.ExploreDao
import com.rayner.peregrine.data.local.dao.PreferenceDao
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
class DatabaseModule {

    @Provides
    @Singleton
    fun provideAccountDatabase(@ApplicationContext context: Context): AccountDatabase {
        return Room.databaseBuilder(
            context,
            AccountDatabase::class.java,
            "account_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cache_db" // Renamed to cache_db to distinguish from the old combined db
        )
            .fallbackToDestructiveMigration() // This can now stay forever!
            .build()
    }

    @Provides
    fun provideServerConfigDao(database: AccountDatabase): ServerConfigDao {
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

    @Provides
    fun providePreferenceDao(database: AppDatabase): PreferenceDao {
        return database.preferenceDao()
    }
}
