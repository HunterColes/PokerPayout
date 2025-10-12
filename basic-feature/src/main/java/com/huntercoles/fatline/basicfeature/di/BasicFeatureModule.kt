package com.huntercoles.fatline.basicfeature.di

import com.huntercoles.fatline.basicfeature.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.fatline.basicfeature.presentation.PlayNavigationFactory
import com.huntercoles.fatline.basicfeature.presentation.OddsCalculatorNavigationFactory
import com.huntercoles.fatline.core.navigation.NavigationFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BasicFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindPlayNavigationFactory(factory: PlayNavigationFactory): NavigationFactory

    @Binds
    @IntoSet
    abstract fun bindOddsCalculatorNavigationFactory(factory: OddsCalculatorNavigationFactory): NavigationFactory

    companion object {
        @Provides
        @Singleton
        fun provideCalculatePayoutsUseCase(): CalculatePayoutsUseCase {
            return CalculatePayoutsUseCase()
        }
    }
}