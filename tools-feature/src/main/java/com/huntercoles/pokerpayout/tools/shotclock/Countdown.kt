package com.huntercoles.pokerpayout.tools.shotclock

/** Where one decision stands: not started yet, counting, paused, or out of time. */
enum class ShotClockPhase { Ready, Running, Paused, TimeUp }

/**
 * One decision's countdown on the monotonic clock (`TimeSource.elapsedRealtimeMillis`). While it
 * runs it keeps the moment it reaches zero ([deadline]); while stopped, the time that was left. The
 * time left is always worked out from the clock, never counted down tick by tick, so a late tick or
 * a sleeping phone can't make it drift.
 *
 * @property totalMillis the decision's whole time: the preset plus any time-bank cards played on it.
 * @property started false until the first tap: the clock waits, full, for the first decision.
 */
data class Countdown(
    val totalMillis: Long,
    val running: Boolean = false,
    val deadline: Long = 0,
    val leftWhenStopped: Long = totalMillis,
    val started: Boolean = false,
) {
    /** Milliseconds left at [now]; below zero once time is up (by how long ago it ran out). */
    fun signedLeftAt(now: Long): Long = if (running) deadline - now else leftWhenStopped

    /** Milliseconds left at [now], never below zero. */
    fun leftAt(now: Long): Long = signedLeftAt(now).coerceAtLeast(0)

    fun phaseAt(now: Long): ShotClockPhase = when {
        !started -> ShotClockPhase.Ready
        leftAt(now) == 0L -> ShotClockPhase.TimeUp
        running -> ShotClockPhase.Running
        else -> ShotClockPhase.Paused
    }

    /** Stopped where it is. Out of time, it stays out of time. */
    fun pausedAt(now: Long): Countdown = if (running) copy(running = false, leftWhenStopped = leftAt(now)) else this

    fun resumedAt(now: Long): Countdown =
        if (running || !started) this else copy(running = true, deadline = now + leftWhenStopped)

    /**
     * A time-bank card played: [millis] more for this decision. Out of time, the clock runs again with
     * just the card's time; paused, it stays paused with the time added.
     */
    fun extendedAt(now: Long, millis: Long): Countdown {
        val wasUp = phaseAt(now) == ShotClockPhase.TimeUp
        val left = leftAt(now) + millis
        val runs = running || wasUp
        return copy(
            totalMillis = totalMillis + millis,
            running = runs,
            deadline = if (runs) now + left else deadline,
            leftWhenStopped = left,
        )
    }

    companion object {
        /** Full, waiting for the first tap. */
        fun ready(totalMillis: Long) = Countdown(totalMillis = totalMillis)

        /** A new decision, counting down from [totalMillis] at [now]. */
        fun startedAt(totalMillis: Long, now: Long) =
            Countdown(totalMillis = totalMillis, running = true, deadline = now + totalMillis, started = true)
    }
}

/** The two warnings: ten seconds left, and time up. */
enum class ShotClockCue { TenSeconds, TimeUp }

/** When the warnings fall, and when a look at the clock is too late to give one. */
object ShotClockTiming {
    /** The first warning, with this many milliseconds left. */
    const val WARNING_MILLIS = 10_000L

    /**
     * A warning noticed more than this long after its moment is skipped: the screen was away (the
     * app in the background), and a buzz now would be about a decision already made.
     */
    const val LATE_MILLIS = 2_000L

    private const val MILLIS_PER_SECOND = 1_000L

    /**
     * The warning to give between two looks at the clock, [before] and [after] (signed milliseconds
     * left, as [Countdown.signedLeftAt]). One look that passes both gives only time up.
     */
    fun crossed(before: Long, after: Long): ShotClockCue? {
        val timeUp = before > 0 && after <= 0 && -after <= LATE_MILLIS
        val warning = before > WARNING_MILLIS && after <= WARNING_MILLIS && WARNING_MILLIS - after <= LATE_MILLIS
        return when {
            timeUp -> ShotClockCue.TimeUp
            warning && after > 0 -> ShotClockCue.TenSeconds
            else -> null
        }
    }

    /** The whole seconds shown with [leftMillis] to go: 30 at the start, 1 in the last second, 0 at the end. */
    fun shownSeconds(leftMillis: Long): Int = ((leftMillis.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

    /** How long until the seconds shown change, with [leftMillis] (above zero) to go. */
    fun untilNextSecond(leftMillis: Long): Long = leftMillis - (shownSeconds(leftMillis) - 1) * MILLIS_PER_SECOND
}
