package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.custom.CueSoundFiles
import com.huntercoles.pokerpayout.core.audio.custom.CustomCueSound
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.coroutines.IoDispatcher
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.CustomCueSoundPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Cue sounds (S18), the clock's one sound screen: the cue sounds on or off (`is_muted`, the same
 * setting as the clock's and the Bank's bell), how loud, the host's own sound file for the changes,
 * the sound packs and the one the clock plays (the fallback for the file, and the sound of the other
 * moments).
 */
data class CueSoundsUiState(
    val packs: List<SoundPack> = SoundPacks.all,
    /** The picked pack's id; one no pack has any more shows the default picked. */
    val selected: String = SoundPacks.default.id,
    val soundOn: Boolean = true,
    /** 0 to 1. Kept while the cue sounds are off, so turning them back on restores it. */
    val volume: Float = 1f,
    val custom: CustomSoundUi = CustomSoundUi(),
) {
    /** The pack the clock plays. */
    val picked: SoundPack get() = packs.firstOrNull { it.id == selected } ?: SoundPacks.default
}

/** The host's own change sound (phone_prefs `cue_custom_*`), and what the screen shows about it. */
data class CustomSoundUi(
    /** `cue_custom_on`: the four change sounds play the file instead of the pack's. */
    val on: Boolean = false,
    /** The kept file, its peak and end; null when none was picked. */
    val sound: CustomCueSound? = null,
    /** `cue_custom_missing`: the file could not be read at the last try, so the pack plays. */
    val missing: Boolean = false,
    /** The last pick was a file that can't be played, so nothing changed. */
    val refused: Boolean = false,
    /** Counts the previews started; each new value runs the preview bar once from the start. */
    val previewRun: Int = 0,
) {
    /** The preview bar's length: the end, or the whole file when it plays to the end. */
    val barMillis: Long get() = sound?.let { if (it.endMillis > 0) it.endMillis else it.lengthMillis } ?: 0

    /** Where the "change" mark sits on the bar: the peak. */
    val markMillis: Long get() = sound?.peakMillis ?: 0
}

sealed interface CueSoundsIntent {
    /** The cue sounds on or off: the bells' mute, under its own key. */
    data class SetSoundOn(val on: Boolean) : CueSoundsIntent

    data class SetVolume(val volume: Float) : CueSoundsIntent

    data class Pick(val packId: String) : CueSoundsIntent

    /** Plays one slot's sound once, even with the sound off: hearing it is the point. */
    data class Preview(val packId: String, val event: CueEvent) : CueSoundsIntent

    /** The system's file picker came back: the file's uri, or null when the host cancelled it. */
    data class CustomPicked(val uri: String?) : CueSoundsIntent

    /** The own sound on or off, keeping the file (with no file the screen opens the picker instead). */
    data class SetCustomOn(val on: Boolean) : CueSoundsIntent

    data class SetCustomPeak(val millis: Long) : CueSoundsIntent

    /** Where the file stops; 0 plays it to the end. */
    data class SetCustomEnd(val millis: Long) : CueSoundsIntent

    /** Plays the file from the start to its end, even with the sound off, and runs the preview bar. */
    data object PreviewCustom : CueSoundsIntent

    /** Lets go of the file: the grant is released and the pack plays again. */
    data object StopCustom : CueSoundsIntent
}

/**
 * The cue sounds: on or off and the volume (`is_muted` and `volume` in audio_prefs, the keys the old
 * volume dialog and the Tools Sound section used), the pack (`sound_pack`), and the host's own file
 * for the changes (`cue_custom_*` in phone_prefs: a loan from this phone's file picker, so no backup
 * carries it); the clock plays them from its next cue. The music has its own volume, on the Music screen.
 */
@HiltViewModel
class CueSoundsViewModel @Inject constructor(
    private val audioPreferences: AudioPreferences,
    private val soundManager: SoundManager,
    private val customPreferences: CustomCueSoundPreferences,
    private val files: CueSoundFiles,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val custom = MutableStateFlow(readCustom())

    val uiState: StateFlow<CueSoundsUiState> =
        combine(
            audioPreferences.soundPack,
            audioPreferences.isMuted,
            audioPreferences.volume,
            custom,
        ) { pack, muted, volume, own ->
            CueSoundsUiState(selected = pack, soundOn = !muted, volume = volume, custom = own)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CueSoundsUiState(
                selected = audioPreferences.getSoundPack(),
                soundOn = !audioPreferences.getIsMuted(),
                volume = audioPreferences.getVolume(),
                custom = custom.value,
            ),
        )

    init {
        checkFileStillThere()
    }

    fun acceptIntent(intent: CueSoundsIntent) {
        when (intent) {
            is CueSoundsIntent.SetSoundOn -> audioPreferences.setMuted(!intent.on)
            is CueSoundsIntent.SetVolume -> audioPreferences.setVolume(intent.volume)
            is CueSoundsIntent.Pick -> packOf(intent.packId)?.let { audioPreferences.setSoundPack(it.id) }
            is CueSoundsIntent.Preview -> packOf(intent.packId)?.soundFor(intent.event)?.let(soundManager::previewSound)
            is CueSoundsIntent.CustomPicked -> intent.uri?.let(::keep)
            is CueSoundsIntent.SetCustomOn -> setCustomOn(intent.on)
            is CueSoundsIntent.SetCustomPeak -> edit { customPreferences.setPeak(intent.millis) }
            is CueSoundsIntent.SetCustomEnd -> edit { customPreferences.setEnd(intent.millis) }
            CueSoundsIntent.PreviewCustom -> previewCustom()
            CueSoundsIntent.StopCustom -> stopCustom()
        }
    }

    private fun packOf(id: String): SoundPack? = uiState.value.packs.firstOrNull { it.id == id }

    private fun readCustom(previewRun: Int = 0): CustomSoundUi = CustomSoundUi(
        on = customPreferences.isOn(),
        sound = customPreferences.getSound(),
        missing = customPreferences.isMissing(),
        previewRun = previewRun,
    )

    private fun edit(write: () -> Unit) {
        write()
        custom.value = readCustom(custom.value.previewRun)
    }

    /** Keeps a newly picked file and turns it on; the old peak carries over, clamped to the new length. */
    private fun keep(uri: String) {
        viewModelScope.launch {
            val kept = withContext(io) { files.keep(uri) }
            if (kept == null) {
                custom.update { it.copy(refused = true) }
                return@launch
            }
            val old = customPreferences.getSound()
            if (old != null && old.uri != uri) withContext(io) { files.forget(old.uri) }
            customPreferences.setSound(kept.copy(peakMillis = old?.peakMillis ?: 0, endMillis = 0))
            customPreferences.setMissing(false)
            customPreferences.setOn(true)
            custom.value = readCustom(custom.value.previewRun)
        }
    }

    private fun setCustomOn(on: Boolean) {
        if (on && customPreferences.getSound() == null) return
        edit { customPreferences.setOn(on) }
    }

    private fun previewCustom() {
        val sound = customPreferences.getSound() ?: return
        val played = soundManager.previewFile(sound.uri, 0, sound.endMillis)
        if (!played) customPreferences.setMissing(true)
        custom.value = readCustom(custom.value.previewRun + if (played) 1 else 0)
    }

    private fun stopCustom() {
        val uri = customPreferences.getSound()?.uri
        customPreferences.clear()
        custom.value = readCustom(custom.value.previewRun)
        if (uri != null) viewModelScope.launch { withContext(io) { files.forget(uri) } }
    }

    /** A file that went away since the last cue (moved, deleted, its grant gone) says so at once. */
    private fun checkFileStillThere() {
        val uri = customPreferences.getSound()?.uri ?: return
        viewModelScope.launch {
            val there = withContext(io) { files.exists(uri) }
            if (!there && !customPreferences.isMissing()) edit { customPreferences.setMissing(true) }
        }
    }
}
