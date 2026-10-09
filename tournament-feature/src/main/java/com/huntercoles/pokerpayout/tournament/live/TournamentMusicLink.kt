package com.huntercoles.pokerpayout.tournament.live

import android.content.Context
import android.os.PowerManager
import com.huntercoles.pokerpayout.core.audio.music.MusicAutoPlay
import com.huntercoles.pokerpayout.core.audio.music.MusicControls
import com.huntercoles.pokerpayout.core.coroutines.MainImmediateScope
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.presentation.AppVisibilityListener
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockPhases
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The music and the tournament clock ("Play with the clock", Tools > Sound > Music).
 *
 * It looks at the saved clock (the one clock: the screen's and the live notification's) whenever it
 * is saved (start, pause, a jump, the end, a reset) and at each change of level or break, and hands
 * what it sees to [MusicAutoPlay]: the music starts when the clock runs, pauses when it is paused or
 * over, and on a break does what the host chose. It never writes the clock.
 *
 * In the background the music plays on only while something keeps the app awake in the foreground:
 * the live clock's service (the clock running), or the app still on screen with the screen off. Left
 * for another app with the clock stopped, the music pauses, and plays again when the app comes back.
 * Called on the main thread.
 */
@Singleton
@Suppress("LongParameterList") // one injected source per thing the music follows
class TournamentMusicLink @Inject constructor(
    private val savedClock: SavedClock,
    private val timerPreferences: TimerPreferences,
    private val musicPreferences: MusicPreferences,
    private val music: MusicControls,
    private val timeSource: TimeSource,
    @ApplicationContext private val context: Context,
    @MainImmediateScope private val scope: CoroutineScope,
) : AppVisibilityListener {

    private val autoPlay = MusicAutoPlay()

    /** Reads the saved clock; a test puts in its own. */
    internal var readClock: () -> SavedClock.State? = savedClock::read
    private var following: Job? = null
    private var nextLook: Job? = null

    private var appVisible = false

    /** The app went out of sight because the screen went off (it is still the one on screen). */
    private var hiddenByScreenOff = false

    /** The music was paused because the app left the screen; it plays again when the app is back. */
    private var pausedForBackground = false

    /** Starts following the clock. Again does nothing. */
    fun start() {
        if (following != null) return
        following = merge(
            timerPreferences.observeChanges(),
            musicPreferences.autoPlay.drop(1).map { },
            musicPreferences.breakMusic.drop(1).map { },
        )
            .onStart { emit(Unit) }
            .onEach { look() }
            .launchIn(scope)
    }

    /** Looks at the clock now, sets the music going (or not), and comes back at the next change. */
    internal fun look() {
        val clock = readClock()
        val elapsed = clock?.anchor?.elapsedAt(timeSource) ?: 0L
        val autoPlayOn = musicPreferences.getAutoPlay()
        val step = autoPlay.update(ClockPhases.of(clock, elapsed), autoPlayOn, musicPreferences.getBreakMusic())
        music.setQuiet(step.quiet)
        when (step.play) {
            true -> music.play()
            false -> music.pause()
            null -> Unit
        }
        nextLook?.cancel()
        nextLook = ClockPhases.nextChange(clock, elapsed)?.takeIf { autoPlayOn }?.let { at ->
            scope.launch {
                delay(at - elapsed + LATE_MILLIS)
                look()
            }
        }
    }

    override fun onAppVisible() {
        appVisible = true
        start()
        if (pausedForBackground) {
            pausedForBackground = false
            music.play()
        }
    }

    override fun onAppHidden() {
        appVisible = false
        hiddenByScreenOff = context.getSystemService(PowerManager::class.java)?.isInteractive == false
        val keepsAwake = hiddenByScreenOff || LiveClockController.shows(timerPreferences)
        if (!keepsAwake) pauseForBackground()
    }

    /** The live clock's service has stopped: out of sight, nothing keeps the app playing now. */
    fun liveClockGone() {
        if (!appVisible && !hiddenByScreenOff) pauseForBackground()
    }

    private fun pauseForBackground() {
        if (music.isPlaying) {
            music.pause()
            pausedForBackground = true
        }
    }

    private companion object {
        /** Look this long after a change is due, so the look is past it. */
        const val LATE_MILLIS = 50L
    }
}
