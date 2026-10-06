package com.huntercoles.pokerpayout.tournament.domain.clock

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

/** One cue: what it is and when, in milliseconds of play. Two equal cues are the same moment. */
data class ClockCue(val kind: ClockCueKind, val atMillis: Long)

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

    private fun dueIn(segment: ClockSegment): List<Due> {
        val end = segment.endSeconds * MILLIS_PER_SECOND
        val chime = end - LEVEL_CHANGE_SOUND_LEAD_SECONDS * MILLIS_PER_SECOND
        val warning = end - WARNING_MILLIS
        return listOfNotNull(
            Due(ClockCue(ClockCueKind.ONE_MINUTE, warning), warning + GRACE_MILLIS)
                .takeIf { segment.durationSeconds * MILLIS_PER_SECOND > WARNING_MILLIS },
            // The chime keeps its old window: until GRACE_MILLIS after the change itself
            Due(ClockCue(ClockCueKind.CHIME, chime), end + GRACE_MILLIS),
            Due(ClockCue(ClockCueKind.LEVEL_CHANGE, end), end + GRACE_MILLIS),
        )
    }

    /**
     * The cues passed between two looks at the clock, after [fromMillis] up to and including
     * [toMillis] of play, leaving out any too stale to sound.
     */
    fun crossed(timeline: ClockTimeline, fromMillis: Long, toMillis: Long): List<ClockCue> {
        if (toMillis <= fromMillis) return emptyList()
        return timeline.segments
            .flatMap(::dueIn)
            .filter { it.cue.atMillis > fromMillis && it.cue.atMillis <= toMillis && toMillis <= it.latestMillis }
            .map { it.cue }
    }

    /** When the next cue falls after [elapsedMillis] of play, or null when none is left. */
    fun nextAfter(timeline: ClockTimeline, elapsedMillis: Long): Long? =
        timeline.segments
            .flatMap(::dueIn)
            .map { it.cue.atMillis }
            .filter { it > elapsedMillis }
            .minOrNull()
}
