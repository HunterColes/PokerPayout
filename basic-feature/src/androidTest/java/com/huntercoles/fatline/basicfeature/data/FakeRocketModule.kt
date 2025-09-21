package com.huntercoles.fatline.basicfeature.data

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import com.huntercoles.fatline.basicfeature.data.di.RocketModule
import com.huntercoles.fatline.basicfeature.domain.usecase.GetRocketsUseCase
import com.huntercoles.fatline.basicfeature.domain.usecase.RefreshRocketsUseCase
import com.huntercoles.fatline.core.utils.resultOf
import kotlinx.coroutines.flow.flowOf

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RocketModule::class],
)
internal object FakeRocketModule {

    @Provides
    fun provideFakeGetRocketsUseCase(): GetRocketsUseCase = GetRocketsUseCase {
        flowOf(
            Result.success(generateTestRocketsFromDomain()),
        )
    }

    @Provides
    fun provideNoopRefreshRocketsUseCase(): RefreshRocketsUseCase = RefreshRocketsUseCase {
        resultOf { }
    }
}
