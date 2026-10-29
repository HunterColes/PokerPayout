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
 * Cue sounds, the clock's sound screen: the cue sounds on at full volume on a fresh install; the
 * switch and the volume save under the keys the old volume dialog and the bells use (`is_muted`,
 * `volume`); the saved pack shows (the classic one on a fresh install), picking a pack saves it, an
 * id no pack has is ignored, and a slot's sound plays on request even with the cue sounds off,
 * while an empty slot plays nothing.
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
    fun aFreshInstallHasTheCueSoundsOnAtFullVolume() {
        val state = newViewModel().uiState.value
        assertTrue(state.soundOn)
        assertEquals(1f, state.volume, 0f)
    }

    @Test
    fun showsTheVolumeAndMuteSavedByTheOldVolumeDialogAndTheBells() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("volume", 0.4f)
            .putBoolean("is_muted", true)
            .commit()
        audio = AudioPreferences(context)
        val state = newViewModel().uiState.value
        assertFalse(state.soundOn)
        assertEquals(0.4f, state.volume, 0f)
    }

    @Test
    fun turningTheCueSoundsOffMutesAndKeepsTheVolume() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(CueSoundsIntent.SetVolume(0.6f))
        viewModel.acceptIntent(CueSoundsIntent.SetSoundOn(false))
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.soundOn)
        assertEquals(0.6f, viewModel.uiState.value.volume, 0f)
        assertTrue(audio.getIsMuted())
        assertEquals(0.6f, audio.getVolume(), 0f)

        viewModel.acceptIntent(CueSoundsIntent.SetSoundOn(true))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.soundOn)
        assertFalse(audio.getIsMuted())
    }

    @Test
    fun theVolumeStaysBetweenSilentAndFull() {
        val viewModel = newViewModel()
        viewModel.acceptIntent(CueSoundsIntent.SetVolume(1.5f))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1f, viewModel.uiState.value.volume, 0f)
        viewModel.acceptIntent(CueSoundsIntent.SetVolume(-0.2f))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, viewModel.uiState.value.volume, 0f)
    }

    @Test
    fun theSwitchLeavesTheQuietCuesAsTheyAre() {
        newViewModel().acceptIntent(CueSoundsIntent.SetSoundOn(false))
        assertTrue(audio.getVibrateCues())
        assertTrue(audio.getFlashCues())
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
