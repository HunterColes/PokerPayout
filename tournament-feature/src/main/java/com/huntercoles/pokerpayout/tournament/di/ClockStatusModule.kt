package com.huntercoles.pokerpayout.tournament.di

import com.huntercoles.pokerpayout.core.domain.model.ClockStatusProvider
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClockStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The clock's level and break, for other tabs (the Bank's cutoffs), through core's [ClockStatusProvider]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClockStatusModule {

    @Binds
    abstract fun bindClockStatusProvider(provider: SavedClockStatusProvider): ClockStatusProvider
}
