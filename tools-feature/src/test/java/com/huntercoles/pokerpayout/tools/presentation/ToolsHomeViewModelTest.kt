package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The Tools tab's Sound section against real (in-memory) audio preferences: it shows what is saved,
 * saves what changes under the same keys the old volume dialog used, and plays the chime on request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ToolsHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val soundManager: SoundManager = mockk(relaxed = true)
    private lateinit var context: Context

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
    fun aFreshInstallHasTheSoundOnAtFullVolume() {
        val state = newViewModel().uiState.value
        assertTrue(state.soundOn)
        assertEquals(1f, state.volume, 0f)
    }

    @Test
    fun showsTheVolumeAndMuteSavedByTheOldVolumeDialog() {
        // The keys the Settings tile's dialog wrote before M2: "volume" and "is_muted" in audio_prefs.
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("volume", 0.4f)
            .putBoolean("is_muted", true)
            .commit()
        val state = newViewModel().uiState.value
        assertFalse(state.soundOn)
        assertEquals(0.4f, state.volume, 0f)
    }

    @Test
    fun turningTheSoundOffMutesAndKeepsTheVolume() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(ToolsHomeIntent.SetVolume(0.6f))
        viewModel.acceptIntent(ToolsHomeIntent.SetSoundOn(false))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.soundOn)
        assertEquals(0.6f, viewModel.uiState.value.volume, 0f)
        val saved = AudioPreferences(context)
        assertTrue(saved.getIsMuted())
        assertEquals(0.6f, saved.getVolume(), 0f)

        viewModel.acceptIntent(ToolsHomeIntent.SetSoundOn(true))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.soundOn)
        assertFalse(AudioPreferences(context).getIsMuted())
    }

    @Test
    fun theVolumeStaysBetweenSilentAndFull() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(ToolsHomeIntent.SetVolume(1.5f))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1f, viewModel.uiState.value.volume, 0f)
        viewModel.acceptIntent(ToolsHomeIntent.SetVolume(-0.2f))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, viewModel.uiState.value.volume, 0f)
    }

    @Test
    fun testChimePlaysTheLevelChime() {
        newViewModel().acceptIntent(ToolsHomeIntent.TestChime)
        verify(exactly = 1) { soundManager.playSound(CoreR.raw.blind_level_up) }
    }

    private fun newViewModel(): ToolsHomeViewModel {
        val preferences = AudioPreferences(context)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ToolsHomeViewModel(preferences, soundManager) as T
        }
        return ViewModelProvider(store, factory)[ToolsHomeViewModel::class.java].also {
            dispatcher.scheduler.advanceUntilIdle()
        }
    }
}
