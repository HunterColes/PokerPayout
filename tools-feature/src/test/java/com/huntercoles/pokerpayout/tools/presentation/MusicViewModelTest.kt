package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.music.BundledTrack
import com.huntercoles.pokerpayout.core.audio.music.MusicLibrary
import com.huntercoles.pokerpayout.core.audio.music.MusicPlayer
import com.huntercoles.pokerpayout.core.audio.music.MusicState
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.audio.music.Playlist
import com.huntercoles.pokerpayout.core.audio.music.RepeatMode
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import com.huntercoles.pokerpayout.core.preferences.PhonePrefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The Music screen's ViewModel: it shows the player's playlist and state, passes the buttons on,
 * saves the clock settings, names the picked files and looks for gone ones off the main thread
 * (here on the test dispatcher), and adds a built-in song by its id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MusicViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val library = FakeLibrary()
    private val songs = listOf(MusicTrack("content://music/1", "Night Owl"), MusicTrack("content://music/2", "Rain"))
    private val musicState = MutableStateFlow(MusicState(playlist = Playlist().add(songs, Random(0))))
    private val player: MusicPlayer = mockk(relaxed = true) { every { state } returns musicState }
    private lateinit var preferences: MusicPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf(MusicPreferences.FILE, PhonePrefs.FILE).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        preferences = MusicPreferences(context)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun newViewModel(): MusicViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MusicViewModel(player, preferences, library, dispatcher) as T
        }
        return ViewModelProvider(store, factory)[MusicViewModel::class.java].also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun showsThePlayersListAndWhatPlays() {
        musicState.value = musicState.value.copy(playing = true, missing = setOf(songs[1].ref))
        val state = newViewModel().uiState.value
        assertEquals(songs, state.tracks)
        assertEquals(songs[0], state.current)
        assertEquals(1, state.position)
        assertTrue(state.playing)
        assertEquals(setOf(songs[1].ref), state.missing)
        assertFalse(state.nothingPlayable)
    }

    @Test
    fun openingTheScreenLooksForFilesThatHaveGone() {
        library.gone += songs[1].ref
        newViewModel()
        verify(exactly = 1) { player.setMissing(setOf(songs[1].ref)) }
    }

    @Test
    fun pickedFilesAreKeptNamedAndAdded() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(MusicIntent.AddPicked(listOf("content://picked/a.mp3", "content://picked/b.ogg")))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("content://picked/a.mp3", "content://picked/b.ogg"), library.kept)
        verify(exactly = 1) {
            player.add(listOf(MusicTrack("content://picked/a.mp3", "a.mp3"), MusicTrack("content://picked/b.ogg", "b.ogg")))
        }
    }

    @Test
    fun aPickerClosedWithNothingPickedAddsNothing() {
        newViewModel().acceptIntent(MusicIntent.AddPicked(emptyList()))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(library.kept.isEmpty())
        verify(exactly = 0) { player.add(any()) }
    }

    @Test
    fun theButtonsGoToThePlayer() {
        val viewModel = newViewModel()
        listOf(
            MusicIntent.TogglePlay,
            MusicIntent.Next,
            MusicIntent.Previous,
            MusicIntent.SetShuffle(true),
            MusicIntent.CycleRepeat,
            MusicIntent.SetVolume(0.3f),
            MusicIntent.PlayTrack(songs[1].ref),
            MusicIntent.Move(1, 0),
            MusicIntent.Remove(songs[0].ref),
        ).forEach(viewModel::acceptIntent)
        verify(exactly = 1) { player.toggle() }
        verify(exactly = 1) { player.next() }
        verify(exactly = 1) { player.previous() }
        verify(exactly = 1) { player.setShuffle(true) }
        verify(exactly = 1) { player.setRepeat(RepeatMode.ALL) }
        verify(exactly = 1) { player.setVolume(0.3f) }
        verify(exactly = 1) { player.playTrack(songs[1].ref) }
        verify(exactly = 1) { player.move(1, 0) }
        verify(exactly = 1) { player.remove(songs[0].ref) }
    }

    @Test
    fun theClockSettingsAreSavedAndShown() {
        val viewModel = newViewModel()
        assertFalse(viewModel.uiState.value.autoPlay)
        viewModel.acceptIntent(MusicIntent.SetAutoPlay(true))
        viewModel.acceptIntent(MusicIntent.SetBreakMusic(BreakMusic.QUIET))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.autoPlay)
        assertEquals(BreakMusic.QUIET, viewModel.uiState.value.breakMusic)
        assertTrue(preferences.getAutoPlay())
        assertEquals(BreakMusic.QUIET, preferences.getBreakMusic())
    }

    @Test
    fun editTurnsTheListIntoMoveAndRemove() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(MusicIntent.SetEditing(true))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.editing)
        viewModel.acceptIntent(MusicIntent.SetEditing(false))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.editing)
    }

    @Test
    fun noSongsComeWithTheAppYet() {
        assertTrue(newViewModel().uiState.value.bundled.isEmpty())
    }

    @Test
    fun aBuiltInSongIsAddedByItsId() {
        val viewModel = newViewModel()
        val blues = BundledTrack("blues", "Slow Blues", res = 1, credit = "Jane Doe, CC0")
        viewModel.bundled = listOf(blues)
        viewModel.acceptIntent(MusicIntent.AddBundled("blues"))
        viewModel.acceptIntent(MusicIntent.AddBundled("not here"))
        verify(exactly = 1) { player.add(listOf(MusicTrack("bundled:blues", "Slow Blues"))) }
    }

    /** Files in [gone] have gone; it names a file after the last part of its URI. */
    private class FakeLibrary : MusicLibrary {
        val gone = mutableSetOf<String>()
        val kept = mutableListOf<String>()

        override fun open(player: MediaPlayer, ref: String) = ref !in gone

        override fun exists(ref: String) = ref !in gone

        override fun keep(uris: List<String>): List<MusicTrack> {
            kept += uris
            return uris.map { MusicTrack(it, it.substringAfterLast('/')) }
        }

        override fun forget(ref: String) = Unit
    }
}
