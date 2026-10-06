package com.huntercoles.pokerpayout.tournament.live

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import com.huntercoles.pokerpayout.tournament.domain.clock.SilentCue
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

/**
 * The live clock notification's driver (PP-081) on virtual time: the monotonic clock follows the
 * test scheduler, so a 20-minute level takes no real time. It must post a card once and then only
 * when the notification's words change (never once a second), play the cues from the background on
 * time, hold the CPU only while the clock runs, follow Pause and Resume, and end the notification
 * when the game finishes, is reset, or stays paused for half an hour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveClockDriverTest {

    private val dispatcher = StandardTestDispatcher()
    private val sound: SoundManager = mockk(relaxed = true)
    private val buzzes = mutableListOf<SilentCue>()
    private val posts = mutableListOf<LiveClockCard>()
    private val awake = mutableListOf<Long?>()
    private lateinit var cues: ClockCues

    /** The monotonic and wall clocks, on the scheduler's virtual time. */
    private val time = object : TimeSource {
        override fun elapsedRealtimeMillis() = 50_000_000L + dispatcher.scheduler.currentTime
        override fun wallClockMillis() = 1_760_000_000_000L + dispatcher.scheduler.currentTime
        override fun bootCount() = 3
    }

    /** 3 h of 20-minute levels, 50 to 5,000, and a 10-minute break after level 4. */
    private val timeline = ClockTimeline.build(
        BlindStructureCalculator.generateSchedule(BlindStructureInput(10, 180, 50, 5_000, 20)),
        roundLengthMinutes = 20,
        breaks = BreakSettings(everyLevels = 4, lengthMinutes = 10),
        smallestChip = 50,
    )
    private val minute = 60_000L
    private val round = 20 * minute

    /** The saved clock, as the driver reads it; null is a reset. */
    private var saved: SavedClock.State? = null

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audio_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        cues = ClockCues(sound, AudioPreferences(context), { buzzes += it }, time)
    }

    private fun runningFrom(elapsedMillis: Long) {
        saved = SavedClock.State(ClockAnchor.runningFrom(elapsedMillis, time), timeline, finished = false)
    }

    private fun pausedAt(elapsedMillis: Long) {
        saved = SavedClock.State(ClockAnchor.stopped(elapsedMillis), timeline, finished = false)
    }

    private fun elapsed() = saved!!.anchor.elapsedAt(time)

    private fun driver() = LiveClockDriver({ saved }, time, cues, { posts += it }, { awake += it })

    private fun TestScope.start(driver: LiveClockDriver): Job = launch { driver.run() }.also { runCurrent() }

    /** Moves virtual time on by [millis] and runs what is due then. */
    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private val LiveClockCard.level get() = (showing as? LiveClockCard.Showing.Level)?.blinds?.level

    @Test
    fun postsOnceThenOnlyWhenTheLevelChanges() = runTest(dispatcher) {
        runningFrom(0L)
        val run = start(driver())
        assertEquals(1, posts.size)

        advance(59 * minute) // three levels
        assertEquals(listOf(1, 2, 3), posts.map { it.level })
        // Each card counts down to its own level's end; nothing was posted in between
        assertEquals(posts.size, posts.map { it.endsAtRealtime }.distinct().size)
        run.cancel()
    }

    @Test
    fun theBreakAndTheLevelAfterItArePostedToo() = runTest(dispatcher) {
        runningFrom(3 * round + 10 * minute) // level 4, half way
        val run = start(driver())

        advance(10 * minute + 1_000) // into the break
        assertTrue(posts.last().showing is LiveClockCard.Showing.Break)
        advance(10 * minute) // level 5
        assertEquals(5, posts.last().level)
        assertEquals(3, posts.size)
        run.cancel()
    }

    @Test
    fun cuesPlayOnTimeFromTheBackground() = runTest(dispatcher) {
        runningFrom(0L)
        val run = start(driver())

        advance(19 * minute - 1_000)
        assertTrue(buzzes.isEmpty())
        advance(1_100) // one minute left
        assertEquals(listOf(SilentCue.ONE_MINUTE), buzzes)
        verify(exactly = 0) { sound.playSound(any()) }
        advance(56_000) // 4 s left: the chime
        verify(exactly = 1) { sound.playSound(R.raw.blind_level_up) }
        assertEquals(listOf(SilentCue.ONE_MINUTE), buzzes)
        advance(4_000) // the change
        assertEquals(listOf(SilentCue.ONE_MINUTE, SilentCue.LEVEL_CHANGE), buzzes)
        verify(exactly = 1) { sound.playSound(any()) }
        run.cancel()
    }

    @Test
    fun whileRunningTheCpuIsHeldJustPastTheNextLook() = runTest(dispatcher) {
        runningFrom(0L)
        val run = start(driver())

        val held = awake.last()
        assertTrue("held for $held ms", held != null && held > 0 && held <= LiveClockDriver.MAX_WAIT_MILLIS + minute)
        run.cancel()
    }

    @Test
    fun pauseFollowsTheSavedClockAndLetsTheCpuSleep() = runTest(dispatcher) {
        runningFrom(0L)
        val driver = driver()
        val run = start(driver)

        advance(5 * minute)
        pausedAt(elapsed()) // the notification's Pause saved this; the service pokes the driver
        driver.poke()
        runCurrent()

        val card = posts.last()
        assertFalse(card.running)
        assertEquals(15 * 60, card.pausedLeftSeconds)
        assertNull(awake.last())
        // Paused, time passes and nothing more is posted or played
        advance(20 * minute)
        assertEquals(2, posts.size)
        assertTrue(buzzes.isEmpty())
        assertTrue(run.isActive)
        run.cancel()
    }

    @Test
    fun resumeBringsTheCountdownBack() = runTest(dispatcher) {
        runningFrom(0L)
        val driver = driver()
        val run = start(driver)
        advance(5 * minute)
        pausedAt(elapsed())
        driver.poke()
        runCurrent()

        advance(2 * minute)
        runningFrom(5 * minute) // Resume
        driver.poke()
        runCurrent()

        assertTrue(posts.last().running)
        assertEquals(time.elapsedRealtimeMillis() + 15 * minute, posts.last().endsAtRealtime)
        assertTrue(awake.last() != null)
        run.cancel()
    }

    @Test
    fun aClockPausedForHalfAnHourLosesItsNotification() = runTest(dispatcher) {
        pausedAt(5 * minute)
        val run = start(driver())

        advance(LiveClockDriver.PAUSED_KEEP_MILLIS - minute)
        assertTrue(run.isActive)
        advance(minute)
        assertTrue(run.isCompleted)
        assertNull(awake.last())
    }

    @Test
    fun aResetOrAFinishEndsIt() = runTest(dispatcher) {
        runningFrom(0L)
        val first = driver()
        val run = start(first)
        saved = null // New tournament…
        first.poke()
        runCurrent()
        assertTrue(run.isCompleted)

        runningFrom(0L)
        val second = driver()
        val again = start(second)
        saved = saved!!.copy(finished = true) // the clock's screen marked the game finished
        second.poke()
        runCurrent()
        assertTrue(again.isCompleted)
    }

    @Test
    fun theEndOfTheLastLevelEndsIt() = runTest(dispatcher) {
        runningFrom(timeline.endSeconds * 1_000L - minute)
        val run = start(driver())

        advance(minute - 1_000)
        assertTrue(run.isActive)
        advance(2_000)
        assertTrue(run.isCompleted)
    }

    @Test
    fun aJumpPlaysNoCueForWhatItSkipped() = runTest(dispatcher) {
        runningFrom(0L)
        val driver = driver()
        val run = start(driver)

        advance(30_000)
        runningFrom(19 * minute + 30_000) // nudged: the minute warning is behind it now
        driver.poke()
        runCurrent()
        assertTrue(buzzes.isEmpty())
        advance(30_100) // and the change still comes
        assertEquals(listOf(SilentCue.LEVEL_CHANGE), buzzes)
        run.cancel()
    }
}
