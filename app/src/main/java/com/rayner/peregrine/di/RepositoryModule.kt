package com.rayner.peregrine.di

import com.rayner.peregrine.data.repository.FrigateRepositoryImpl
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFrigateRepository(
        frigateRepositoryImpl: FrigateRepositoryImpl
    ): FrigateRepository
}
