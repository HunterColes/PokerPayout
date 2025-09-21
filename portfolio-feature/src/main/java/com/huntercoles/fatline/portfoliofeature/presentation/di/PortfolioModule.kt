package com.huntercoles.fatline.portfoliofeature.presentation.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioNavigationFactory
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioUiState
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
internal object PortfolioViewModelModule {

    @Provides
    fun provideInitialPortfolioUiState(): PortfolioUiState = PortfolioUiState()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface PortfolioSingletonModule {

    @Singleton
    @Binds
    @IntoSet
    fun bindPortfolioNavigationFactory(factory: PortfolioNavigationFactory): NavigationFactory
}
