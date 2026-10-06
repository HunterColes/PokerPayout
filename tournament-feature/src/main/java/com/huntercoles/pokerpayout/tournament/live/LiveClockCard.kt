package com.huntercoles.pokerpayout.tournament.live

import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock

/**
 * What the live clock notification says (PP-081), worked out from the saved clock alone: the level
 * or break, its blinds and what comes next, and either when it ends (running: the notification
 * counts down to that by itself, so nothing is re-posted every second) or how long is left
 * (paused). Two equal cards say the same thing, so a card is posted again only when it changes:
 * at a level change, a break, a pause or a resume, or when the time left is changed.
 */
data class LiveClockCard(
    val showing: Showing,
    val next: Next,
    /** Running: when the level or break ends, on the monotonic clock, to the second. Null when paused. */
    val endsAtRealtime: Long?,
    /** Paused: seconds left in the level or break, as the clock shows them. Null when running. */
    val pausedLeftSeconds: Int?,
) {
    val running: Boolean get() = endsAtRealtime != null

    /** The level or break on the clock now. */
    sealed interface Showing {
        data class Level(val blinds: BlindLevel, val overtime: Boolean) : Showing

        data class Break(val number: Int, val note: String) : Showing
    }

    /** What comes after it. */
    sealed interface Next {
        data class Level(val blinds: BlindLevel, val overtime: Boolean) : Next

        data class Break(val minutes: Int) : Next

        /** Nothing: the last level. */
        data object None : Next
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60

        /**
         * The card for [clock] at [elapsedMillis] of play, read at [realtimeMillis] on the monotonic
         * clock; null when the timeline is empty.
         */
        fun of(clock: SavedClock.State, elapsedMillis: Long, realtimeMillis: Long): LiveClockCard? {
            val timeline = clock.timeline
            val seconds = (elapsedMillis / MILLIS_PER_SECOND).toInt()
            val index = timeline.segmentIndexAt(seconds)
            val segment = timeline.segments.getOrNull(index) ?: return null
            val showing = when (segment) {
                is LevelSegment -> Showing.Level(segment.level, segment.isOvertime)
                is BreakSegment -> Showing.Break(segment.number, segment.message)
            }
            val next = when (val after = timeline.segments.getOrNull(index + 1)) {
                null -> Next.None
                is LevelSegment -> Next.Level(after.level, after.isOvertime)
                is BreakSegment -> Next.Break(after.durationSeconds / SECONDS_PER_MINUTE)
            }
            val leftMillis = (segment.endSeconds * MILLIS_PER_SECOND - elapsedMillis).coerceAtLeast(0L)
            return LiveClockCard(
                showing = showing,
                next = next,
                endsAtRealtime = if (clock.anchor.running) wholeSecond(realtimeMillis + leftMillis) else null,
                pausedLeftSeconds = if (clock.anchor.running) null else (segment.endSeconds - seconds).coerceAtLeast(0),
            )
        }

        /** To the nearest second, so a look a few milliseconds off doesn't make a new card. */
        private fun wholeSecond(millis: Long): Long = (millis + MILLIS_PER_SECOND / 2) / MILLIS_PER_SECOND * MILLIS_PER_SECOND
    }
}
