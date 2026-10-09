package com.huntercoles.pokerpayout.tools.di

import com.huntercoles.pokerpayout.tools.presentation.QuizSeeds
import com.huntercoles.pokerpayout.tools.presentation.WheelSeeds
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockSignals
import com.huntercoles.pokerpayout.tools.shotclock.SystemShotClockSignals
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.random.Random

/** The shot clock, dealer's choice and the equity quiz. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FunToolsModule {
    /** The shot clock's beeps and buzzes: the phone's own (tests count them instead). */
    @Binds
    abstract fun bindShotClockSignals(signals: SystemShotClockSignals): ShotClockSignals

    companion object {
        /** Each spin of the wheel from a fresh random seed. */
        @Provides
        fun provideWheelSeeds(): WheelSeeds = WheelSeeds { Random.nextLong() }

        /** Each quiz deal from a fresh random seed. */
        @Provides
        fun provideQuizSeeds(): QuizSeeds = QuizSeeds { Random.nextLong() }
    }
}
