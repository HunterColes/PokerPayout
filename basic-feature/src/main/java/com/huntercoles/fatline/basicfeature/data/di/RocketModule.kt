package com.huntercoles.fatline.basicfeature.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.huntercoles.fatline.basicfeature.data.remote.api.RocketApi
import com.huntercoles.fatline.basicfeature.data.repository.RocketRepositoryImpl
import com.huntercoles.fatline.basicfeature.domain.repository.RocketRepository
import com.huntercoles.fatline.basicfeature.domain.usecase.GetRocketsUseCase
import com.huntercoles.fatline.basicfeature.domain.usecase.RefreshRocketsUseCase
import com.huntercoles.fatline.basicfeature.domain.usecase.getRockets
import com.huntercoles.fatline.basicfeature.domain.usecase.refreshRockets
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RocketModule {

    @Provides
    @Singleton
    fun provideRocketApi(retrofit: Retrofit): RocketApi = retrofit.create(RocketApi::class.java)

    @Provides
    fun provideGetRocketsUseCase(rocketRepository: RocketRepository): GetRocketsUseCase = GetRocketsUseCase {
        getRockets(rocketRepository)
    }

    @Provides
    fun provideRefreshRocketsUseCase(rocketRepository: RocketRepository): RefreshRocketsUseCase = RefreshRocketsUseCase {
        refreshRockets(rocketRepository)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface BindsModule {

        @Binds
        @Singleton
        fun bindRocketRepository(impl: RocketRepositoryImpl): RocketRepository
    }
}
