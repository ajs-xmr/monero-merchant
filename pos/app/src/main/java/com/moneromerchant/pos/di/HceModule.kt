package com.moneromerchant.pos.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.moneromerchant.pos.data.repository.HceRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HceModule {

    @Provides
    @Singleton
    fun provideHceRepository(): HceRepository {
        return HceRepository()
    }

}