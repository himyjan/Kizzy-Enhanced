package com.my.kizzy.data.di

import com.my.kizzy.data.repository.KizzyRepositoryImpl
import com.my.kizzy.domain.repository.KizzyRepository
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
    abstract fun bindKizzyRepository(
        impl: KizzyRepositoryImpl
    ): KizzyRepository
}
