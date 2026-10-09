package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.music.BundledTrack
import com.huntercoles.pokerpayout.core.audio.music.BundledTracks
import com.huntercoles.pokerpayout.core.audio.music.MusicLibrary
import com.huntercoles.pokerpayout.core.audio.music.MusicPlayer
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.audio.music.RepeatMode
import com.huntercoles.pokerpayout.core.coroutines.IoDispatcher
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The Music screen (S17): the playlist and the player, how loud, playing with the clock, and the built-in songs. */
data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val currentRef: String? = null,
    /** 1-based place of the current song in play order; 0 with none. */
    val position: Int = 0,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val volume: Float = MusicPreferences.DEFAULT_VOLUME,
    /** Songs whose files can't be found. */
    val missing: Set<String> = emptySet(),
    val autoPlay: Boolean = false,
    val breakMusic: BreakMusic = MusicPreferences.DEFAULT_BREAK_MUSIC,
    /** The songs that come with the app ([BundledTracks]); none yet. */
    val bundled: List<BundledTrack> = BundledTracks.all,
    /** The list shows move and remove buttons instead of playing a song on a tap. */
    val editing: Boolean = false,
) {
    val current: MusicTrack? get() = tracks.firstOrNull { it.ref == currentRef }

    val nothingPlayable: Boolean get() = tracks.isNotEmpty() && tracks.all { it.ref in missing }

    fun inList(track: BundledTrack): Boolean = tracks.any { it.ref == track.ref }
}

sealed interface MusicIntent {
    data object TogglePlay : MusicIntent

    data object Next : MusicIntent

    data object Previous : MusicIntent

    data class SetShuffle(val on: Boolean) : MusicIntent

    /** Off, all songs, this song, off. */
    data object CycleRepeat : MusicIntent

    data class SetVolume(val volume: Float) : MusicIntent

    data class PlayTrack(val ref: String) : MusicIntent

    data class Remove(val ref: String) : MusicIntent

    data class Move(val from: Int, val to: Int) : MusicIntent

    /** The files the host picked in the system's file picker (content URIs). */
    data class AddPicked(val uris: List<String>) : MusicIntent

    data class AddBundled(val id: String) : MusicIntent

    data class SetAutoPlay(val on: Boolean) : MusicIntent

    data class SetBreakMusic(val mode: BreakMusic) : MusicIntent

    data class SetEditing(val on: Boolean) : MusicIntent
}

/**
 * The Music screen. The [MusicPlayer] plays and keeps the playlist; this shows it, passes the
 * buttons on, and does what reads storage (naming picked files, finding which have gone) off the
 * main thread. Each time the screen opens it looks for songs whose files have gone.
 */
@HiltViewModel
class MusicViewModel @Inject constructor(
    private val player: MusicPlayer,
    private val preferences: MusicPreferences,
    private val library: MusicLibrary,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val editing = MutableStateFlow(false)

    /** The built-in songs on offer; a test puts in its own. */
    internal var bundled: List<BundledTrack> = BundledTracks.all

    private val settings =
        combine(preferences.volume, preferences.autoPlay, preferences.breakMusic, editing) { volume, auto, breaks, edit ->
            MusicUiState(volume = volume, autoPlay = auto, breakMusic = breaks, editing = edit, bundled = bundled)
        }

    val uiState: StateFlow<MusicUiState> = combine(player.state, settings) { music, base ->
        base.copy(
            tracks = music.playlist.tracks,
            currentRef = music.playlist.currentRef,
            position = music.playlist.position,
            playing = music.playing,
            shuffle = music.playlist.shuffle,
            repeat = music.playlist.repeat,
            missing = music.missing,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MusicUiState())

    init {
        findMissing()
    }

    fun acceptIntent(intent: MusicIntent) {
        when (intent) {
            MusicIntent.TogglePlay -> player.toggle()
            MusicIntent.Next -> player.next()
            MusicIntent.Previous -> player.previous()
            is MusicIntent.SetShuffle -> player.setShuffle(intent.on)
            MusicIntent.CycleRepeat -> player.setRepeat(player.state.value.playlist.repeat.next())
            is MusicIntent.SetVolume -> player.setVolume(intent.volume)
            is MusicIntent.PlayTrack -> player.playTrack(intent.ref)
            is MusicIntent.Remove -> player.remove(intent.ref)
            is MusicIntent.Move -> player.move(intent.from, intent.to)
            is MusicIntent.AddPicked -> addPicked(intent.uris)
            is MusicIntent.AddBundled -> addBundled(intent.id)
            is MusicIntent.SetAutoPlay -> preferences.setAutoPlay(intent.on)
            is MusicIntent.SetBreakMusic -> preferences.setBreakMusic(intent.mode)
            is MusicIntent.SetEditing -> editing.value = intent.on
        }
    }

    private fun addBundled(id: String) {
        bundled.firstOrNull { it.id == id }?.let { player.add(listOf(it.toTrack())) }
    }

    private fun addPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val tracks = withContext(io) { library.keep(uris) }
            player.add(tracks)
        }
    }

    /** Marks the songs whose files have gone since the list was made (moved, deleted, a card taken out). */
    private fun findMissing() {
        viewModelScope.launch {
            val refs = player.state.value.playlist.tracks.map { it.ref }
            val gone = withContext(io) { refs.filterNot(library::exists).toSet() }
            player.setMissing(gone)
        }
    }
}
