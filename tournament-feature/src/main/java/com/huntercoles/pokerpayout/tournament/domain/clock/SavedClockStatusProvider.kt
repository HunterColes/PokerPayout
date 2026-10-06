package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.domain.model.ClockStatus
import com.huntercoles.pokerpayout.core.domain.model.ClockStatusProvider
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
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
 * clock shows ([SavedClock]), so the level here is the level on the clock, without depending on the
 * clock's ViewModel.
 *
 * The clock only writes when it starts, pauses, jumps or finishes; a running clock's level changes
 * with time alone. So [status] looks once a second while collected and emits only on a change.
 */
@Singleton
class SavedClockStatusProvider @Inject constructor(
    private val timerPreferences: TimerPreferences,
    tournamentPreferences: TournamentPreferences,
    private val timeSource: TimeSource
) : ClockStatusProvider {

    private val saved = SavedClock(timerPreferences, tournamentPreferences)

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
        val timeline = saved.timeline()
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

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val LOOK_EVERY_MS = 1_000L
    }
}
