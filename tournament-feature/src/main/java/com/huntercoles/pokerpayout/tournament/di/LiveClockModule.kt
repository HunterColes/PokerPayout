package com.huntercoles.pokerpayout.tournament.di

import com.huntercoles.pokerpayout.core.presentation.AppVisibilityListener
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.live.LiveClockController
import com.huntercoles.pokerpayout.tournament.live.SystemCueVibrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** The live clock notification (PP-081) and the quiet cues (PP-083). */
@Module
@InstallIn(SingletonComponent::class)
abstract class LiveClockModule {

    /** MainActivity tells it when the app leaves the screen and comes back. */
    @Binds
    @IntoSet
    abstract fun bindLiveClockController(controller: LiveClockController): AppVisibilityListener

    @Binds
    abstract fun bindCueVibrator(vibrator: SystemCueVibrator): CueVibrator
}
