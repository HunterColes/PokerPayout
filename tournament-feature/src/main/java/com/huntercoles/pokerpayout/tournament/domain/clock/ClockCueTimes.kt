package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.constants.AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS

/** The moments the clock calls out. */
enum class ClockCueKind {
    /** The cue sound, a few seconds before a level, a break or the game ends, so it peaks on the change. */
    SOUND,

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
 * When the clock's cues fall, from the timeline alone. Every segment (level or break) has a cue sound
 * a lead before its end ([DEFAULT_LEAD_MILLIS] for the packs, a custom sound's peak), a level change at
 * its end, and a one-minute warning when it is longer than a minute. A segment shorter than the lead has
 * its sound at its start, played part way in ([soundOffsetMillis]) so its peak still lands on the change.
 *
 * Crossing-based, as the cue sound always was (B17): a cue sounds when a look at the clock passes it,
 * so pausing or resuming can't skip or repeat one, and one more than [GRACE_MILLIS] stale (the
 * device slept through it) stays silent rather than sounding late.
 */
object ClockCueTimes {
    /** A cue this late is skipped rather than played late. */
    const val GRACE_MILLIS = 2_000L

    private const val MILLIS_PER_SECOND = 1_000L

    /** The bundled packs' lead: from the cue sound's start to the change it peaks on. */
    const val DEFAULT_LEAD_MILLIS = LEVEL_CHANGE_SOUND_LEAD_SECONDS * MILLIS_PER_SECOND
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

    /** The cue sound of segment [index]: [leadMillis] before its end, never before its start. */
    private fun soundAt(timeline: ClockTimeline, index: Int, leadMillis: Long): Long {
        val segment = timeline.segments[index]
        val end = segment.endSeconds * MILLIS_PER_SECOND
        return maxOf(segment.startSeconds * MILLIS_PER_SECOND, end - leadMillis)
    }

    private fun dueIn(timeline: ClockTimeline, index: Int, leadMillis: Long): List<Due> {
        val segment = timeline.segments[index]
        val change = changeAt(timeline, index)
        val end = segment.endSeconds * MILLIS_PER_SECOND
        val sound = soundAt(timeline, index, leadMillis)
        val warning = end - WARNING_MILLIS
        return listOfNotNull(
            Due(ClockCue(ClockCueKind.ONE_MINUTE, warning, CueEvent.ONE_MINUTE), warning + GRACE_MILLIS)
                .takeIf { segment.durationSeconds * MILLIS_PER_SECOND > WARNING_MILLIS },
            // The cue sound keeps its old window: until GRACE_MILLIS after the change itself
            Due(ClockCue(ClockCueKind.SOUND, sound, change), end + GRACE_MILLIS),
            Due(ClockCue(ClockCueKind.LEVEL_CHANGE, end, change), end + GRACE_MILLIS),
        )
    }

    private fun allDue(timeline: ClockTimeline, leadMillis: Long): List<Due> =
        timeline.segments.indices.flatMap { dueIn(timeline, it, leadMillis) }

    /**
     * The cues passed between two looks at the clock, after [fromMillis] up to and including
     * [toMillis] of play, leaving out any too stale to sound. When one look passes two
     * cue sounds (segments shorter than the lead), the earlier change's comes first: it is at its peak now,
     * and the clock plays the first one.
     */
    fun crossed(
        timeline: ClockTimeline,
        fromMillis: Long,
        toMillis: Long,
        leadMillis: Long = DEFAULT_LEAD_MILLIS,
    ): List<ClockCue> {
        if (toMillis <= fromMillis) return emptyList()
        return allDue(timeline, leadMillis)
            .filter { it.cue.atMillis > fromMillis && it.cue.atMillis <= toMillis && toMillis <= it.latestMillis }
            .map { it.cue }
    }

    /** When the next cue falls after [elapsedMillis] of play, or null when none is left. */
    fun nextAfter(timeline: ClockTimeline, elapsedMillis: Long, leadMillis: Long = DEFAULT_LEAD_MILLIS): Long? =
        allDue(timeline, leadMillis)
            .map { it.cue.atMillis }
            .filter { it > elapsedMillis }
            .minOrNull()

    /**
     * How far into the sound file to start [cue] (a SOUND cue from [crossed] with the same [leadMillis]) when
     * it is played at [nowMillis] of play, so its peak, [leadMillis] in, lands on the change: the time since
     * the sound would have started, at least 0. Any other cue, or one not on [timeline], starts at 0.
     */
    fun soundOffsetMillis(timeline: ClockTimeline, cue: ClockCue, nowMillis: Long, leadMillis: Long): Long {
        val index = timeline.segments.indices.firstOrNull {
            cue.kind == ClockCueKind.SOUND &&
                soundAt(timeline, it, leadMillis) == cue.atMillis && changeAt(timeline, it) == cue.event
        } ?: return 0L
        val start = timeline.segments[index].endSeconds * MILLIS_PER_SECOND - leadMillis
        return maxOf(0L, nowMillis - start)
    }
}
