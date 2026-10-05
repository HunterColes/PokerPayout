package com.huntercoles.pokerpayout.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The clocks the tournament timer reads. Injected so tests can drive time (and simulate sleep, process
 * death and reboots) without real waiting.
 */
interface TimeSource {
    /** Monotonic milliseconds since boot, including deep sleep ([SystemClock.elapsedRealtime]). */
    fun elapsedRealtimeMillis(): Long

    /** Wall-clock milliseconds. Only used to bridge a reboot, which resets [elapsedRealtimeMillis]. */
    fun wallClockMillis(): Long

    /**
     * Identifies the current boot, so a saved [elapsedRealtimeMillis] is only trusted on the same boot;
     * -1 if unknown.
     */
    fun bootCount(): Int
}

@Singleton
class SystemTimeSource @Inject constructor(
    @ApplicationContext private val context: Context
) : TimeSource {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun wallClockMillis(): Long = System.currentTimeMillis()

    override fun bootCount(): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }
            .getOrDefault(-1)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    abstract fun bindTimeSource(source: SystemTimeSource): TimeSource
}

/**
 * Where the tournament clock stood at one instant: [elapsedMillis] of play (pauses excluded) and, while
 * [running], the readings of every clock at that instant. Elapsed time at any later moment is derived
 * from the monotonic clock, so it can't drift, stall in deep sleep, or be lost with the process.
 */
data class ClockAnchor(
    val elapsedMillis: Long = 0,
    val running: Boolean = false,
    val realtimeMillis: Long = 0,
    val wallMillis: Long = 0,
    val bootCount: Int = -1
) {
    /** Play time elapsed at the current moment of [time]. */
    fun elapsedAt(time: TimeSource): Long {
        if (!running) return elapsedMillis
        return elapsedMillis + sinceAnchor(time)
    }

    private fun sinceAnchor(time: TimeSource): Long {
        val now = time.elapsedRealtimeMillis()
        val bootNow = time.bootCount()
        val sameBoot = if (bootCount >= 0 && bootNow >= 0) bootCount == bootNow else now >= realtimeMillis
        return if (sameBoot && now >= realtimeMillis) {
            now - realtimeMillis
        } else {
            // Rebooted since the anchor: the monotonic clock restarted, so fall back to wall time.
            (time.wallClockMillis() - wallMillis).coerceAtLeast(0)
        }
    }

    companion object {
        fun stopped(elapsedMillis: Long) = ClockAnchor(elapsedMillis = elapsedMillis.coerceAtLeast(0))

        fun runningFrom(elapsedMillis: Long, time: TimeSource) = ClockAnchor(
            elapsedMillis = elapsedMillis.coerceAtLeast(0),
            running = true,
            realtimeMillis = time.elapsedRealtimeMillis(),
            wallMillis = time.wallClockMillis(),
            bootCount = time.bootCount()
        )
    }
}
