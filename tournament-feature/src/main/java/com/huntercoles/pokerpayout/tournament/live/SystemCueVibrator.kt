package com.huntercoles.pokerpayout.tournament.live

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.domain.clock.SilentCue
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * The phone's vibrator for the quiet cues (PP-083). Vibrated as an alarm: the host turned the switch
 * on in the app, so it buzzes even with the phone on silent, which is the point in a quiet room.
 * Does nothing on a device without a vibrator (most tablets).
 */
class SystemCueVibrator @Inject constructor(
    @ApplicationContext private val context: Context,
) : CueVibrator {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun vibrate(cue: SilentCue) {
        val device = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createWaveform(timings(cue), NO_REPEAT)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(effect, ALARM)
            }
        }.onFailure { Timber.w(it, "Cue vibration failed") }
    }

    private fun timings(cue: SilentCue): LongArray = when (cue) {
        SilentCue.LEVEL_CHANGE -> LEVEL_CHANGE_TIMINGS
        SilentCue.ONE_MINUTE -> ONE_MINUTE_TIMINGS
    }

    private companion object {
        const val NO_REPEAT = -1

        /** Off, on, off, on...: three long pulses for a level change. */
        val LEVEL_CHANGE_TIMINGS = longArrayOf(0, 450, 200, 450, 200, 450)

        /** Two short taps for one minute left. */
        val ONE_MINUTE_TIMINGS = longArrayOf(0, 120, 120, 120)

        val ALARM: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
