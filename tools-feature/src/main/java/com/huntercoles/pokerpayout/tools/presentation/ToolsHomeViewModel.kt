package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.huntercoles.pokerpayout.core.R as CoreR

/** The Tools tab's Sound section (S7): the clock's chime, on or off, and how loud. */
data class ToolsHomeUiState(
    val soundOn: Boolean = true,
    /** 0 to 1. Kept while the sound is off, so turning it back on restores it. */
    val volume: Float = 1f,
)

sealed interface ToolsHomeIntent {
    data class SetSoundOn(val on: Boolean) : ToolsHomeIntent

    data class SetVolume(val volume: Float) : ToolsHomeIntent

    /** Plays the level chime once at the current volume. */
    data object TestChime : ToolsHomeIntent
}

/**
 * The Tools tab. The tools are plain links; the state here is the Sound section, which used to be
 * a volume dialog behind a "Settings" tile. It reads and writes the same [AudioPreferences] (same
 * keys), so a saved volume or mute carries over.
 */
@HiltViewModel
class ToolsHomeViewModel @Inject constructor(
    private val audioPreferences: AudioPreferences,
    private val soundManager: SoundManager,
) : ViewModel() {

    val uiState: StateFlow<ToolsHomeUiState> =
        combine(audioPreferences.isMuted, audioPreferences.volume) { muted, volume ->
            ToolsHomeUiState(soundOn = !muted, volume = volume)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ToolsHomeUiState(soundOn = !audioPreferences.getIsMuted(), volume = audioPreferences.getVolume()),
        )

    fun acceptIntent(intent: ToolsHomeIntent) {
        when (intent) {
            is ToolsHomeIntent.SetSoundOn -> audioPreferences.setMuted(!intent.on)
            is ToolsHomeIntent.SetVolume -> audioPreferences.setVolume(intent.volume)
            ToolsHomeIntent.TestChime -> soundManager.playSound(CoreR.raw.blind_level_up)
        }
    }
}
