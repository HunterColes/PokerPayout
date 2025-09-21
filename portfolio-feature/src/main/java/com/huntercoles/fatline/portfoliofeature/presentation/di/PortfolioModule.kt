package com.huntercoles.fatline.portfoliofeature.presentation.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.huntercoles.fatline.portfoliofeature.presentation.BankNavigationFactory
import com.huntercoles.fatline.portfoliofeature.presentation.BankUiState
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Singleton

@Module
@InstallIn(ViewModelComponent::class)
internal object BankViewModelModule {

    @Provides
    fun provideInitialBankUiState(): BankUiState = BankUiState()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface BankSingletonModule {

    @Singleton
    @Binds
    @IntoSet
    fun bindBankNavigationFactory(factory: BankNavigationFactory): NavigationFactory
}
