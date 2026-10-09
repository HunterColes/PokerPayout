package com.huntercoles.pokerpayout.tools.shotclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

/** The phone's beeper and vibrator for the shot clock's warnings. An interface so tests can count them. */
interface ShotClockSignals {
    /** A short beep at [volume] (0 to 1, the Sound section's slider). */
    fun beep(cue: ShotClockCue, volume: Float)

    fun buzz(cue: ShotClockCue)

    /** Lets go of the beeper when the screen goes. */
    fun release() = Unit
}

/**
 * Gives a warning the way the Sound section in Tools says: a beep unless the sound is off, a buzz
 * if Vibrate is on, and a flash if Flash the clock is on. The same switches as the tournament
 * clock, so a quiet room stays quiet; but its own beeper, never the clock's chime player, so the
 * tournament clock's chime is never cut off or unloaded.
 */
class ShotClockAlerts @Inject constructor(
    private val audio: AudioPreferences,
    private val signals: ShotClockSignals,
) {
    /** Beeps and buzzes for [cue] as the switches say; true if the face should flash too. */
    fun alert(cue: ShotClockCue): Boolean {
        if (!audio.getIsMuted()) signals.beep(cue, audio.getVolume())
        if (audio.getVibrateCues()) signals.buzz(cue)
        return audio.getFlashCues()
    }

    fun release() = signals.release()
}

/**
 * The system's tone generator and vibrator. Beeps are synthesised (no sound file) on the media
 * stream, like the clock's chime, at the Sound section's volume. The vibration is an alarm's, as
 * the clock's quiet cues are: the host turned Vibrate on in the app, so it buzzes on silent too.
 * Nothing here throws: a phone without a vibrator or a busy audio system simply stays quiet.
 */
class SystemShotClockSignals @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShotClockSignals {

    private var tones: ToneGenerator? = null
    private var tonesVolume = -1

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun beep(cue: ShotClockCue, volume: Float) {
        val percent = (volume.coerceIn(0f, 1f) * MAX_VOLUME).roundToInt()
        if (percent == 0) return
        runCatching {
            val generator = tones?.takeIf { tonesVolume == percent } ?: run {
                tones?.release()
                ToneGenerator(AudioManager.STREAM_MUSIC, percent).also {
                    tones = it
                    tonesVolume = percent
                }
            }
            when (cue) {
                ShotClockCue.TenSeconds -> generator.startTone(ToneGenerator.TONE_PROP_ACK, WARNING_TONE_MILLIS)
                ShotClockCue.TimeUp -> generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TIME_UP_TONE_MILLIS)
            }
        }.onFailure { Timber.w(it, "Shot clock beep failed") }
    }

    override fun buzz(cue: ShotClockCue) {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        val timings = if (cue == ShotClockCue.TimeUp) TIME_UP_TIMINGS else WARNING_TIMINGS
        val effect = VibrationEffect.createWaveform(timings, NO_REPEAT)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(effect, ALARM)
            }
        }.onFailure { Timber.w(it, "Shot clock vibration failed") }
    }

    override fun release() {
        runCatching { tones?.release() }
        tones = null
        tonesVolume = -1
    }

    private companion object {
        const val MAX_VOLUME = 100
        const val NO_REPEAT = -1
        const val WARNING_TONE_MILLIS = 300
        const val TIME_UP_TONE_MILLIS = 900

        /** Two short taps with ten seconds left. */
        val WARNING_TIMINGS = longArrayOf(0, 120, 120, 120)

        /** Three long pulses when time is up. */
        val TIME_UP_TIMINGS = longArrayOf(0, 450, 200, 450, 200, 450)

        val ALARM: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
