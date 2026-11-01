package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.audio.custom.CustomCueSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The custom cue sound: a file the host picked for the clock's change cues, with its peak and end.
 * The file is a loan from this phone's file picker, so it lives in [PhonePrefs.FILE], which no backup
 * takes (like the music's picked songs). `sound_pack` (audio_prefs) stays the fallback pack; "Custom"
 * is [isOn], not a pack id. The keys at the foot of this file are saved data: never rename one.
 */
@Singleton
class CustomCueSoundPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = PhonePrefs.open(context)

    /** Whether the clock's change cues play the picked file instead of the pack's. */
    fun isOn(): Boolean = prefs.getBoolean(ON_KEY, false)

    fun setOn(on: Boolean) {
        prefs.edit().putBoolean(ON_KEY, on).apply()
    }

    /** The picked file, its peak and end clamped to its length; null when none was picked. */
    fun getSound(): CustomCueSound? {
        val uri = prefs.getString(URI_KEY, null) ?: return null
        return CustomCueSound(
            uri = uri,
            lengthMillis = prefs.getLong(LENGTH_KEY, 0),
            peakMillis = prefs.getLong(PEAK_KEY, 0),
            endMillis = prefs.getLong(END_KEY, 0),
        ).clamped()
    }

    /** Saves [sound] (a new pick, or a new peak or end), clamped; a newly picked file is no longer missing. */
    fun setSound(sound: CustomCueSound) {
        val clamped = sound.clamped()
        val edit = prefs.edit()
        if (prefs.getString(URI_KEY, null) != clamped.uri) edit.putBoolean(MISSING_KEY, false)
        edit.putString(URI_KEY, clamped.uri)
            .putLong(LENGTH_KEY, clamped.lengthMillis)
            .putLong(PEAK_KEY, clamped.peakMillis)
            .putLong(END_KEY, clamped.endMillis)
            .apply()
    }

    /** Moves the peak (clamped to 0..length, the end kept at or after it); a no-op with no file. */
    fun setPeak(millis: Long) {
        getSound()?.let { setSound(it.copy(peakMillis = millis)) }
    }

    /** Moves the end (clamped to peak..length; 0 plays to the end); a no-op with no file. */
    fun setEnd(millis: Long) {
        getSound()?.let { setSound(it.copy(endMillis = millis)) }
    }

    /** Whether the file could not be read at the last try. */
    fun isMissing(): Boolean = prefs.getBoolean(MISSING_KEY, false)

    fun setMissing(missing: Boolean) {
        prefs.edit().putBoolean(MISSING_KEY, missing).apply()
    }

    /** Forgets the picked file: the pack plays again. */
    fun clear() {
        prefs.edit()
            .putBoolean(ON_KEY, false)
            .remove(URI_KEY)
            .remove(LENGTH_KEY)
            .remove(PEAK_KEY)
            .remove(END_KEY)
            .putBoolean(MISSING_KEY, false)
            .apply()
    }

    companion object {
        // Saved data: never rename a key
        const val ON_KEY = "cue_custom_on"
        const val URI_KEY = "cue_custom_uri"
        const val LENGTH_KEY = "cue_custom_length_ms"
        const val PEAK_KEY = "cue_custom_peak_ms"
        const val END_KEY = "cue_custom_end_ms"
        const val MISSING_KEY = "cue_custom_missing"
    }
}
