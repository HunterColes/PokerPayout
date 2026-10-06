package com.huntercoles.pokerpayout.tournament.di

import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.utils.BlindScheduleProvider
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockBlindScheduleProvider
import com.huntercoles.pokerpayout.tournament.presentation.PayoutsNavigationFactory
import com.huntercoles.pokerpayout.tournament.presentation.TournamentNavigationFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

// CalculatePayoutsUseCase lives in core (shared with the Bank) and has an @Inject constructor.
@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentFeatureModule {

    @Binds
    @IntoSet
    abstract fun bindTournamentNavigationFactory(factory: TournamentNavigationFactory): NavigationFactory

    @Binds
    @IntoSet
    abstract fun bindPayoutsNavigationFactory(factory: PayoutsNavigationFactory): NavigationFactory

    /** The clock's schedule for the chip set's color-up plan (tools-feature reads it through core). */
    @Binds
    abstract fun bindBlindScheduleProvider(provider: ClockBlindScheduleProvider): BlindScheduleProvider
}
