package com.huntercoles.pokerpayout.tournament.live

import android.content.Context
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.presentation.AppVisibilityListener
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When the live clock notification shows (PP-081): from the moment the app leaves the screen (Home,
 * another app, the screen locking) with the clock running, until the app comes back. [LiveClockService]
 * takes it from there: it also goes when the game finishes, on a reset, or after a long pause.
 */
@Singleton
class LiveClockController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timerPreferences: TimerPreferences,
) : AppVisibilityListener {

    /** True while the app is on screen. The service won't show over it. */
    @Volatile
    var appVisible: Boolean = false
        private set

    /** Set by the service itself, so the app knows there is one to stop. */
    @Volatile
    var serviceRunning: Boolean = false

    /** Asked to start and not yet told to stop: the service may not have started yet. */
    private var showRequested = false

    override fun onAppHidden() {
        appVisible = false
        if (!shows(timerPreferences)) return
        showRequested = true
        // Allowed for a few seconds after the app leaves the screen; if Android says no, the app
        // works as before, without the notification.
        runCatching { context.startForegroundService(LiveClockService.intent(context, LiveClockService.ACTION_SHOW)) }
            .onFailure {
                showRequested = false
                Timber.w(it, "Live clock: not started")
            }
    }

    override fun onAppVisible() {
        appVisible = true
        if (!showRequested && !serviceRunning) return
        showRequested = false
        runCatching { context.startService(LiveClockService.intent(context, LiveClockService.ACTION_HIDE)) }
            .onFailure { Timber.w(it, "Live clock: not stopped") }
    }

    companion object {
        /** The notification is for a running clock: started, not finished, not paused. */
        fun shows(timerPreferences: TimerPreferences): Boolean =
            timerPreferences.getHasTimerStarted() && !timerPreferences.getIsFinished() && timerPreferences.getTimerRunning()
    }
}
