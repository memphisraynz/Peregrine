package com.rayner.peregrine.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "peregrine_db"
        ).build()
    }

    @Provides
    fun provideServerConfigDao(database: AppDatabase): ServerConfigDao {
        return database.serverConfigDao()
    }
}
