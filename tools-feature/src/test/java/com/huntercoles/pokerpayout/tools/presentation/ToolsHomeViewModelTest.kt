package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.music.MusicPlayer
import com.huntercoles.pokerpayout.core.audio.music.MusicState
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.audio.music.Playlist
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
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
 * The Tools tab's Sound and music section against real (in-memory) audio preferences: it shows
 * whether the cue sounds are on and which pack plays, saves the quiet cues, and plays the music.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ToolsHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var context: Context
    private val musicState = MutableStateFlow(MusicState())
    private val music: MusicPlayer = mockk(relaxed = true) { every { state } returns musicState }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun theCueSoundsRowShowsTheMuteTheBellsSave() {
        assertTrue(newViewModel().uiState.value.soundOn)
        store.clear()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_muted", true).commit()
        assertFalse(newViewModel().uiState.value.soundOn)
    }

    @Test
    fun aFreshInstallVibratesAndFlashesTheClock() {
        val state = newViewModel().uiState.value
        assertTrue(state.vibrate)
        assertTrue(state.flash)
    }

    @Test
    fun theQuietCuesAreSwitchedOneByOneAndSaved() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(ToolsHomeIntent.SetVibrate(false))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.vibrate)
        assertTrue(viewModel.uiState.value.flash)

        viewModel.acceptIntent(ToolsHomeIntent.SetFlash(false))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.flash)
        val saved = AudioPreferences(context)
        assertFalse(saved.getVibrateCues())
        assertFalse(saved.getFlashCues())
        // New keys of their own: the volume and mute the old dialog saved are untouched
        assertEquals(1f, saved.getVolume(), 0f)
        assertFalse(saved.getIsMuted())
    }

    @Test
    fun mutingTheCueSoundsLeavesTheQuietCuesOn() {
        AudioPreferences(context).setMuted(true)
        val viewModel = newViewModel()
        assertFalse(viewModel.uiState.value.soundOn)
        assertTrue(viewModel.uiState.value.vibrate)
        assertTrue(viewModel.uiState.value.flash)
    }

    @Test
    fun aFreshInstallPlaysTheClassicPackAndHasNoSongs() {
        val state = newViewModel().uiState.value
        assertEquals(SoundPacks.CLASSIC_ID, state.soundPack)
        assertEquals(MusicSummary(), state.music)
    }

    @Test
    fun theMusicRowSaysWhatPlaysAndItsButtonPlaysOrPauses() {
        val viewModel = newViewModel()
        val songs = listOf(MusicTrack("content://music/1", "Night Owl"), MusicTrack("content://music/2", "Rain"))
        musicState.value = MusicState(playlist = Playlist().add(songs, Random(0)).select(songs[1].ref), playing = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(MusicSummary(songs = 2, playing = true, current = "Rain"), viewModel.uiState.value.music)

        viewModel.acceptIntent(ToolsHomeIntent.ToggleMusic)
        verify(exactly = 1) { music.toggle() }
    }

    @Test
    fun aPackFromALaterVersionStaysSaved() {
        AudioPreferences(context).setSoundPack("a pack from a later version")
        assertEquals("a pack from a later version", newViewModel().uiState.value.soundPack)
    }

    private fun newViewModel(): ToolsHomeViewModel {
        val preferences = AudioPreferences(context)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ToolsHomeViewModel(preferences, music) as T
        }
        return ViewModelProvider(store, factory)[ToolsHomeViewModel::class.java].also {
            dispatcher.scheduler.advanceUntilIdle()
        }
    }
}
