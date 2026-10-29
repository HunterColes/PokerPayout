package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.audio.music.MusicPlayer
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Tools tab's Sound and music section (S7): the way into Cue sounds, saying whether the cue
 * sounds are on and which pack plays; the quiet cues (PP-083), a vibration and a gold flash of the
 * clock at each level change and with a minute left; and the way into the music, with what plays.
 */
data class ToolsHomeUiState(
    /** The cue sounds on (not muted); switched on Cue sounds or with the bells. */
    val soundOn: Boolean = true,
    val vibrate: Boolean = AudioPreferences.DEFAULT_VIBRATE_CUES,
    val flash: Boolean = AudioPreferences.DEFAULT_FLASH_CUES,
    /** False on a device that can't vibrate (most tablets): the row isn't shown. */
    val canVibrate: Boolean = true,
    /**
     * PP-081: notifications are off for the app, so the live clock can't show in the shade or on the
     * lock screen; the section offers the way to turn them on. Read from the system by the screen.
     */
    val notificationsOff: Boolean = false,
    /** The cue sound pack's id ([SoundPacks]). */
    val soundPack: String = SoundPacks.default.id,
    val music: MusicSummary = MusicSummary(),
    /** The currency amounts show in (PP-114), for the Currency row; the route reads it from MoneyFormat. */
    val currency: AppCurrency = AppCurrency.DEFAULT,
)

/** The music, in a line: how many songs, and the one playing. */
data class MusicSummary(
    val songs: Int = 0,
    val playing: Boolean = false,
    /** The current song's title; null with no songs. */
    val current: String? = null,
)

sealed interface ToolsHomeIntent {
    data class SetVibrate(val on: Boolean) : ToolsHomeIntent

    data class SetFlash(val on: Boolean) : ToolsHomeIntent

    /** Plays or pauses the music, from the Music row. */
    data object ToggleMusic : ToolsHomeIntent
}

/**
 * The Tools tab. The tools are plain links; the state here is the Sound and music section. The cue
 * sounds' switch and volume live on Cue sounds (#79), which writes the same [AudioPreferences] keys.
 */
@HiltViewModel
class ToolsHomeViewModel @Inject constructor(
    private val audioPreferences: AudioPreferences,
    private val music: MusicPlayer,
) : ViewModel() {

    private val sound = combine(
        audioPreferences.isMuted,
        audioPreferences.vibrateCues,
        audioPreferences.flashCues,
    ) { muted, vibrate, flash ->
        ToolsHomeUiState(soundOn = !muted, vibrate = vibrate, flash = flash)
    }

    val uiState: StateFlow<ToolsHomeUiState> =
        combine(sound, audioPreferences.soundPack, music.state) { state, pack, playing ->
            state.copy(
                soundPack = pack,
                music = MusicSummary(playing.playlist.tracks.size, playing.playing, playing.playlist.current?.title),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ToolsHomeUiState(
                soundOn = !audioPreferences.getIsMuted(),
                vibrate = audioPreferences.getVibrateCues(),
                flash = audioPreferences.getFlashCues(),
                soundPack = audioPreferences.getSoundPack(),
            ),
        )

    fun acceptIntent(intent: ToolsHomeIntent) {
        when (intent) {
            is ToolsHomeIntent.SetVibrate -> audioPreferences.setVibrateCues(intent.on)
            is ToolsHomeIntent.SetFlash -> audioPreferences.setFlashCues(intent.on)
            ToolsHomeIntent.ToggleMusic -> music.toggle()
        }
    }
}
