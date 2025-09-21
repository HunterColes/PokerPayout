package com.huntercoles.fatline.basicfeature.presentation.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.huntercoles.fatline.basicfeature.presentation.SearchNavigationFactory
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
internal object StockSearchViewModelModule {

    @Provides
    fun provideInitialStockSearchUiState(): StockSearchUiState = StockSearchUiState()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StockSearchSingletonModule {

    @Singleton
    @Binds
    @IntoSet
    fun bindSearchNavigationFactory(factory: SearchNavigationFactory): NavigationFactory
}
