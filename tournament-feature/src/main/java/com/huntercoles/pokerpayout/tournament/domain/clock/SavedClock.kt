package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import javax.inject.Inject

/**
 * The clock as it is saved, for readers away from the clock's screen (the Bank's cutoffs, the live
 * clock notification): its anchor, whether it has finished, and the [ClockTimeline] it plays, built
 * from the setup frozen at the first start exactly as the clock builds it. Read-only.
 */
class SavedClock @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences,
) {
    /** A started clock: where it stands ([anchor]) and what it plays ([timeline]). */
    data class State(val anchor: ClockAnchor, val timeline: ClockTimeline, val finished: Boolean)

    private var cached: Pair<TimelineInput, ClockTimeline>? = null

    /** The started clock, or null before the first start and after a reset. */
    fun read(): State? {
        val anchor = timerPreferences.getClock()?.takeIf { timerPreferences.getHasTimerStarted() }
        return anchor?.let { State(it, timeline(), timerPreferences.getIsFinished()) }
    }

    /** What shapes the schedule once the clock has started (the setup it froze then). */
    private data class TimelineInput(
        val players: Int,
        val durationMinutes: Int,
        val roundLengthMinutes: Int,
        val smallestChip: Int,
        val startingChips: Int,
        val breaks: BreakSettings,
        val anteFromLevel: Int
    )

    /** The started clock's timeline; empty if its saved setup can't be played. */
    fun timeline(): ClockTimeline {
        val input = TimelineInput(
            players = tournamentPreferences.getPlayerCount().coerceAtLeast(1),
            durationMinutes = timerPreferences.getGameDurationMinutes(),
            roundLengthMinutes = timerPreferences.getRoundLengthAtStart(),
            smallestChip = timerPreferences.getSmallestChipAtStart(),
            startingChips = timerPreferences.getStartingChipsAtStart(),
            breaks = BreakSettings(
                everyLevels = timerPreferences.getBreakEveryLevels(),
                lengthMinutes = timerPreferences.getBreakLengthMinutes(),
                message = timerPreferences.getBreakMessage()
            ),
            anteFromLevel = timerPreferences.getBigBlindAnteFromLevel()
        )
        cached?.let { (key, timeline) -> if (key == input) return timeline }
        return build(input).also { cached = input to it }
    }

    private fun build(input: TimelineInput): ClockTimeline {
        val problem = BlindSetupAdvisor.check(
            durationMinutes = input.durationMinutes,
            roundLengthMinutes = input.roundLengthMinutes,
            smallestChip = input.smallestChip,
            startingChips = input.startingChips
        )
        val levels = if (problem == null) {
            runCatching {
                BlindStructureCalculator.generateSchedule(
                    BlindStructureInput(
                        players = input.players,
                        targetDurationMinutes = input.durationMinutes,
                        smallestChip = input.smallestChip,
                        startingStack = input.startingChips,
                        roundLengthMinutes = input.roundLengthMinutes,
                        bigBlindAnteFromLevel = input.anteFromLevel
                    )
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return ClockTimeline.build(
            regularLevels = levels,
            roundLengthMinutes = input.roundLengthMinutes,
            breaks = input.breaks,
            smallestChip = input.smallestChip,
            bigBlindAnteFromLevel = input.anteFromLevel
        )
    }
}
