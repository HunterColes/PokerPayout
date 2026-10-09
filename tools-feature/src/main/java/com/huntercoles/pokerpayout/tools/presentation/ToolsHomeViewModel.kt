package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.music.MusicPlayer
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Tools tab's Sound section (S7): the clock's chime, on or off, and how loud; and the quiet cues
 * (PP-083), a vibration and a gold flash of the clock at each level change and with a minute left.
 * Then the ways into the cue sound packs and the music, with what each is set to.
 */
data class ToolsHomeUiState(
    val soundOn: Boolean = true,
    /** 0 to 1. Kept while the sound is off, so turning it back on restores it. */
    val volume: Float = 1f,
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
)

/** The music, in a line: how many songs, and the one playing. */
data class MusicSummary(
    val songs: Int = 0,
    val playing: Boolean = false,
    /** The current song's title; null with no songs. */
    val current: String? = null,
)

sealed interface ToolsHomeIntent {
    data class SetSoundOn(val on: Boolean) : ToolsHomeIntent

    data class SetVolume(val volume: Float) : ToolsHomeIntent

    /** Plays the level chime (the sound pack's) once at the current volume. */
    data object TestChime : ToolsHomeIntent

    data class SetVibrate(val on: Boolean) : ToolsHomeIntent

    data class SetFlash(val on: Boolean) : ToolsHomeIntent

    /** Plays or pauses the music, from the Music row. */
    data object ToggleMusic : ToolsHomeIntent
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
    private val music: MusicPlayer,
) : ViewModel() {

    private val sound = combine(
        audioPreferences.isMuted,
        audioPreferences.volume,
        audioPreferences.vibrateCues,
        audioPreferences.flashCues,
    ) { muted, volume, vibrate, flash ->
        ToolsHomeUiState(soundOn = !muted, volume = volume, vibrate = vibrate, flash = flash)
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
                volume = audioPreferences.getVolume(),
                vibrate = audioPreferences.getVibrateCues(),
                flash = audioPreferences.getFlashCues(),
                soundPack = audioPreferences.getSoundPack(),
            ),
        )

    fun acceptIntent(intent: ToolsHomeIntent) {
        when (intent) {
            is ToolsHomeIntent.SetSoundOn -> audioPreferences.setMuted(!intent.on)
            is ToolsHomeIntent.SetVolume -> audioPreferences.setVolume(intent.volume)
            ToolsHomeIntent.TestChime ->
                SoundPacks.byId(audioPreferences.getSoundPack()).soundFor(CueEvent.LEVEL_UP)?.let(soundManager::playSound)
            is ToolsHomeIntent.SetVibrate -> audioPreferences.setVibrateCues(intent.on)
            is ToolsHomeIntent.SetFlash -> audioPreferences.setFlashCues(intent.on)
            ToolsHomeIntent.ToggleMusic -> music.toggle()
        }
    }
}
