package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.custom.CueSoundFiles
import com.huntercoles.pokerpayout.core.audio.custom.CustomCueSound
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.CustomCueSoundPreferences
import com.huntercoles.pokerpayout.core.preferences.PhonePrefs
import io.mockk.every
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
import org.junit.Assert.assertNull
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
 * while an empty slot plays nothing. The own sound (#22, phone_prefs `cue_custom_*`, a fake file
 * picker's files): a picked file is kept and on, its peak and end saved and back after a restart,
 * Preview plays it from 0 to its end with the mark at the peak; a missing file says so until a new
 * pick; a cancelled picker or an unreadable file changes nothing; a shorter file clamps the peak.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CueSoundsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val soundManager: SoundManager = mockk(relaxed = true)
    private lateinit var audio: AudioPreferences
    private lateinit var custom: CustomCueSoundPreferences
    private val files = FakeCueSoundFiles()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PhonePrefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        audio = AudioPreferences(context)
        custom = CustomCueSoundPreferences(context)
        every { soundManager.previewFile(any(), any(), any()) } returns true
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun newViewModel(): CueSoundsViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CueSoundsViewModel(audio, soundManager, custom, files, dispatcher) as T
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

    // ------------------------------------------------------------------ the own sound (#22)

    private fun CueSoundsViewModel.send(intent: CueSoundsIntent) {
        acceptIntent(intent)
        dispatcher.scheduler.advanceUntilIdle()
    }

    /** A process restart: the old ViewModel goes, and a new one reads phone_prefs afresh. */
    private fun restart(): CueSoundsViewModel {
        store.clear()
        custom = CustomCueSoundPreferences(ApplicationProvider.getApplicationContext())
        return newViewModel()
    }

    @Test
    fun aFreshInstallHasNoOwnSound() {
        val own = newViewModel().uiState.value.custom
        assertFalse(own.on)
        assertNull(own.sound)
        assertFalse(own.missing)
    }

    @Test
    fun aPickedFileIsKeptWithItsPeakAndPreviewsAgainstTheMark() {
        files.lengths[TEN] = 10_000
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        viewModel.send(CueSoundsIntent.SetCustomPeak(8_000))

        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences(PhonePrefs.FILE, Context.MODE_PRIVATE)
        assertEquals(8_000L, prefs.getLong("cue_custom_peak_ms", -1))
        assertTrue(prefs.getBoolean("cue_custom_on", false))
        assertEquals(TEN, prefs.getString("cue_custom_uri", null))
        assertEquals(10_000L, prefs.getLong("cue_custom_length_ms", -1))

        val again = restart()
        val own = again.uiState.value.custom
        assertTrue(own.on)
        assertEquals(CustomCueSound(uri = TEN, lengthMillis = 10_000, peakMillis = 8_000, endMillis = 0), own.sound)

        again.send(CueSoundsIntent.PreviewCustom)
        verify(exactly = 1) { soundManager.previewFile(TEN, 0, 0) }
        val previewed = again.uiState.value.custom
        assertEquals(1, previewed.previewRun)
        assertEquals(8_000L, previewed.markMillis)
        assertEquals(10_000L, previewed.barMillis)
    }

    @Test
    fun anEndPointStopsThePreviewAndShortensTheBar() {
        files.lengths[TEN] = 10_000
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        viewModel.send(CueSoundsIntent.SetCustomPeak(8_000))
        viewModel.send(CueSoundsIntent.SetCustomEnd(9_500))
        viewModel.send(CueSoundsIntent.PreviewCustom)
        verify(exactly = 1) { soundManager.previewFile(TEN, 0, 9_500) }
        assertEquals(9_500L, viewModel.uiState.value.custom.barMillis)
        assertEquals(8_000L, viewModel.uiState.value.custom.markMillis)
    }

    @Test
    fun aMissingFileSaysSoUntilANewPick() {
        custom.setSound(CustomCueSound(uri = TEN, lengthMillis = 10_000, peakMillis = 2_000))
        custom.setOn(true)
        custom.setMissing(true)
        files.lengths[OTHER] = 4_000
        val viewModel = newViewModel()
        assertTrue(viewModel.uiState.value.custom.missing)
        assertEquals(SoundPacks.default, viewModel.uiState.value.picked)

        viewModel.send(CueSoundsIntent.CustomPicked(OTHER))
        assertFalse(viewModel.uiState.value.custom.missing)
        assertFalse(custom.isMissing())
        assertEquals(OTHER, custom.getSound()?.uri)
    }

    @Test
    fun aFileGoneSinceTheLastCueShowsMissingOnOpening() {
        custom.setSound(CustomCueSound(uri = TEN, lengthMillis = 10_000))
        custom.setOn(true)
        files.gone += TEN
        assertTrue(newViewModel().uiState.value.custom.missing)
        assertTrue(custom.isMissing())
    }

    @Test
    fun aCancelledPickerLeavesTheSettingAsItWas() {
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.SetCustomOn(true))
        viewModel.send(CueSoundsIntent.CustomPicked(null))
        assertFalse(viewModel.uiState.value.custom.on)
        assertNull(custom.getSound())
        assertFalse(custom.isOn())
    }

    @Test
    fun aFileThatCannotBeReadIsRefusedAndNothingChanges() {
        files.lengths[TEN] = 10_000
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        viewModel.send(CueSoundsIntent.CustomPicked("content://unreadable"))
        assertTrue(viewModel.uiState.value.custom.refused)
        assertEquals(TEN, custom.getSound()?.uri)
        assertTrue(custom.isOn())
    }

    @Test
    fun aShorterFileClampsThePeakAndLetsGoOfTheOldOne() {
        files.lengths[TEN] = 10_000
        files.lengths[OTHER] = 4_000
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        viewModel.send(CueSoundsIntent.SetCustomPeak(8_000))
        viewModel.send(CueSoundsIntent.CustomPicked(OTHER))
        assertEquals(CustomCueSound(uri = OTHER, lengthMillis = 4_000, peakMillis = 4_000), viewModel.uiState.value.custom.sound)
        assertEquals(listOf(TEN), files.forgotten)
    }

    @Test
    fun stopUsingItForgetsTheFileAndThePackPlaysAgain() {
        files.lengths[TEN] = 10_000
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        viewModel.send(CueSoundsIntent.StopCustom)
        assertFalse(custom.isOn())
        assertNull(custom.getSound())
        assertEquals(listOf(TEN), files.forgotten)
        assertNull(viewModel.uiState.value.custom.sound)
    }

    @Test
    fun turningItOffKeepsTheFileAndOnWithoutAFileDoesNothing() {
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.SetCustomOn(true))
        assertFalse(viewModel.uiState.value.custom.on)
        files.lengths[TEN] = 10_000
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        viewModel.send(CueSoundsIntent.SetCustomOn(false))
        assertFalse(custom.isOn())
        assertEquals(TEN, custom.getSound()?.uri)
        viewModel.send(CueSoundsIntent.SetCustomOn(true))
        assertTrue(custom.isOn())
    }

    @Test
    fun theOwnSoundPreviewsEvenWithTheSoundOffAndAFailedOneShowsMissing() {
        audio.setMuted(true)
        files.lengths[TEN] = 10_000
        val viewModel = newViewModel()
        viewModel.send(CueSoundsIntent.CustomPicked(TEN))
        every { soundManager.previewFile(any(), any(), any()) } returns false
        viewModel.send(CueSoundsIntent.PreviewCustom)
        verify(exactly = 1) { soundManager.previewFile(TEN, 0, 0) }
        assertTrue(viewModel.uiState.value.custom.missing)
        assertEquals(0, viewModel.uiState.value.custom.previewRun)
    }

    /** The file picker's files: [lengths] are the readable ones; [gone] can no longer be opened. */
    private class FakeCueSoundFiles : CueSoundFiles {
        val lengths = mutableMapOf<String, Long>()
        val gone = mutableSetOf<String>()
        val forgotten = mutableListOf<String>()

        override fun keep(uri: String): CustomCueSound? = lengths[uri]?.let { CustomCueSound(uri = uri, lengthMillis = it) }

        override fun exists(uri: String): Boolean = uri !in gone

        override fun forget(uri: String) {
            forgotten += uri
        }
    }

    private companion object {
        const val TEN = "content://picked/ten_seconds.mp3"
        const val OTHER = "content://picked/four_seconds.mp3"
    }
}
