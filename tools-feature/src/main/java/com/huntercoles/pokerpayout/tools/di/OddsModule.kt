package com.huntercoles.pokerpayout.tools.di

import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OddsModule {

    @Provides
    @Singleton
    fun provideOddsEngine(): OddsEngine = OddsEngine()

    /**
     * The odds screen's calculation settings: exact whenever feasible, otherwise Monte Carlo
     * until every player's equity is within 0.1 points (one standard error) or 300k deals.
     */
    @Provides
    fun provideOddsSettings(): OddsSettings = OddsSettings(maxSamples = 300_000, targetStdErr = 0.1)
}
