package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The quiet cues (PP-083): a vibration and a gold flash of the clock, for a muted phone or a quiet room. */
enum class SilentCue {
    /** The level (or break) has changed: three long pulses, two gold flashes. */
    LEVEL_CHANGE,

    /** One minute left: two short taps, one flash. */
    ONE_MINUTE,
}

/** Buzzes the phone for a quiet cue. An interface so tests can count the buzzes. */
fun interface CueVibrator {
    fun vibrate(cue: SilentCue)
}

/**
 * What the cues crossed at one look set off, given the Sound section's switches: the [sound] of one
 * event from the sound pack (null for none), and at most one vibration and one flash.
 */
data class CueActions(val sound: CueEvent?, val vibrate: SilentCue?, val flash: SilentCue?) {
    companion object {
        fun of(crossed: List<ClockCue>, vibrateOn: Boolean, flashOn: Boolean): CueActions {
            val kinds = crossed.map { it.kind }.toSet()
            // A level change outranks the minute warning when one look passes both (a late look)
            val silent = when {
                ClockCueKind.LEVEL_CHANGE in kinds -> SilentCue.LEVEL_CHANGE
                ClockCueKind.ONE_MINUTE in kinds -> SilentCue.ONE_MINUTE
                else -> null
            }
            // So does the change's sound (the chime's moment) over the minute's
            val sound = crossed.firstOrNull { it.kind == ClockCueKind.CHIME }?.event
                ?: crossed.firstOrNull { it.kind == ClockCueKind.ONE_MINUTE }?.event
            return CueActions(
                sound = sound,
                vibrate = silent?.takeIf { vibrateOn },
                flash = silent?.takeIf { flashOn },
            )
        }
    }
}

/**
 * Plays the clock's cues: each change's sound from the chosen sound pack ([SoundPacks]; muted or
 * not, as the Sound section says, inside [SoundManager]), the vibration and the flash. Two clocks
 * may report the same cue: the clock's screen while the app is alive, and the live clock
 * notification's service in the background (PP-081). Each cue plays
 * once: one reported again within [SAME_CUE_MILLIS] (on the monotonic clock) is the same moment.
 * Called on the main thread.
 */
@Singleton
class ClockCues @Inject constructor(
    private val soundManager: SoundManager,
    private val audioPreferences: AudioPreferences,
    private val vibrator: CueVibrator,
    private val timeSource: TimeSource,
) {
    private val flashEvents = MutableSharedFlow<SilentCue>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Flashes for the clock's screen. One that comes while no screen collects is dropped, not kept for later. */
    val flashes: SharedFlow<SilentCue> = flashEvents.asSharedFlow()

    /** The cues played lately, with when (monotonic). */
    private val played = mutableMapOf<ClockCue, Long>()

    /** The packs to pick from; a test puts in its own. */
    internal var packs: List<SoundPack> = SoundPacks.all

    /** The pack the host picked; the default if it is gone. */
    private fun pack(): SoundPack = packs.firstOrNull { it.id == audioPreferences.getSoundPack() } ?: SoundPacks.default

    /** Loads the pack's level sound (the chime) ahead of its first use. */
    fun preload() {
        pack().soundFor(CueEvent.LEVEL_UP)?.let(soundManager::preloadSound)
    }

    /** Plays what the [crossed] cues call for, leaving out any already played. */
    fun play(crossed: List<ClockCue>) {
        if (crossed.isEmpty()) return
        val now = timeSource.elapsedRealtimeMillis()
        played.values.removeAll { now - it !in 0L until SAME_CUE_MILLIS }
        val fresh = crossed.filter { it !in played }
        fresh.forEach { played[it] = now }
        val actions = CueActions.of(fresh, audioPreferences.getVibrateCues(), audioPreferences.getFlashCues())
        actions.sound?.let { event -> pack().soundFor(event)?.let(soundManager::playSound) }
        actions.vibrate?.let { vibrator.vibrate(it) }
        actions.flash?.let { flashEvents.tryEmit(it) }
    }

    private companion object {
        /**
         * The same cue reported again within this long is the one already played. A real second pass
         * (after a jump back or a nudge) is at least a minute away, since every level and break is.
         */
        const val SAME_CUE_MILLIS = 5_000L
    }
}
