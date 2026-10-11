package com.huntercoles.pokerpayout.tournament.presentation

import androidx.compose.runtime.Immutable
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblem
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment

/** How urgent the time left in the current level is. Checked most urgent first (B14). */
enum class TimeTone { NORMAL, LOW, CRITICAL }

/** Rebuys and add-ons: what each costs and how many the Bank has recorded. */
data class Purchases(
    val rebuyCents: Long = 0L,
    val addOnCents: Long = 0L,
    val rebuysTaken: Int = 0,
    val addOnsTaken: Int = 0
)

/** Where rebuys stand, for the clock's info list ("Rebuys open until level 6", "Rebuys closed"). */
sealed interface RebuyState {
    val taken: Int

    /** The rebuy amount is $0: the game has no rebuys. */
    data class Off(override val taken: Int = 0) : RebuyState

    /** Open; [untilLevel] is the last level they are allowed at, or null for no cutoff. */
    data class Open(val untilLevel: Int?, override val taken: Int) : RebuyState

    /** Closed after [afterLevel]. */
    data class Closed(val afterLevel: Int, override val taken: Int) : RebuyState
}

/**
 * What the clock's buttons show (back, the nudges, play/pause, next). It changes when the clock
 * starts, pauses, finishes or moves to another level; not every second, so the buttons skip the
 * clock's ticks.
 */
@Immutable
data class ClockButtons(
    val started: Boolean = false,
    val running: Boolean = false,
    val finished: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

/**
 * The blinds being played and what comes next: the blinds card's whole input. It changes at a level
 * change, not every second.
 */
@Immutable
data class BlindsUp(
    /** The level being played, or during a break the one just played. */
    val current: LevelSegment? = null,
    /** The break right after the current segment, if one is next. */
    val upcomingBreak: BreakSegment? = null,
    /** The level after the current segment (after a break: the level it leads into). */
    val next: LevelSegment? = null,
    val finished: Boolean = false
)

/**
 * The clock's state. A new one comes every second while the clock runs (only [elapsedSeconds]
 * changes), so the parts of the screen that don't show the time take narrower inputs ([buttons],
 * [blindsUp], [table]) and skip the tick.
 */
@Immutable
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
    /** S3 forced on with ⤢ until ✕ (a phone turned sideways shows it without this). */
    val isTableView: Boolean = false,
    /** The last level rebuys are allowed at; 0 = no cutoff (TournamentPreferences). */
    val rebuyUntilLevel: Int = 0,
    val purchases: Purchases = Purchases(),
    /** Breaks whose color-up is ticked off, by the level each follows. */
    val colorUpDoneAfterLevels: Set<Int> = emptySet(),
    /**
     * Your chip set (Tools → Chip set) once it is set up: the [timeline]'s color-ups use its chips,
     * and the break screen draws them in its colours (PP-091 #9). Null: a common home set's chips.
     */
    val chipSet: ChipSetChips? = null,
    /**
     * When the scheduled levels and breaks end, as wall-clock millis, if play goes on from now
     * without pausing. Null in overtime and once finished.
     */
    val endsAtWallClock: Long? = null,
    /** Chimes muted (the top bar's bell; Tools, Sound). */
    val isMuted: Boolean = false,
    /**
     * A mid-game blind change ([TimerIntent.KeepingLevel]) that can't be played, so it wasn't applied:
     * the clock runs on the setup it had. Cleared by the next change that works.
     */
    val midGameProblem: BlindSetupProblem? = null,
    /**
     * PP-111: the big moment the clock shows (the bubble, in the money, the final table, heads-up),
     * from the knockout that brought it until it has been on screen for a while; null between them.
     * Only while a clock exists. An Undo that takes the knockout back takes it away.
     */
    val moment: MomentBanner? = null,
    /** PP-111: the champion's screen is open; it opens with the last knockout, and closes with ✕ or an Undo. */
    val winnerOpen: Boolean = false
) {
    val gameDurationMinutes: Int get() = config.gameDurationMinutes

    /** The regular levels in the schedule ("Level 6 of 9"). */
    val regularLevelCount: Int get() = baseBlindLevels.size

    /** The next break after the current segment, or null when none is left. */
    val nextBreak: BreakSegment?
        get() {
            val segments = timeline.segments
            for (i in currentSegmentIndex + 1 until segments.size) {
                (segments[i] as? BreakSegment)?.let { return it }
            }
            return null
        }

    /** Seconds of play until the next break starts, or null when none is left. */
    val nextBreakInSeconds: Int? get() = nextBreak?.let { it.startSeconds - elapsedSeconds }

    /** Whether rebuys are off, open (until a level, or all game) or closed, with the count taken. */
    val rebuyState: RebuyState
        get() {
            val taken = purchases.rebuysTaken
            val until = rebuyUntilLevel
            return when {
                purchases.rebuyCents <= 0L -> RebuyState.Off(taken)
                until <= 0 -> RebuyState.Open(untilLevel = null, taken = taken)
                rebuysClosedAfter(until) -> RebuyState.Closed(afterLevel = until, taken = taken)
                else -> RebuyState.Open(untilLevel = until, taken = taken)
            }
        }

    /** Closed once a later level starts, or on the break that follows the cutoff level. */
    private fun rebuysClosedAfter(until: Int): Boolean {
        val played = currentLevelSegment?.level?.level ?: 0
        val breakAfter = currentBreak?.afterLevel ?: 0
        val pastCutoff = played > until || breakAfter >= until
        return hasTimerStarted && (isFinished || pastCutoff)
    }

    /** True once the current break's color-up has been ticked off. */
    val colorUpDone: Boolean get() = currentBreak?.let { it.afterLevel in colorUpDoneAfterLevels } ?: false

    /** Seconds played in the current level or break ("7:19 played"). */
    val segmentPlayedSeconds: Int
        get() = currentSegment?.let { it.durationSeconds - segmentRemainingSeconds } ?: 0

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
            val segments = timeline.segments
            for (i in currentSegmentIndex downTo 0) {
                (segments[i] as? LevelSegment)?.let { return it }
            }
            return null
        }

    val currentBlindLevelIndex: Int get() = currentLevelSegment?.index ?: 0

    val currentBlindLevel: BlindLevel? get() = if (isOnBreak) null else currentLevelSegment?.level

    /** The level after the current segment (after a break: the level it leads into). */
    val nextLevelSegment: LevelSegment? get() = timeline.nextLevelAfter(currentSegmentIndex)

    val nextBlindLevel: BlindLevel? get() = nextLevelSegment?.level

    /** The big blind the table's stats count stacks in: this level's, or before the start the first one's. */
    val statsBigBlind: Int get() = (currentLevelSegment ?: nextLevelSegment)?.level?.bigBlind ?: 0

    /** The buttons' input: see [ClockButtons]. */
    val buttons: ClockButtons
        get() = ClockButtons(
            started = hasTimerStarted,
            running = isRunning,
            finished = isFinished,
            canGoBack = canGoBack,
            canGoForward = canGoForward
        )

    /** PP-111: the moment slot's input: see [MomentSlotState]. */
    val momentSlot: MomentSlotState
        get() = MomentSlotState(moment = moment, champion = table.championName.takeUnless { winnerOpen })

    /** The blinds card's input: see [BlindsUp]. */
    val blindsUp: BlindsUp
        get() = BlindsUp(
            current = currentLevelSegment,
            upcomingBreak = timeline.segments.getOrNull(currentSegmentIndex + 1) as? BreakSegment,
            next = nextLevelSegment,
            finished = isFinished
        )

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

    companion object {
        const val LOW_FRACTION = 0.25
        const val CRITICAL_FRACTION = 0.10
    }
}

/**
 * PP-111: a big moment on the clock. [id] grows with every moment shown, so the same moment again
 * (after an Undo) shows again.
 */
@Immutable
data class MomentBanner(
    val moment: BigMoment,
    val id: Int,
    val playersLeft: Int,
    /** Heads-up: the two still in. */
    val names: List<String> = emptyList(),
    /** In the money: the least anyone left wins, the last paid place's prize, in cents. */
    val lowestPrizeCents: Long = 0L,
)

/**
 * PP-111: what the clock's moment slot shows: a [moment]'s banner, else, once there is a champion
 * and their screen is closed, the [champion]'s card. It changes with a moment, not every second.
 */
@Immutable
data class MomentSlotState(val moment: MomentBanner? = null, val champion: String? = null)

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
