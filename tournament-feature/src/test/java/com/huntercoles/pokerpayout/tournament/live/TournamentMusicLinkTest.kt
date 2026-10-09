package com.huntercoles.pokerpayout.tournament.live

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.audio.music.BreakMusic
import com.huntercoles.pokerpayout.core.audio.music.ClockPhase
import com.huntercoles.pokerpayout.core.audio.music.MusicControls
import com.huntercoles.pokerpayout.core.preferences.MusicPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockPhases
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import org.robolectric.Shadows.shadowOf

/**
 * The music and the clock ("Play with the clock"), on virtual time: the phase the saved clock is in,
 * the music starting and pausing with it and doing what the host chose on breaks, the change of
 * level or break caught on time without anything saved, and the music pausing when the app leaves
 * the screen with nothing to keep it playing in the background.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TournamentMusicLinkTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var musicPreferences: MusicPreferences
    private lateinit var timerPreferences: TimerPreferences
    private val music = FakeMusic()

    /** The monotonic and wall clocks, on the scheduler's virtual time. */
    private val time = object : TimeSource {
        override fun elapsedRealtimeMillis() = 50_000_000L + dispatcher.scheduler.currentTime
        override fun wallClockMillis() = 1_760_000_000_000L + dispatcher.scheduler.currentTime
        override fun bootCount() = 3
    }

    /** 3 h of 20-minute levels and a 10-minute break after level 4: the break runs from 80 to 90 minutes. */
    private val timeline = ClockTimeline.build(
        BlindStructureCalculator.generateSchedule(BlindStructureInput(10, 180, 50, 5_000, 20)),
        roundLengthMinutes = 20,
        breaks = BreakSettings(everyLevels = 4, lengthMinutes = 10),
        smallestChip = 50,
    )
    private val minute = 60_000L
    private val breakStart = 80 * minute
    private val breakEnd = 90 * minute

    private var saved: SavedClock.State? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(MusicPreferences.FILE, "timer_prefs", "tournament_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        musicPreferences = MusicPreferences(context)
        timerPreferences = TimerPreferences(context)
    }

    private fun TestScope.link() = TournamentMusicLink(
        SavedClock(timerPreferences, TournamentPreferences(context)),
        timerPreferences,
        musicPreferences,
        music,
        time,
        context,
        backgroundScope,
    ).also { it.readClock = { saved } }

    private fun runningFrom(elapsedMillis: Long) {
        saved = SavedClock.State(ClockAnchor.runningFrom(elapsedMillis, time), timeline, finished = false)
    }

    private fun pausedAt(elapsedMillis: Long) {
        saved = SavedClock.State(ClockAnchor.stopped(elapsedMillis), timeline, finished = false)
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    // ------------------------------------------------------------------ the phase

    @Test
    fun thePhaseOfTheSavedClock() {
        assertEquals(ClockPhase.IDLE, ClockPhases.of(null, 0L))
        assertEquals(ClockPhase.IDLE, ClockPhases.of(SavedClock.State(ClockAnchor(), ClockTimeline.EMPTY, false), 0L))
        val running = SavedClock.State(ClockAnchor.runningFrom(0L, time), timeline, finished = false)
        assertEquals(ClockPhase.LEVEL, ClockPhases.of(running, 0L))
        assertEquals(ClockPhase.LEVEL, ClockPhases.of(running, breakStart - 1))
        assertEquals(ClockPhase.BREAK, ClockPhases.of(running, breakStart))
        assertEquals(ClockPhase.LEVEL, ClockPhases.of(running, breakEnd))
        assertEquals(ClockPhase.FINISHED, ClockPhases.of(running, timeline.endSeconds * 1_000L))
        val paused = SavedClock.State(ClockAnchor.stopped(breakStart + minute), timeline, finished = false)
        assertEquals(ClockPhase.PAUSED, ClockPhases.of(paused, breakStart + minute))
        assertEquals(ClockPhase.FINISHED, ClockPhases.of(paused.copy(finished = true), breakStart + minute))
    }

    @Test
    fun theNextChangeIsTheEndOfTheSegmentRunning() {
        val running = SavedClock.State(ClockAnchor.runningFrom(0L, time), timeline, finished = false)
        assertEquals(20 * minute, ClockPhases.nextChange(running, 0L))
        assertEquals(breakStart, ClockPhases.nextChange(running, 79 * minute))
        assertEquals(breakEnd, ClockPhases.nextChange(running, breakStart))
        assertNull(ClockPhases.nextChange(running.copy(anchor = ClockAnchor.stopped(0L)), 0L))
        assertNull(ClockPhases.nextChange(running, timeline.endSeconds * 1_000L))
    }

    // ------------------------------------------------------------------ playing with the clock

    @Test
    fun theMusicStartsAndPausesWithTheClock() = runTest(dispatcher) {
        musicPreferences.setAutoPlay(true)
        val link = link()
        link.look() // not started
        assertEquals(emptyList<String>(), music.calls)

        runningFrom(0L)
        link.look()
        pausedAt(5 * minute)
        link.look()
        runningFrom(5 * minute)
        link.look()
        assertEquals(listOf("play", "pause", "play"), music.calls)
    }

    @Test
    fun withTheLinkOffTheClockLeavesTheMusicAlone() = runTest(dispatcher) {
        val link = link()
        runningFrom(0L)
        link.look()
        pausedAt(minute)
        link.look()
        assertEquals(emptyList<String>(), music.calls)
    }

    @Test
    fun theBreakIsCaughtOnTimeAndPausesTheMusicWhenChosen() = runTest(dispatcher) {
        musicPreferences.setAutoPlay(true)
        musicPreferences.setBreakMusic(BreakMusic.PAUSE)
        runningFrom(breakStart - minute)
        link().look()
        assertEquals(listOf("play"), music.calls)

        advance(minute - 1_000)
        assertEquals(listOf("play"), music.calls)
        advance(1_100) // the break starts: nothing was saved, the link looks by itself (just after)
        assertEquals(listOf("play", "pause"), music.calls)
        advance(10 * minute) // and ends
        assertEquals(listOf("play", "pause", "play"), music.calls)
    }

    @Test
    fun aQuietBreakIsQuieterUntilTheNextLevel() = runTest(dispatcher) {
        musicPreferences.setAutoPlay(true)
        musicPreferences.setBreakMusic(BreakMusic.QUIET)
        runningFrom(breakStart - 1_000)
        link().look()
        assertFalse(music.quietNow)

        advance(1_100)
        assertTrue(music.quietNow)
        assertEquals(listOf("play"), music.calls)
        advance(10 * minute)
        assertFalse(music.quietNow)
    }

    @Test
    fun turningTheLinkOnWhileTheClockRunsStartsTheMusic() = runTest(dispatcher) {
        runningFrom(3 * minute)
        val link = link()
        link.start()
        runCurrent()
        assertEquals(emptyList<String>(), music.calls)

        musicPreferences.setAutoPlay(true)
        runCurrent()
        assertEquals(listOf("play"), music.calls)
    }

    // ------------------------------------------------------------------ in the background

    @Test
    fun leavingTheAppWithTheClockStoppedPausesTheMusicUntilItsBack() = runTest(dispatcher) {
        val link = link()
        link.onAppVisible()
        music.isPlaying = true
        link.onAppHidden()
        assertEquals(listOf("pause"), music.calls)

        link.onAppVisible()
        assertEquals(listOf("pause", "play"), music.calls)
    }

    @Test
    fun leavingTheAppWithTheClockRunningLetsTheMusicPlayOn() = runTest(dispatcher) {
        timerPreferences.setHasTimerStarted(true)
        timerPreferences.setTimerRunning(true)
        val link = link()
        music.isPlaying = true
        link.onAppHidden()
        assertEquals(emptyList<String>(), music.calls)

        // The live clock's service stops (the game is over): now nothing keeps the music playing
        link.liveClockGone()
        assertEquals(listOf("pause"), music.calls)
    }

    @Test
    fun theScreenGoingOffLetsTheMusicPlayOn() = runTest(dispatcher) {
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(false)
        val link = link()
        music.isPlaying = true
        link.onAppHidden()
        link.liveClockGone()
        assertEquals(emptyList<String>(), music.calls)
    }

    @Test
    fun musicNotPlayingIsLeftAloneWhenTheAppComesBack() = runTest(dispatcher) {
        val link = link()
        link.onAppHidden()
        link.onAppVisible()
        assertEquals(emptyList<String>(), music.calls)
    }

    /** Writes down what the clock asks of the music. */
    private class FakeMusic : MusicControls {
        val calls = mutableListOf<String>()
        var quietNow = false
        override var isPlaying = false

        override fun play() {
            calls += "play"
            isPlaying = true
        }

        override fun pause() {
            calls += "pause"
            isPlaying = false
        }

        override fun setQuiet(quiet: Boolean) {
            quietNow = quiet
        }
    }
}
