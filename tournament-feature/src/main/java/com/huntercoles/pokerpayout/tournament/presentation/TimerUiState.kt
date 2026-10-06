package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblem
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment

/** How urgent the time left in the current level is. Checked most urgent first (B14). */
enum class TimeTone { NORMAL, LOW, CRITICAL }

data class TimerUiState(
    val config: BlindConfiguration = BlindConfiguration(),
    /** Seconds of play since the start, pauses excluded. The one clock value; all else derives from it. */
    val elapsedSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    /** True from the first start until reset. */
    val hasTimerStarted: Boolean = false,
    /** The regular levels, empty when the setup is invalid. */
    val baseBlindLevels: List<BlindLevel> = emptyList(),
    val timeline: ClockTimeline = ClockTimeline.EMPTY,
    /** Why the blind setup can't be played, or null when it can. */
    val setupProblem: BlindSetupProblem? = null,
    val showInvalidConfigDialog: Boolean = false,
    val table: TableStats = TableStats(),
    val isTableView: Boolean = false
) {
    val gameDurationMinutes: Int get() = config.gameDurationMinutes

    val currentSegmentIndex: Int get() = timeline.segmentIndexAt(elapsedSeconds)

    val currentSegment: ClockSegment? get() = timeline.segmentAt(elapsedSeconds)

    val isOnBreak: Boolean get() = currentSegment is BreakSegment

    val currentBreak: BreakSegment? get() = currentSegment as? BreakSegment

    /** Regular levels plus the overtime levels that have started. */
    val blindLevels: List<BlindLevel>
        get() = timeline.levels.filter { !it.isOvertime || it.startSeconds <= elapsedSeconds }.map { it.level }

    /** The level being played, or during a break the one just played. */
    val currentLevelSegment: LevelSegment?
        get() {
            val index = currentSegmentIndex
            if (index < 0) return null
            return timeline.segments.take(index + 1).lastOrNull { it is LevelSegment } as LevelSegment?
        }

    val currentBlindLevelIndex: Int get() = currentLevelSegment?.index ?: 0

    val currentBlindLevel: BlindLevel? get() = if (isOnBreak) null else currentLevelSegment?.level

    /** The level after the current segment (after a break: the level it leads into). */
    val nextLevelSegment: LevelSegment? get() = timeline.nextLevelAfter(currentSegmentIndex)

    val nextBlindLevel: BlindLevel? get() = nextLevelSegment?.level

    val overtimeLevelsRevealed: Int get() = timeline.overtimeLevelsRevealedAt(elapsedSeconds)

    val isOvertime: Boolean get() = !timeline.isEmpty && elapsedSeconds >= timeline.regularEndSeconds

    /** Seconds left in the current level or break. */
    val segmentRemainingSeconds: Int
        get() = currentSegment?.let { (it.endSeconds - elapsedSeconds).coerceIn(0, it.durationSeconds) } ?: 0

    val segmentProgress: Float
        get() = currentSegment?.let { 1f - segmentRemainingSeconds.toFloat() / it.durationSeconds } ?: 0f

    /** Until the scheduled end (regular levels and breaks); negative in overtime. */
    val tournamentRemainingSeconds: Int get() = timeline.regularEndSeconds - elapsedSeconds

    val tone: TimeTone
        get() {
            val segment = currentSegment ?: return TimeTone.NORMAL
            if (isFinished || !hasTimerStarted) return TimeTone.NORMAL
            val fractionLeft = segmentRemainingSeconds.toDouble() / segment.durationSeconds
            return when {
                fractionLeft <= CRITICAL_FRACTION -> TimeTone.CRITICAL
                fractionLeft <= LOW_FRACTION -> TimeTone.LOW
                else -> TimeTone.NORMAL
            }
        }

    val canGoBack: Boolean get() = currentSegmentIndex > 0

    /** Next is possible until the last overtime level. */
    val canGoForward: Boolean
        get() = currentSegmentIndex in 0 until timeline.segments.lastIndex

    val maxOvertimeLevels: Int get() = BlindStructureCalculator.MAX_OVERTIME_LEVELS

    companion object {
        const val LOW_FRACTION = 0.25
        const val CRITICAL_FRACTION = 0.10
    }
}

/** Clock text: "12:34" under an hour, "1:02:03" from an hour. */
object ClockFormat {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    fun clock(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = seconds / SECONDS_PER_HOUR
        val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val rest = seconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, rest)
        } else {
            "%d:%02d".format(minutes, rest)
        }
    }

    /** "2:47:12" for the tournament line; always shows hours. */
    fun long(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        return "%d:%02d:%02d".format(
            seconds / SECONDS_PER_HOUR,
            seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            seconds % SECONDS_PER_MINUTE
        )
    }

    /** "+0:20" style offset of a level from the start. */
    fun offset(seconds: Int): String {
        val minutes = seconds / SECONDS_PER_MINUTE
        return "+%d:%02d".format(minutes / SECONDS_PER_MINUTE, minutes % SECONDS_PER_MINUTE)
    }
}
