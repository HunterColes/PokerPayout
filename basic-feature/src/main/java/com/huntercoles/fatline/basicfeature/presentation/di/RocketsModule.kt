package com.huntercoles.fatline.basicfeature.presentation.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.huntercoles.fatline.basicfeature.presentation.CalculatorNavigationFactory
import com.huntercoles.fatline.basicfeature.presentation.CalculatorUiState
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
internal object CalculatorViewModelModule {

    @Provides
    fun provideInitialCalculatorUiState(): CalculatorUiState = CalculatorUiState()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface CalculatorSingletonModule {

    @Singleton
    @Binds
    @IntoSet
    fun bindCalculatorNavigationFactory(factory: CalculatorNavigationFactory): NavigationFactory
}
