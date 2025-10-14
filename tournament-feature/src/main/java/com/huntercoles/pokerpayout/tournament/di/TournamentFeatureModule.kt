package com.huntercoles.pokerpayout.tournament.di

import com.huntercoles.pokerpayout.tournament.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.tournament.presentation.TournamentNavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindTournamentNavigationFactory(factory: TournamentNavigationFactory): NavigationFactory

    companion object {
        @Provides
        @Singleton
        fun provideCalculatePayoutsUseCase(): CalculatePayoutsUseCase {
            return CalculatePayoutsUseCase()
        }
    }
}