package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.domain.model.ClockStatusProvider
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindSetupAdvisor
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The clock's level and break, for the Bank's rebuy and add-on cutoffs (PP-030), read from what the
 * clock saves: its anchor and the setup frozen at the start. It builds the same [ClockTimeline] the
 * clock shows, so the level here is the level on the clock, without depending on the clock's
 * ViewModel.
 *
 * The clock only writes when it starts, pauses, jumps or finishes; a running clock's level changes
 * with time alone. So [status] looks once a second while collected and emits only on a change.
 */
@Singleton
class SavedClockStatusProvider @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences,
    private val timeSource: TimeSource
) : ClockStatusProvider {

    private var cached: Pair<TimelineInput, ClockTimeline>? = null

    override val status: Flow<ClockStatus> = flow {
        while (currentCoroutineContext().isActive) {
            emit(current())
            delay(LOOK_EVERY_MS)
        }
    }.distinctUntilChanged()

    /** The status right now. */
    fun current(): ClockStatus {
        val started = timerPreferences.getHasTimerStarted()
        if (!started) return ClockStatus.NOT_STARTED
        val timeline = timeline()
        val elapsedSeconds = ((timerPreferences.getClock()?.elapsedAt(timeSource) ?: 0L) / MILLIS_PER_SECOND).toInt()
        val index = timeline.segmentIndexAt(elapsedSeconds)
        val segment = timeline.segments.getOrNull(index)
        val levelSegment = timeline.segments.take(index + 1).lastOrNull { it is LevelSegment } as LevelSegment?
        return ClockStatus(
            started = true,
            level = levelSegment?.level?.level ?: 1,
            onBreak = segment is BreakSegment,
            breakAfterLevels = timeline.segments.filterIsInstance<BreakSegment>().map { it.afterLevel },
            finished = timerPreferences.getIsFinished()
        )
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

    private fun timeline(): ClockTimeline {
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

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val LOOK_EVERY_MS = 1_000L
    }
}
