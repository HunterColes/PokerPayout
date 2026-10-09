package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.constants.AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS

/** The moments the clock calls out. */
enum class ClockCueKind {
    /** The chime, a few seconds before a level, a break or the game ends, so it peaks on the change. */
    CHIME,

    /** The level (or break) changes: vibrate and flash (PP-083). */
    LEVEL_CHANGE,

    /** One minute left in a level or break: vibrate and flash (PP-083). */
    ONE_MINUTE,
}

/**
 * One cue: what it is, when, in milliseconds of play, and the [event] it calls out, which picks its
 * sound from the sound pack (a level, a break starting or ending, the end of the game, or the
 * minute warning). Two equal cues are the same moment.
 */
data class ClockCue(
    val kind: ClockCueKind,
    val atMillis: Long,
    val event: CueEvent = if (kind == ClockCueKind.ONE_MINUTE) CueEvent.ONE_MINUTE else CueEvent.LEVEL_UP,
)

/**
 * When the clock's cues fall, from the timeline alone. Every segment (level or break) has a chime
 * [LEVEL_CHANGE_SOUND_LEAD_SECONDS] before its end, a level change at its end, and a one-minute
 * warning when it is longer than a minute.
 *
 * Crossing-based, as the chime always was (B17): a cue sounds when a look at the clock passes it,
 * so pausing or resuming can't skip or repeat one, and one more than [GRACE_MILLIS] stale (the
 * device slept through it) stays silent rather than sounding late.
 */
object ClockCueTimes {
    /** A cue this late is skipped rather than played late. */
    const val GRACE_MILLIS = 2_000L

    private const val MILLIS_PER_SECOND = 1_000L
    private const val WARNING_MILLIS = 60_000L

    /** A cue and the latest play time at which it may still sound. */
    private class Due(val cue: ClockCue, val latestMillis: Long)

    /** What the end of segment [index] is: a new level, a break starting or ending, or the end of the game. */
    private fun changeAt(timeline: ClockTimeline, index: Int): CueEvent {
        val next = timeline.segments.getOrNull(index + 1)
        return when {
            next == null -> CueEvent.GAME_OVER
            next is BreakSegment -> CueEvent.BREAK_START
            timeline.segments[index] is BreakSegment -> CueEvent.BREAK_END
            else -> CueEvent.LEVEL_UP
        }
    }

    private fun dueIn(timeline: ClockTimeline, index: Int): List<Due> {
        val segment = timeline.segments[index]
        val change = changeAt(timeline, index)
        val end = segment.endSeconds * MILLIS_PER_SECOND
        val chime = end - LEVEL_CHANGE_SOUND_LEAD_SECONDS * MILLIS_PER_SECOND
        val warning = end - WARNING_MILLIS
        return listOfNotNull(
            Due(ClockCue(ClockCueKind.ONE_MINUTE, warning, CueEvent.ONE_MINUTE), warning + GRACE_MILLIS)
                .takeIf { segment.durationSeconds * MILLIS_PER_SECOND > WARNING_MILLIS },
            // The chime keeps its old window: until GRACE_MILLIS after the change itself
            Due(ClockCue(ClockCueKind.CHIME, chime, change), end + GRACE_MILLIS),
            Due(ClockCue(ClockCueKind.LEVEL_CHANGE, end, change), end + GRACE_MILLIS),
        )
    }

    private fun allDue(timeline: ClockTimeline): List<Due> = timeline.segments.indices.flatMap { dueIn(timeline, it) }

    /**
     * The cues passed between two looks at the clock, after [fromMillis] up to and including
     * [toMillis] of play, leaving out any too stale to sound.
     */
    fun crossed(timeline: ClockTimeline, fromMillis: Long, toMillis: Long): List<ClockCue> {
        if (toMillis <= fromMillis) return emptyList()
        return allDue(timeline)
            .filter { it.cue.atMillis > fromMillis && it.cue.atMillis <= toMillis && toMillis <= it.latestMillis }
            .map { it.cue }
    }

    /** When the next cue falls after [elapsedMillis] of play, or null when none is left. */
    fun nextAfter(timeline: ClockTimeline, elapsedMillis: Long): Long? =
        allDue(timeline)
            .map { it.cue.atMillis }
            .filter { it > elapsedMillis }
            .minOrNull()
}
