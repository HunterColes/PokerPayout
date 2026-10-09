package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
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
 * The cue sound picker: it shows the saved pack (the classic one on a fresh install), picking a pack
 * saves it, an id no pack has is ignored, and a slot's sound plays on request even with the sound
 * off, while an empty slot plays nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CueSoundsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val soundManager: SoundManager = mockk(relaxed = true)
    private lateinit var audio: AudioPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        audio = AudioPreferences(context)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun newViewModel(): CueSoundsViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CueSoundsViewModel(audio, soundManager) as T
        }
        return ViewModelProvider(store, factory)[CueSoundsViewModel::class.java].also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun aFreshInstallHasTheClassicPackPickedAndTheSoundOn() {
        val state = newViewModel().uiState.value
        assertEquals(SoundPacks.CLASSIC_ID, state.selected)
        assertEquals(SoundPacks.Classic, state.picked)
        assertTrue(state.soundOn)
        assertEquals(SoundPacks.all, state.packs)
    }

    @Test
    fun pickingAPackSavesIt() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(CueSoundsIntent.Pick(SoundPacks.CLASSIC_ID))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(SoundPacks.CLASSIC_ID, audio.getSoundPack())
    }

    @Test
    fun anIdNoPackHasIsIgnored() {
        newViewModel().acceptIntent(CueSoundsIntent.Pick("made up"))
        assertEquals(SoundPacks.CLASSIC_ID, audio.getSoundPack())
    }

    @Test
    fun aSavedPackThatIsGoneShowsTheDefaultPicked() {
        audio.setSoundPack("gone")
        assertEquals(SoundPacks.default, newViewModel().uiState.value.picked)
    }

    @Test
    fun aSlotPlaysItsSoundEvenWithTheSoundOff() {
        audio.setMuted(true)
        val viewModel = newViewModel()
        assertFalse(viewModel.uiState.value.soundOn)
        viewModel.acceptIntent(CueSoundsIntent.Preview(SoundPacks.CLASSIC_ID, CueEvent.BREAK_START))
        verify(exactly = 1) { soundManager.previewSound(CoreR.raw.blind_level_up) }
        verify(exactly = 0) { soundManager.playSound(any()) }
    }

    @Test
    fun anEmptySlotPlaysNothing() {
        newViewModel().acceptIntent(CueSoundsIntent.Preview(SoundPacks.CLASSIC_ID, CueEvent.ONE_MINUTE))
        verify(exactly = 0) { soundManager.previewSound(any()) }
    }
}
