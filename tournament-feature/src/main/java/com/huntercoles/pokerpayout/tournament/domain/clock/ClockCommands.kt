package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import javax.inject.Inject

/**
 * What pausing and resuming the clock save, in one place for both ways to do it: the clock's own
 * button ([com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel]) and the live clock
 * notification's ([ClockCommands]). The saved anchor is the one clock; whoever changes it, the
 * clock's screen follows it, and a process death resumes from it.
 */
class ClockSaves(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences,
) {
    /** The clock stopped at [anchor]: money and blinds may be edited again. */
    fun paused(anchor: ClockAnchor) {
        timerPreferences.saveClock(anchor)
        tournamentPreferences.setTournamentLocked(false)
    }

    /** The clock running from [anchor]: money and blinds locked, the setup folded away. */
    fun resumed(anchor: ClockAnchor) {
        timerPreferences.saveClock(anchor)
        tournamentPreferences.setTournamentLocked(true)
        tournamentPreferences.setIsConfigExpanded(false)
    }
}

/** A button on the live clock notification (PP-081). */
enum class ClockCommand { PAUSE, RESUME }

/**
 * The live clock notification's Pause and Resume (PP-081), on the saved clock: exactly what the
 * clock's own button saves ([ClockSaves]), whether or not the clock's screen is alive. A screen that
 * is alive follows the saved clock at once; one opened later restores from it. A command that no
 * longer fits (Pause on a paused clock, from a stale notification) does nothing.
 */
class ClockCommands @Inject constructor(
    timerPreferences: TimerPreferences,
    tournamentPreferences: TournamentPreferences,
    private val timeSource: TimeSource,
    private val savedClock: SavedClock,
) {
    private val saves = ClockSaves(timerPreferences, tournamentPreferences)

    /** Carries out [command]; true if it changed the clock. */
    fun perform(command: ClockCommand): Boolean {
        val clock = savedClock.read()?.takeUnless { it.finished || it.timeline.isEmpty } ?: return false
        val elapsed = clock.anchor.elapsedAt(timeSource)
        // Past the last level the clock is over, even if no screen has marked it finished yet
        val playable = elapsed < clock.timeline.endSeconds * MILLIS_PER_SECOND
        return when {
            !playable -> false
            command == ClockCommand.PAUSE && clock.anchor.running -> {
                saves.paused(ClockAnchor.stopped(elapsed))
                true
            }
            command == ClockCommand.RESUME && !clock.anchor.running -> {
                saves.resumed(ClockAnchor.runningFrom(elapsed, timeSource))
                true
            }
            else -> false
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
