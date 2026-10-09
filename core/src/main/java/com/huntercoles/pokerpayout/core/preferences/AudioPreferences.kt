package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Sound section's settings (Tools, S7): the chime's volume and mute, and the quiet cues (PP-083)
 * that vibrate the phone and flash the clock at each level change and with one minute left; and the
 * cue sound pack the clock plays.
 */
@Singleton
class AudioPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)

    private val _volume = MutableStateFlow(getVolume())
    val volume: Flow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(getIsMuted())
    val isMuted: Flow<Boolean> = _isMuted.asStateFlow()

    private val _vibrateCues = MutableStateFlow(getVibrateCues())
    val vibrateCues: Flow<Boolean> = _vibrateCues.asStateFlow()

    private val _flashCues = MutableStateFlow(getFlashCues())
    val flashCues: Flow<Boolean> = _flashCues.asStateFlow()

    private val _soundPack = MutableStateFlow(getSoundPack())

    /** The id of the cue sound pack the clock plays ([SoundPacks]). */
    val soundPack: Flow<String> = _soundPack.asStateFlow()

    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat(VOLUME_KEY, clampedVolume).apply()
        _volume.value = clampedVolume
    }

    fun getVolume(): Float {
        return prefs.getFloat(VOLUME_KEY, DEFAULT_VOLUME)
    }

    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean(IS_MUTED_KEY, muted).apply()
        _isMuted.value = muted
    }

    fun getIsMuted(): Boolean {
        return prefs.getBoolean(IS_MUTED_KEY, false)
    }

    fun toggleMute() {
        setMuted(!getIsMuted())
    }

    /** PP-083: vibrate at each level change and with one minute left. On unless turned off. */
    fun getVibrateCues(): Boolean = prefs.getBoolean(VIBRATE_CUES_KEY, DEFAULT_VIBRATE_CUES)

    fun setVibrateCues(on: Boolean) {
        prefs.edit().putBoolean(VIBRATE_CUES_KEY, on).apply()
        _vibrateCues.value = on
    }

    /** PP-083: flash the clock gold at each level change and with one minute left. On unless turned off. */
    fun getFlashCues(): Boolean = prefs.getBoolean(FLASH_CUES_KEY, DEFAULT_FLASH_CUES)

    fun setFlashCues(on: Boolean) {
        prefs.edit().putBoolean(FLASH_CUES_KEY, on).apply()
        _flashCues.value = on
    }

    /** The cue sound pack's id. One no pack has any more plays the default ([SoundPacks.byId]). */
    fun getSoundPack(): String = prefs.getString(SOUND_PACK_KEY, null) ?: SoundPacks.default.id

    fun setSoundPack(id: String) {
        prefs.edit().putString(SOUND_PACK_KEY, id).apply()
        _soundPack.value = id
    }

    companion object {
        private const val VOLUME_KEY = "volume"
        private const val IS_MUTED_KEY = "is_muted"
        private const val DEFAULT_VOLUME = 1.0f

        // PP-083: new keys beside the old ones, so a saved volume and mute are untouched
        private const val VIBRATE_CUES_KEY = "vibrate_cues"
        private const val FLASH_CUES_KEY = "flash_cues"
        const val DEFAULT_VIBRATE_CUES = true
        const val DEFAULT_FLASH_CUES = true

        // Sound packs: one more key beside the others. With none saved the clock plays what it always did.
        private const val SOUND_PACK_KEY = "sound_pack"
    }
}
