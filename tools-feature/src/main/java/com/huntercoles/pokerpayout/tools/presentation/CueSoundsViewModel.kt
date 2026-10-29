package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Cue sounds (S18), the clock's one sound screen: the cue sounds on or off (`is_muted`, the same
 * setting as the clock's and the Bank's bell), how loud, the sound packs and the one the clock plays.
 */
data class CueSoundsUiState(
    val packs: List<SoundPack> = SoundPacks.all,
    /** The picked pack's id; one no pack has any more shows the default picked. */
    val selected: String = SoundPacks.default.id,
    val soundOn: Boolean = true,
    /** 0 to 1. Kept while the cue sounds are off, so turning them back on restores it. */
    val volume: Float = 1f,
) {
    /** The pack the clock plays. */
    val picked: SoundPack get() = packs.firstOrNull { it.id == selected } ?: SoundPacks.default
}

sealed interface CueSoundsIntent {
    /** The cue sounds on or off: the bells' mute, under its own key. */
    data class SetSoundOn(val on: Boolean) : CueSoundsIntent

    data class SetVolume(val volume: Float) : CueSoundsIntent

    data class Pick(val packId: String) : CueSoundsIntent

    /** Plays one slot's sound once, even with the sound off: hearing it is the point. */
    data class Preview(val packId: String, val event: CueEvent) : CueSoundsIntent
}

/**
 * The cue sounds: on or off and the volume (`is_muted` and `volume` in audio_prefs, the keys the old
 * volume dialog and the Tools Sound section used), and the pack (`sound_pack`); the clock plays it
 * from its next cue. The music has its own volume, on the Music screen.
 */
@HiltViewModel
class CueSoundsViewModel @Inject constructor(
    private val audioPreferences: AudioPreferences,
    private val soundManager: SoundManager,
) : ViewModel() {

    val uiState: StateFlow<CueSoundsUiState> =
        combine(audioPreferences.soundPack, audioPreferences.isMuted, audioPreferences.volume) { pack, muted, volume ->
            CueSoundsUiState(selected = pack, soundOn = !muted, volume = volume)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CueSoundsUiState(
                selected = audioPreferences.getSoundPack(),
                soundOn = !audioPreferences.getIsMuted(),
                volume = audioPreferences.getVolume(),
            ),
        )

    fun acceptIntent(intent: CueSoundsIntent) {
        when (intent) {
            is CueSoundsIntent.SetSoundOn -> audioPreferences.setMuted(!intent.on)
            is CueSoundsIntent.SetVolume -> audioPreferences.setVolume(intent.volume)
            is CueSoundsIntent.Pick -> packOf(intent.packId)?.let { audioPreferences.setSoundPack(it.id) }
            is CueSoundsIntent.Preview -> packOf(intent.packId)?.soundFor(intent.event)?.let(soundManager::previewSound)
        }
    }

    private fun packOf(id: String): SoundPack? = uiState.value.packs.firstOrNull { it.id == id }
}
