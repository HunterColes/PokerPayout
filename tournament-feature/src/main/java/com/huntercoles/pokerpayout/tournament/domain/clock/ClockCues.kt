package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.custom.CustomCueSound
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.CustomCueSoundPreferences
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
            // So does the change's sound (the cue sound's moment) over the minute's
            val sound = crossed.firstOrNull { it.kind == ClockCueKind.SOUND }?.event
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
 * not, as the Sound section says, inside [SoundManager]), the vibration and the flash.
 *
 * With a custom cue sound on (epic #2, [CustomCueSoundPreferences]), the four change sounds play the
 * host's file instead, started [leadMillis] (its peak) before the change, part way in when the look
 * comes late, so the peak lands on the change. A file that can't be opened sets `cue_custom_missing`
 * and the pack's sound plays at the packs' lead instead. The minute warning and the big moments stay
 * on the pack. The clock's lookers ask [crossed] and [nextAfter] here, so they use the same lead. Two clocks
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
    // Nullable with a default so a test without a custom sound builds this as before; Hilt passes it
    private val customCue: CustomCueSoundPreferences? = null,
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

    /** The lead the last [crossed] used, to carry a cue across the host switching the custom sound mid-level. */
    private var lastLead: Long? = null

    /**
     * The changes whose sound played lately, with when (monotonic), keyed by the change's end on the
     * timeline: a switch of the lead moves a change's sound cue, and it must still play once.
     */
    private val sounded = mutableMapOf<Any, Long>()

    /** The custom cue sound when it is on, picked and readable at the last try; null plays the pack. */
    private val custom: CustomCueSound?
        get() =
            customCue?.takeIf { it.isOn() && !it.isMissing() }?.getSound()

    /** Play time from the cue sound's start to the change: the custom sound's peak, else the packs' lead. */
    fun leadMillis(): Long = custom?.peakMillis ?: ClockCueTimes.DEFAULT_LEAD_MILLIS

    /**
     * The cues a look passed, after [fromMillis] up to [toMillis] of play, at the current lead
     * ([ClockCueTimes.crossed]). When the lead changed since the last look (the custom sound switched
     * on or off, or its file went missing), a change's sound also counts as passed when it was still
     * ahead at the old lead and is due at the new one, so the switch doesn't skip it; [play] keeps
     * it from repeating.
     */
    fun crossed(
        timeline: ClockTimeline,
        fromMillis: Long,
        toMillis: Long,
        lead: Long = leadMillis(),
    ): List<ClockCue> {
        val before = lastLead ?: lead
        lastLead = lead
        val crossed = ClockCueTimes.crossed(timeline, fromMillis, toMillis, lead)
        if (before == lead) return crossed
        val sounds = ClockCueTimes.crossed(timeline, minOf(fromMillis, fromMillis + before - lead), toMillis, lead)
            .filter { it.kind == ClockCueKind.SOUND }
        return sounds + crossed.filter { it.kind != ClockCueKind.SOUND }
    }

    /** When the next cue falls after [elapsedMillis] of play, at the current lead ([ClockCueTimes.nextAfter]). */
    fun nextAfter(timeline: ClockTimeline, elapsedMillis: Long): Long? =
        ClockCueTimes.nextAfter(timeline, elapsedMillis, leadMillis())

    /** Looks at [timeline] between two looks of play and plays what was crossed, timed to [toMillis]. */
    fun look(timeline: ClockTimeline, fromMillis: Long, toMillis: Long) {
        val lead = leadMillis()
        play(crossed(timeline, fromMillis, toMillis, lead), Look(timeline, toMillis, lead))
    }

    /**
     * Loads the pack's level sound (the cue sound, and the custom sound's fallback) ahead of its first
     * use. A custom sound is left alone: it opens when it plays.
     */
    fun preload() {
        pack().soundFor(CueEvent.LEVEL_UP)?.let(soundManager::preloadSound)
    }

    /** Plays what the [crossed] cues call for, leaving out any already played. */
    fun play(crossed: List<ClockCue>) = play(crossed, look = null)

    private fun play(crossed: List<ClockCue>, look: Look?) {
        if (crossed.isEmpty()) return
        val now = timeSource.elapsedRealtimeMillis()
        played.values.removeAll { now - it !in 0L until SAME_CUE_MILLIS }
        sounded.values.removeAll { now - it !in 0L until SAME_CHANGE_MILLIS }
        val fresh = crossed.filter { it !in played }
            .filterNot { it.kind == ClockCueKind.SOUND && changeOf(it, look) in sounded }
        fresh.forEach { played[it] = now }
        fresh.filter { it.kind == ClockCueKind.SOUND }.forEach { sounded[changeOf(it, look)] = now }
        val actions = CueActions.of(fresh, audioPreferences.getVibrateCues(), audioPreferences.getFlashCues())
        actions.sound?.let { event ->
            val cue = fresh.firstOrNull { it.kind == ClockCueKind.SOUND }
            val handled = cue != null && event in CHANGE_EVENTS && playCustom(cue, look)
            if (!handled) pack().soundFor(event)?.let(soundManager::playSound)
        }
        actions.vibrate?.let { vibrator.vibrate(it) }
        actions.flash?.let { flashEvents.tryEmit(it) }
    }

    /**
     * Plays the custom sound for the change sound [cue]; false when the pack's should play instead
     * (custom off, or the file can't be opened). An unopened file is marked missing; when the packs'
     * cue time is still ahead, it is left for the look that crosses it (true here, so nothing plays early).
     */
    private fun playCustom(cue: ClockCue, look: Look?): Boolean {
        val sound = custom ?: return false
        val lead = sound.peakMillis
        val at = look?.nowMillis ?: cue.atMillis
        val offset = look?.let { ClockCueTimes.soundOffsetMillis(it.timeline, cue, at, lead) } ?: 0L
        val opened = soundManager.playFile(sound.uri, offset, sound.endMillis)
        if (!opened) {
            customCue?.setMissing(true)
            // The packs' cue for this change comes at the default lead: a later look crosses it as usual
            played.remove(cue)
            sounded.remove(changeOf(cue, look))
            lastLead = ClockCueTimes.DEFAULT_LEAD_MILLIS
        }
        val packAt = look?.timeline?.soundSegment(cue, lead)?.soundAtMillis(ClockCueTimes.DEFAULT_LEAD_MILLIS)
        return opened || (packAt != null && at < packAt)
    }

    /**
     * PP-111: a big moment's sound ([CueEvent.BIG_MOMENT], [CueEvent.CHAMPION]) from the picked pack,
     * muted or not as the Sound section says; an empty slot is silent. The moment itself shows on
     * the clock, so it neither vibrates nor flashes.
     */
    fun playMoment(event: CueEvent) {
        pack().soundFor(event)?.let(soundManager::playSound)
    }

    private companion object {
        /**
         * The same cue reported again within this long is the one already played. A real second pass
         * (after a jump back or a nudge) is at least a minute away, since every level and break is.
         */
        const val SAME_CUE_MILLIS = 5_000L

        /**
         * A change's sound is played once within this long, whatever lead found it. Longer than any
         * lead plus the grace, shorter than a level, so a real second pass still plays.
         */
        const val SAME_CHANGE_MILLIS = 55_000L

        /** The sounds timed to peak on a change: the only ones a custom sound replaces. */
        val CHANGE_EVENTS = setOf(CueEvent.LEVEL_UP, CueEvent.BREAK_START, CueEvent.BREAK_END, CueEvent.GAME_OVER)
    }
}

private const val MILLIS_PER_SECOND = 1_000L

/** One look at the clock: its [timeline], the play time [nowMillis] and the [lead] its cues were found with. */
private class Look(val timeline: ClockTimeline, val nowMillis: Long, val lead: Long)

/** Where this segment's cue sound falls at [lead]: [lead] before its end, never before its start. */
private fun ClockSegment.soundAtMillis(lead: Long): Long =
    maxOf(startSeconds * MILLIS_PER_SECOND, endSeconds * MILLIS_PER_SECOND - lead)

/** The segment whose cue sound, at [lead], is [cue]. */
private fun ClockTimeline.soundSegment(cue: ClockCue, lead: Long): ClockSegment? =
    segments.firstOrNull { it.soundAtMillis(lead) == cue.atMillis }

/** Which change a sound [cue] calls out: its end in play time, or the cue itself without a [look]. */
private fun changeOf(cue: ClockCue, look: Look?): Any =
    look?.timeline?.soundSegment(cue, look.lead)?.let { it.endSeconds * MILLIS_PER_SECOND } ?: cue
