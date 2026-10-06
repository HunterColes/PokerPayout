package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSchedule
import com.huntercoles.pokerpayout.core.utils.BlindScheduleProvider
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.core.utils.ScheduledBreak
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import javax.inject.Inject

/**
 * The clock's schedule for other modules (the chip set's color-up plan, PP-033), read from the same
 * saved setup the clock uses: the values frozen when the clock first started, else the current
 * Tournament setup. The levels and breaks come from [ClockTimeline], exactly as the clock lays them
 * out. Read-only: it never writes a setting.
 */
class ClockBlindScheduleProvider @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences
) : BlindScheduleProvider {

    /** The parts of the setup the schedule is built from. */
    private class Setup(
        val durationMinutes: Int,
        val roundLengthMinutes: Int,
        val smallestChip: Int,
        val startingChips: Int,
        val anteFromLevel: Int
    )

    override fun currentSchedule(): BlindSchedule? {
        val setup = currentSetup()
        val levels = regularLevels(setup) ?: return null
        val timeline = ClockTimeline.build(
            regularLevels = levels,
            roundLengthMinutes = setup.roundLengthMinutes,
            breaks = BreakSettings(
                everyLevels = timerPreferences.getBreakEveryLevels(),
                lengthMinutes = timerPreferences.getBreakLengthMinutes(),
                message = timerPreferences.getBreakMessage()
            ),
            smallestChip = setup.smallestChip,
            bigBlindAnteFromLevel = setup.anteFromLevel
        )
        return BlindSchedule(
            levels = timeline.levels.map { it.level },
            regularLevelCount = levels.size,
            breaks = timeline.segments.filterIsInstance<BreakSegment>().map { ScheduledBreak(it.number, it.afterLevel) },
            smallestChip = setup.smallestChip,
            startingChips = setup.startingChips
        )
    }

    /** As the clock loads it: what was frozen at the first start, else the Tournament setup. */
    private fun currentSetup(): Setup {
        val timer = timerPreferences
        val tournament = tournamentPreferences
        val started = timer.getHasTimerStarted()
        return Setup(
            durationMinutes = timer.getGameDurationMinutes(),
            roundLengthMinutes = if (started) timer.getRoundLengthAtStart() else tournament.getRoundLengthMinutes(),
            smallestChip = SmallestChipChoices.normalize(
                if (started) timer.getSmallestChipAtStart() else tournament.getSmallestChip()
            ),
            startingChips = if (started) timer.getStartingChipsAtStart() else tournament.getStartingChips(),
            anteFromLevel = timer.getBigBlindAnteFromLevel()
        )
    }

    /** The regular levels, or null when the clock can't run this setup. */
    private fun regularLevels(setup: Setup): List<BlindLevel>? {
        val problem = setup.run { BlindSetupAdvisor.check(durationMinutes, roundLengthMinutes, smallestChip, startingChips) }
        if (problem != null) return null
        return runCatching {
            BlindStructureCalculator.generateSchedule(
                BlindStructureInput(
                    players = tournamentPreferences.getPlayerCount().coerceAtLeast(1),
                    targetDurationMinutes = setup.durationMinutes,
                    smallestChip = setup.smallestChip,
                    startingStack = setup.startingChips,
                    roundLengthMinutes = setup.roundLengthMinutes,
                    bigBlindAnteFromLevel = setup.anteFromLevel
                )
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
