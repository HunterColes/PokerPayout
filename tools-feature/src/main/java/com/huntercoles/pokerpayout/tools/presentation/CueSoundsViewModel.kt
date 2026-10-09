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

/** Cue sounds (S18): the sound packs, the one the clock plays, and whether the sound is on at all. */
data class CueSoundsUiState(
    val packs: List<SoundPack> = SoundPacks.all,
    /** The picked pack's id; one no pack has any more shows the default picked. */
    val selected: String = SoundPacks.default.id,
    val soundOn: Boolean = true,
) {
    /** The pack the clock plays. */
    val picked: SoundPack get() = packs.firstOrNull { it.id == selected } ?: SoundPacks.default
}

sealed interface CueSoundsIntent {
    data class Pick(val packId: String) : CueSoundsIntent

    /** Plays one slot's sound once, even with the sound off: hearing it is the point. */
    data class Preview(val packId: String, val event: CueEvent) : CueSoundsIntent
}

/** The cue sound picker. Picking saves the pack (`sound_pack` in audio_prefs); the clock plays it from its next cue. */
@HiltViewModel
class CueSoundsViewModel @Inject constructor(
    private val audioPreferences: AudioPreferences,
    private val soundManager: SoundManager,
) : ViewModel() {

    val uiState: StateFlow<CueSoundsUiState> =
        combine(audioPreferences.soundPack, audioPreferences.isMuted) { pack, muted ->
            CueSoundsUiState(selected = pack, soundOn = !muted)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CueSoundsUiState(selected = audioPreferences.getSoundPack(), soundOn = !audioPreferences.getIsMuted()),
        )

    fun acceptIntent(intent: CueSoundsIntent) {
        when (intent) {
            is CueSoundsIntent.Pick -> packOf(intent.packId)?.let { audioPreferences.setSoundPack(it.id) }
            is CueSoundsIntent.Preview -> packOf(intent.packId)?.soundFor(intent.event)?.let(soundManager::previewSound)
        }
    }

    private fun packOf(id: String): SoundPack? = uiState.value.packs.firstOrNull { it.id == id }
}
