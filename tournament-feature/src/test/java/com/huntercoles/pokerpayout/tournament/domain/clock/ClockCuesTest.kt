package com.huntercoles.pokerpayout.tournament.domain.clock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The quiet cues' decisions (PP-083) and the one place every cue plays from: what a crossed cue sets
 * off with each switch on or off, and that a cue reported by two clocks (the screen's and the live
 * notification's) plays once, on a fake monotonic clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClockCuesTest {

    private lateinit var context: Context
    private lateinit var audio: AudioPreferences
    private val sound: SoundManager = mockk(relaxed = true)
    private val buzzes = mutableListOf<SilentCue>()
    private val time = StepTime()

    /** A monotonic clock that moves only when told to. */
    private class StepTime : TimeSource {
        var now = 1_000_000L
        override fun elapsedRealtimeMillis() = now
        override fun wallClockMillis() = 1_760_000_000_000L + now
        override fun bootCount() = 1
    }

    private val chime = ClockCue(ClockCueKind.CHIME, 1_196_000)
    private val change = ClockCue(ClockCueKind.LEVEL_CHANGE, 1_200_000)
    private val warning = ClockCue(ClockCueKind.ONE_MINUTE, 1_140_000)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        audio = AudioPreferences(context)
    }

    private fun cues() = ClockCues(sound, audio, { buzzes += it }, time)

    // ------------------------------------------------------------------ decisions

    @Test
    fun aLevelChangeVibratesAndFlashesItsOwnWay() {
        val actions = CueActions.of(listOf(change), vibrateOn = true, flashOn = true)
        assertEquals(CueActions(sound = null, vibrate = SilentCue.LEVEL_CHANGE, flash = SilentCue.LEVEL_CHANGE), actions)
    }

    @Test
    fun theMinuteWarningIsQuietButNotSilent() {
        val actions = CueActions.of(listOf(warning), vibrateOn = true, flashOn = true)
        // Its sound is the pack's minute slot, which the classic pack leaves empty
        assertEquals(
            CueActions(sound = CueEvent.ONE_MINUTE, vibrate = SilentCue.ONE_MINUTE, flash = SilentCue.ONE_MINUTE),
            actions,
        )
    }

    @Test
    fun theChimeAloneVibratesNothing() {
        // The chime leads the change by 4 s; the buzz and the flash come on the change itself
        assertEquals(
            CueActions(sound = CueEvent.LEVEL_UP, vibrate = null, flash = null),
            CueActions.of(listOf(chime), true, true),
        )
    }

    @Test
    fun eachSwitchTurnsOffOnlyItsOwnCue() {
        assertEquals(
            CueActions(sound = CueEvent.LEVEL_UP, vibrate = null, flash = SilentCue.LEVEL_CHANGE),
            CueActions.of(listOf(chime, change), vibrateOn = false, flashOn = true),
        )
        assertEquals(
            CueActions(sound = CueEvent.LEVEL_UP, vibrate = SilentCue.LEVEL_CHANGE, flash = null),
            CueActions.of(listOf(chime, change), vibrateOn = true, flashOn = false),
        )
        assertEquals(
            CueActions(sound = CueEvent.ONE_MINUTE, vibrate = null, flash = null),
            CueActions.of(listOf(warning), false, false),
        )
    }

    @Test
    fun aLateLookThatPassesBothCallsOutTheChange() {
        val actions = CueActions.of(listOf(warning, chime, change), vibrateOn = true, flashOn = true)
        assertEquals(SilentCue.LEVEL_CHANGE, actions.vibrate)
        assertEquals(SilentCue.LEVEL_CHANGE, actions.flash)
        assertEquals(CueEvent.LEVEL_UP, actions.sound)
    }

    @Test
    fun aFreshInstallVibratesAndFlashes() {
        assertTrue(audio.getVibrateCues())
        assertTrue(audio.getFlashCues())
    }

    // ------------------------------------------------------------------ playing

    @Test
    fun playingACueSoundsBuzzesAndFlashesOnce() = runTest {
        val cues = cues()
        val flashes = mutableListOf<SilentCue>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { cues.flashes.toList(flashes) }

        cues.play(listOf(chime))
        time.now += 4_000
        cues.play(listOf(change))
        runCurrent()

        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
        assertEquals(listOf(SilentCue.LEVEL_CHANGE), buzzes)
        assertEquals(listOf(SilentCue.LEVEL_CHANGE), flashes)
        collecting.cancel()
    }

    @Test
    fun theSameCueFromTwoClocksPlaysOnce() {
        val cues = cues()

        cues.play(listOf(warning)) // the clock's screen
        time.now += 300
        cues.play(listOf(warning)) // the live notification's service, a moment later
        time.now += 3_700
        cues.play(listOf(chime))
        cues.play(listOf(chime))

        assertEquals(listOf(SilentCue.ONE_MINUTE), buzzes)
        verify(exactly = 1) { sound.playSound(any()) }
    }

    @Test
    fun theSameCueAgainAfterAJumpBackPlaysAgain() {
        val cues = cues()

        cues.play(listOf(warning))
        time.now += 61_000 // jumped back a level and played up to the same warning again
        cues.play(listOf(warning))

        assertEquals(listOf(SilentCue.ONE_MINUTE, SilentCue.ONE_MINUTE), buzzes)
    }

    @Test
    fun switchedOffCuesDoNothingButTheChimeStillRings() = runTest {
        audio.setVibrateCues(false)
        audio.setFlashCues(false)
        val cues = cues()
        val flashes = mutableListOf<SilentCue>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { cues.flashes.toList(flashes) }

        cues.play(listOf(chime, change))
        runCurrent()

        assertTrue(buzzes.isEmpty())
        assertTrue(flashes.isEmpty())
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
        collecting.cancel()
    }

    @Test
    fun nothingCrossedPlaysNothing() {
        cues().play(emptyList())
        assertTrue(buzzes.isEmpty())
        verify(exactly = 0) { sound.playSound(any()) }
    }

    // ------------------------------------------------------------------ sound packs

    /** A pack with a sound of its own for each moment but the minute (fake sound ids: the player is a mock). */
    private val bells = SoundPack(
        id = "bells",
        name = R.string.sound_pack_classic,
        description = R.string.sound_pack_classic_description,
        sounds = mapOf(
            CueEvent.LEVEL_UP to LEVEL_SOUND,
            CueEvent.BREAK_START to BREAK_START_SOUND,
            CueEvent.BREAK_END to BREAK_END_SOUND,
            CueEvent.GAME_OVER to GAME_OVER_SOUND,
        ),
    )

    private fun cuesWithBells() = cues().apply { packs = listOf(SoundPacks.Classic, bells) }

    @Test
    fun aFreshInstallPlaysTheClassicPackTheChimeAtEveryChange() {
        assertEquals(SoundPacks.CLASSIC_ID, audio.getSoundPack())
        val cues = cues()
        val breakStart = ClockCue(ClockCueKind.CHIME, 2_396_000, CueEvent.BREAK_START)
        val gameOver = ClockCue(ClockCueKind.CHIME, 9_596_000, CueEvent.GAME_OVER)

        cues.play(listOf(chime))
        cues.play(listOf(breakStart))
        cues.play(listOf(gameOver))
        cues.play(listOf(warning)) // the classic pack has no minute sound

        verify(exactly = 3) { sound.playSound(R.raw.blind_level_up) }
        verify(exactly = 3) { sound.playSound(any()) }
    }

    @Test
    fun thePickedPackPlaysEachMomentItsOwnSound() {
        audio.setSoundPack("bells")
        val cues = cuesWithBells()

        cues.play(listOf(chime))
        cues.play(listOf(ClockCue(ClockCueKind.CHIME, 2_396_000, CueEvent.BREAK_START)))
        cues.play(listOf(ClockCue(ClockCueKind.CHIME, 2_996_000, CueEvent.BREAK_END)))
        cues.play(listOf(ClockCue(ClockCueKind.CHIME, 9_596_000, CueEvent.GAME_OVER)))

        verify(exactly = 1) { sound.playSound(LEVEL_SOUND) }
        verify(exactly = 1) { sound.playSound(BREAK_START_SOUND) }
        verify(exactly = 1) { sound.playSound(BREAK_END_SOUND) }
        verify(exactly = 1) { sound.playSound(GAME_OVER_SOUND) }
        verify(exactly = 0) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun anEmptySlotIsSilentButStillBuzzesAndFlashes() = runTest {
        audio.setSoundPack("bells")
        val cues = cuesWithBells()
        val flashes = mutableListOf<SilentCue>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { cues.flashes.toList(flashes) }

        cues.play(listOf(warning))
        runCurrent()

        verify(exactly = 0) { sound.playSound(any()) }
        assertEquals(listOf(SilentCue.ONE_MINUTE), buzzes)
        assertEquals(listOf(SilentCue.ONE_MINUTE), flashes)
        collecting.cancel()
    }

    @Test
    fun aPackThatIsGoneFallsBackToTheClassic() {
        audio.setSoundPack("a pack from a later version")
        cuesWithBells().play(listOf(chime))
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun thePickedPacksChimeIsTheOneLoadedAhead() {
        audio.setSoundPack("bells")
        cuesWithBells().preload()
        verify(exactly = 1) { sound.preloadSound(LEVEL_SOUND) }
    }

    @Test
    fun theSettingsAreSavedUnderTheirOwnKeys() {
        audio.setVibrateCues(false)
        val again = AudioPreferences(context)
        assertFalse(again.getVibrateCues())
        assertTrue(again.getFlashCues())
        val raw = context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE)
        assertFalse(raw.getBoolean("vibrate_cues", true))
        assertNull(raw.all["volume"]) // nothing else was written
    }

    private companion object {
        const val LEVEL_SOUND = 101
        const val BREAK_START_SOUND = 102
        const val BREAK_END_SOUND = 103
        const val GAME_OVER_SOUND = 104
    }
}
