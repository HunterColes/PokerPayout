package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.audio.music.ClockPhase

/** Where the saved clock is, for the music ("Play with the clock"). */
object ClockPhases {
    private const val MILLIS_PER_SECOND = 1_000L

    /** The phase of [clock] at [elapsedMillis] of play; not started (or reset) is [ClockPhase.IDLE]. */
    fun of(clock: SavedClock.State?, elapsedMillis: Long): ClockPhase = when {
        clock == null || clock.timeline.isEmpty -> ClockPhase.IDLE
        clock.finished || elapsedMillis >= clock.timeline.endSeconds * MILLIS_PER_SECOND -> ClockPhase.FINISHED
        !clock.anchor.running -> ClockPhase.PAUSED
        clock.timeline.segmentAt((elapsedMillis / MILLIS_PER_SECOND).toInt()) is BreakSegment -> ClockPhase.BREAK
        else -> ClockPhase.LEVEL
    }

    /**
     * When the running [clock] next moves from one segment to another after [elapsedMillis] (the next
     * level or break starts, or the game ends), in play milliseconds; null when paused or over.
     */
    fun nextChange(clock: SavedClock.State?, elapsedMillis: Long): Long? {
        if (clock == null || !clock.anchor.running || clock.finished) return null
        return clock.timeline.segments
            .map { it.endSeconds * MILLIS_PER_SECOND }
            .firstOrNull { it > elapsedMillis }
    }
}
