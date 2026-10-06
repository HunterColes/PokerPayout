package com.huntercoles.pokerpayout.tournament.live

import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCueTimes
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What drives the live clock notification (PP-081), apart from Android so tests can run it on virtual
 * time. It looks at the saved clock only when something is due (a cue, the end of a level or break)
 * or when the saved clock changes ([poke]); never once a second. At each look it plays the cues
 * passed since the last one (through [ClockCues], which drops any the clock's own screen already
 * played) and hands [post] a card only when what the notification says has changed.
 *
 * While the clock runs it asks [keepAwake] to hold the CPU until just past the next look, so a cue
 * isn't late because the phone dozed; paused, it lets go. [run] returns when the notification should
 * go: the clock finished or was reset, or it has been paused for [PAUSED_KEEP_MILLIS].
 */
class LiveClockDriver(
    private val savedClock: () -> SavedClock.State?,
    private val time: TimeSource,
    private val cues: ClockCues,
    private val post: (LiveClockCard) -> Unit,
    private val keepAwake: (Long?) -> Unit,
) {
    private val pokes = Channel<Unit>(Channel.CONFLATED)
    private var anchor: ClockAnchor? = null
    private var seenMillis = 0L
    private var card: LiveClockCard? = null
    private var pausedSince: Long? = null

    /** The saved clock changed: look again now. */
    fun poke() {
        pokes.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            val wait = look() ?: break
            withTimeoutOrNull(wait) { pokes.receive() }
        }
        keepAwake(null)
    }

    /** Looks at the clock now: how long until the next look, or null when the notification should go. */
    internal fun look(): Long? {
        val now = Moment(time)
        val clock = savedClock()?.takeUnless { it.finished || it.timeline.isEmpty } ?: return null
        val elapsed = clock.anchor.elapsedAt(now)
        if (clock.anchor != anchor) {
            // Started, paused, resumed, jumped or nudged: a fresh look, with no cues for what it skipped
            anchor = clock.anchor
            seenMillis = elapsed
        }
        val wait = if (clock.anchor.running) lookRunning(clock, elapsed) else lookPaused(now.realtime)
        if (wait != null) {
            LiveClockCard.of(clock, elapsed, now.realtime)?.takeIf { it != card }?.let {
                card = it
                post(it)
            }
        }
        return wait
    }

    private fun lookRunning(clock: SavedClock.State, elapsed: Long): Long? {
        pausedSince = null
        cues.play(ClockCueTimes.crossed(clock.timeline, seenMillis, elapsed))
        seenMillis = elapsed
        val end = clock.timeline.endSeconds * MILLIS_PER_SECOND
        val next = ClockCueTimes.nextAfter(clock.timeline, elapsed) ?: end
        // Past the last level the game is over; the clock's screen marks it finished when it looks
        val wait = (next - elapsed + LATE_MILLIS).coerceIn(MIN_WAIT_MILLIS, MAX_WAIT_MILLIS).takeIf { elapsed < end }
        keepAwake(wait?.let { it + AWAKE_MARGIN_MILLIS })
        return wait
    }

    private fun lookPaused(realtime: Long): Long? {
        keepAwake(null)
        val since = pausedSince ?: realtime.also { pausedSince = it }
        val left = PAUSED_KEEP_MILLIS - (realtime - since)
        return left.takeIf { it > 0 }?.coerceAtMost(MAX_WAIT_MILLIS)
    }

    /** One reading of every clock, so the time of play and the card's end agree to the millisecond. */
    private class Moment(source: TimeSource) : TimeSource {
        val realtime = source.elapsedRealtimeMillis()
        private val wall = source.wallClockMillis()
        private val boot = source.bootCount()

        override fun elapsedRealtimeMillis() = realtime

        override fun wallClockMillis() = wall

        override fun bootCount() = boot
    }

    companion object {
        /**
         * A clock paused this long (on the monotonic clock) loses its notification: a pause for a
         * dinner break keeps Resume at hand, a game left paused for the night doesn't nag.
         */
        const val PAUSED_KEEP_MILLIS = 30 * 60_000L

        /** Every look is at most this far apart, whatever is due, as a safety net. */
        const val MAX_WAIT_MILLIS = 60_000L

        private const val MILLIS_PER_SECOND = 1_000L

        /** Look this much after a cue is due, so the look is past it. */
        private const val LATE_MILLIS = 20L
        private const val MIN_WAIT_MILLIS = 20L

        /** The CPU is held this much past the next look, in case the look runs late. */
        private const val AWAKE_MARGIN_MILLIS = 30_000L
    }
}
