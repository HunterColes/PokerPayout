package com.huntercoles.pokerpayout.tournament.domain.clock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.audio.custom.CustomCueSound
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.CustomCueSoundPreferences
import com.huntercoles.pokerpayout.core.preferences.PhonePrefs
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.tournament.live.LiveClockDriver
import com.huntercoles.pokerpayout.tournament.presentation.FakeChipSets
import com.huntercoles.pokerpayout.tournament.presentation.NineSeats
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModelTest.FakeTimeSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.math.abs

/**
 * The custom cue sound's timing (#21, epic #2): with a fake clock and a fake player, the file starts its
 * peak before each change, part way in when the look comes late, so the peak lands on the change; an
 * unreadable file falls back to the pack's sound at the packs' lead; switching custom mid-level neither
 * repeats nor skips a cue; and the same holds through the live notification's driver and the clock's
 * screen looking together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CustomCueTimingTest {

    private lateinit var context: Context
    private lateinit var audio: AudioPreferences
    private lateinit var custom: CustomCueSoundPreferences
    private val sound: SoundManager = mockk(relaxed = true)

    /** The play time of the current look, which the fake player reads when it starts. */
    private var playNow = 0L

    /** Each start of the file: (play time, offset into the file). */
    private val starts = mutableListOf<Pair<Long, Long>>()
    private var fileOpens = true

    /** A monotonic clock that the tests move with play time. */
    private val time = object : TimeSource {
        override fun elapsedRealtimeMillis() = 5_000_000L + playNow
        override fun wallClockMillis() = 1_760_000_000_000L + playNow
        override fun bootCount() = 1
    }

    /** Ten 1-minute levels. */
    private val timeline = ClockTimeline.build(
        (1..10).map { BlindLevel(it, 25 * it, 50 * it, 0, it - 1) },
        roundLengthMinutes = 1,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("audio_prefs", PhonePrefs.FILE).forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        audio = AudioPreferences(context)
        custom = CustomCueSoundPreferences(context)
        every { sound.playFile(any(), any(), any()) } answers {
            if (fileOpens) starts += playNow to secondArg<Long>()
            fileOpens
        }
    }

    private fun pickFile(peak: Long = PEAK, on: Boolean = true) {
        custom.setSound(CustomCueSound(URI, lengthMillis = 10_000, peakMillis = peak))
        custom.setOn(on)
    }

    private fun cues() = ClockCues(sound, audio, {}, time, custom)

    /** Looks at the clock at each play time in [looks], as a looker does, from [from]. */
    private fun ClockCues.lookAt(vararg looks: Long, from: Long = 0L) {
        var seen = from
        looks.forEach {
            playNow = it
            look(timeline, seen, it)
            seen = it
        }
    }

    private fun peakOf(start: Pair<Long, Long>) = start.first + (PEAK - start.second)

    private fun assertPeakOnChange(start: Pair<Long, Long>, change: Long = 60_000L) {
        val peak = peakOf(start)
        assertTrue("peak at $peak, change at $change", abs(peak - change) <= 100)
    }

    @Test
    fun aLookOnTimeStartsTheFileItsPeakBeforeTheChange() {
        pickFile()
        cues().lookAt(*LongArray(60) { it * 1_000L })

        assertEquals(listOf(52_000L to 0L), starts)
        assertPeakOnChange(starts.single())
        verify(exactly = 0) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun aLateLookStartsPartWayInAndThePeakStillLands() {
        pickFile()
        cues().lookAt(51_000, 52_700, 53_700)

        assertEquals(listOf(52_700L to 700L), starts)
        assertPeakOnChange(starts.single())
    }

    @Test
    fun theFileEndIsPassedToThePlayer() {
        custom.setSound(CustomCueSound(URI, lengthMillis = 10_000, peakMillis = PEAK, endMillis = 9_000))
        custom.setOn(true)
        cues().lookAt(51_000, 52_000)
        verify(exactly = 1) { sound.playFile(URI, 0, 9_000) }
    }

    @Test
    fun theLooksWakeForTheCustomCue() {
        pickFile()
        assertEquals(PEAK, cues().leadMillis())
        assertEquals(52_000L, cues().nextAfter(timeline, 1_000))
        custom.setOn(false)
        assertEquals(56_000L, cues().nextAfter(timeline, 1_000))
    }

    @Test
    fun anUnreadableFilePlaysThePacksSoundAtThePacksLead() {
        pickFile()
        fileOpens = false
        val cues = cues()

        cues.lookAt(51_000, 52_000, 53_000, 54_000, 55_000)
        verify(exactly = 1) { sound.playFile(URI, 0, 0) }
        verify(exactly = 0) { sound.playSound(any()) } // not 8 s early
        assertTrue(custom.isMissing())

        cues.lookAt(56_000, 57_000, 60_000, from = 55_000)
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
        assertEquals(DEFAULT_LEAD, cues.leadMillis())
        // The next level's change tries no file again
        cues.lookAt(*LongArray(60) { 61_000L + it * 1_000 }, from = 60_000)
        verify(exactly = 1) { sound.playFile(any(), any(), any()) }
        verify(exactly = 2) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun anUnreadableFileFoundByALateLookPlaysThePackAtOnce() {
        pickFile()
        fileOpens = false
        cues().lookAt(50_000, 57_000)
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
        assertTrue(custom.isMissing())
    }

    @Test
    fun customOffPlaysThePackAndNoFile() {
        pickFile(on = false)
        cues().lookAt(*LongArray(60) { it * 1_000L })
        verify(exactly = 0) { sound.playFile(any(), any(), any()) }
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun theMinuteWarningAndTheBigMomentsStayOnThePack() {
        pickFile()
        val cues = cues()
        cues.play(listOf(ClockCue(ClockCueKind.ONE_MINUTE, 60_000)))
        cues.playMoment(com.huntercoles.pokerpayout.core.audio.packs.CueEvent.CHAMPION)
        verify(exactly = 0) { sound.playFile(any(), any(), any()) }
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) } // the classic pack's champion
    }

    @Test
    fun preloadLeavesTheCustomSoundAlone() {
        pickFile()
        cues().preload()
        verify(exactly = 1) { sound.preloadSound(R.raw.blind_level_up) }
        verify(exactly = 0) { sound.playFile(any(), any(), any()) }
    }

    @Test
    fun theSettingSurvivesARestart() {
        pickFile()
        val again = ClockCues(sound, AudioPreferences(context), {}, time, CustomCueSoundPreferences(context))
        again.lookAt(51_000, 52_000)
        assertEquals(listOf(52_000L to 0L), starts)
    }

    // ------------------------------------------------------------------ hardening

    @Test
    fun switchingCustomOnMidLevelBetweenLooksNeitherSkipsNorRepeats() {
        val cues = cues()
        cues.lookAt(51_000, 53_000) // the pack's cue (56 s) is still ahead
        pickFile() // its 52 s start has passed between the looks
        cues.lookAt(54_000, 55_000, 56_000, 57_000, 60_000, from = 53_000)

        assertEquals(listOf(54_000L to 2_000L), starts)
        assertPeakOnChange(starts.single())
        verify(exactly = 0) { sound.playSound(any()) }
    }

    @Test
    fun switchingCustomOffAfterItPlayedDoesNotPlayThePackToo() {
        pickFile()
        val cues = cues()
        cues.lookAt(51_000, 52_000, 53_000)
        custom.setOn(false)
        cues.lookAt(54_000, 56_000, 57_000, 60_000, from = 53_000)

        assertEquals(1, starts.size)
        verify(exactly = 0) { sound.playSound(any()) }
        // The next change plays the pack, once
        cues.lookAt(*LongArray(60) { 61_000L + it * 1_000 }, from = 60_000)
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun switchingCustomOffBeforeItsCuePlaysThePackOnTime() {
        pickFile()
        val cues = cues()
        cues.lookAt(50_000, 51_000)
        custom.setOn(false)
        cues.lookAt(52_000, 55_000, 56_000, from = 51_000)
        assertTrue(starts.isEmpty())
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
    }

    @Test
    fun aCueTheDeviceSleptThroughStaysSilent() {
        pickFile()
        cues().lookAt(51_000, 62_500) // past the change plus GRACE_MILLIS
        assertTrue(starts.isEmpty())
        verify(exactly = 0) { sound.playSound(any()) }
    }

    // ------------------------------------------------------------------ both lookers

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeTimeSource(dispatcher.scheduler)
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun theClocksScreenAndTheNotificationPlayEachChangeOnce() {
        Dispatchers.setMain(dispatcher)
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val tournament = TournamentPreferences(context).apply { setPlayerCount(10) }
        val timer = TimerPreferences(context)
        pickFile()
        val clockStarts = mutableListOf<Long>()
        every { sound.playFile(any(), any(), any()) } answers {
            clockStarts += dispatcher.scheduler.currentTime - secondArg<Long>()
            true
        }
        val cues = ClockCues(sound, audio, {}, clock, custom)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(
                timer, tournament, BankPreferences(context), cues, clock, audio, FakeChipSets(), NineSeats,
            ) as T
        }
        val viewModel = ViewModelProvider(store, factory)[TimerViewModel::class.java]
        dispatcher.scheduler.runCurrent()
        viewModel.acceptIntent(TimerIntent.ToggleTimer)
        dispatcher.scheduler.runCurrent()
        val driver = LiveClockDriver(SavedClock(timer, tournament)::read, clock, cues, {}, {})
        val run = TestScope(dispatcher).launch { driver.run() }
        dispatcher.scheduler.runCurrent()

        val segments = viewModel.uiState.value.timeline.segments
        val firstChange = segments[0].endSeconds * 1_000L
        val secondChange = segments[1].endSeconds * 1_000L
        dispatcher.scheduler.advanceTimeBy(secondChange + 1_000)
        dispatcher.scheduler.runCurrent()

        // Both looked; the file started once per change, its peak (8 s in) on the change
        assertEquals(listOf(firstChange - PEAK, secondChange - PEAK), clockStarts)
        verify(exactly = 0) { sound.playSound(R.raw.blind_level_up) }
        assertFalse(custom.isMissing())
        run.cancel()
    }

    private companion object {
        const val URI = "content://picked/cue.ogg"
        const val PEAK = 8_000L
        const val DEFAULT_LEAD = 4_000L
    }
}
