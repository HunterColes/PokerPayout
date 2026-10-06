package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblemKind
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.clock.CueVibrator
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModelTest.FakeTimeSource
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

/**
 * The clock's M3 additions on the same virtual time and fake clocks as [TimerViewModelTest]: the
 * plus/minus one minute nudges (D5), "End break now" and the color-up tick (S4), the next break, the
 * projected end and the rebuy state (S2's info list), the bell, and mid-game blind changes behind
 * "Unlock to edit…" (S1 v2). Every scenario checks the v1.3.0 guarantees still hold around it: the
 * anchor is saved on the event, nothing drifts, and a process death restores the same clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimerViewModelControlsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeTimeSource(testDispatcher.scheduler)
    private lateinit var context: Context
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var audioPreferences: AudioPreferences
    private val soundManager: SoundManager = mockk(relaxed = true)
    private var store = ViewModelStore()

    // Defaults: 3 h of 20-minute rounds, 50 to 5,000: nine levels
    private val durationSeconds = 180 * 60
    private val roundSeconds = 20 * 60

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "audio_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        newPreferences()
        tournamentPreferences.setPlayerCount(10)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun newPreferences() {
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        audioPreferences = AudioPreferences(context)
    }

    private fun newViewModel(): TimerViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(
                timerPreferences,
                tournamentPreferences,
                bankPreferences,
                ClockCues(soundManager, audioPreferences, CueVibrator { }, clock),
                clock,
                audioPreferences
            ) as T
        }
        return ViewModelProvider(store, factory)[TimerViewModel::class.java]
            .also { testDispatcher.scheduler.runCurrent() }
    }

    private fun processDeathAndRelaunch(afterMillis: Long): TimerViewModel {
        store.clear()
        store = ViewModelStore()
        clock.sleep(afterMillis)
        newPreferences()
        return newViewModel()
    }

    private fun TimerViewModel.send(vararg intents: TimerIntent) {
        intents.forEach { acceptIntent(it) }
        testDispatcher.scheduler.runCurrent()
    }

    private fun advanceSeconds(seconds: Int) {
        testDispatcher.scheduler.advanceTimeBy(seconds * 1000L)
        testDispatcher.scheduler.runCurrent()
    }

    private val TimerViewModel.state get() = uiState.value

    private val TimerViewModel.levelClock get() = ClockFormat.clock(state.segmentRemainingSeconds)

    private fun TimerViewModel.goToLevel(number: Int) {
        repeat(number - 1) { send(TimerIntent.NextBlindLevel) }
    }


    @Test
    fun `plus and minus one minute move the time left and the clock keeps running from there`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(5 * 60) // 15:00 left in level 1

        viewModel.send(TimerIntent.NudgeMinutes(1))
        assertEquals("16:00", viewModel.levelClock)
        assertTrue(viewModel.state.isRunning)
        advanceSeconds(1)
        assertEquals("15:59", viewModel.levelClock)

        viewModel.send(TimerIntent.NudgeMinutes(-1))
        assertEquals("14:59", viewModel.levelClock)
        advanceSeconds(59)
        assertEquals("14:00", viewModel.levelClock)
        // The whole tournament moves with it: no drift, and the saved anchor agrees after a restart
        assertEquals(6 * 60, viewModel.state.elapsedSeconds)
        val restored = processDeathAndRelaunch(afterMillis = 10_000)
        assertEquals("13:50", restored.levelClock)
    }

    @Test
    fun `nudges stay inside the level, and taking off the last minute still rings the change`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(30) // 19:30 left

        viewModel.send(TimerIntent.NudgeMinutes(1))
        assertEquals("20:00", viewModel.levelClock) // not more than the level's length
        assertEquals(1, viewModel.state.currentBlindLevel?.level)

        advanceSeconds(roundSeconds - 40) // 0:40 left
        verify(exactly = 0) { soundManager.playSound(any()) }
        viewModel.send(TimerIntent.NudgeMinutes(-1))
        assertEquals("0:01", viewModel.levelClock) // a second short of the end, still level 1
        assertEquals(1, viewModel.state.currentBlindLevel?.level)
        verify(exactly = 1) { soundManager.playSound(R.raw.blind_level_up) } // the skipped warning plays once
        advanceSeconds(1)
        assertEquals(2, viewModel.state.currentBlindLevel?.level)
        advanceSeconds(5)
        verify(exactly = 1) { soundManager.playSound(any()) }
    }

    @Test
    fun `nudges do nothing before the start, work while paused and are saved`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.NudgeMinutes(-1))
        assertEquals("20:00", viewModel.levelClock)
        assertFalse(viewModel.state.hasTimerStarted)

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(10 * 60)
        viewModel.send(TimerIntent.ToggleTimer) // paused at 10:00 left
        viewModel.send(TimerIntent.NudgeMinutes(-1))
        assertEquals("9:00", viewModel.levelClock)
        assertFalse(viewModel.state.isRunning)
        advanceSeconds(120)
        assertEquals("9:00", viewModel.levelClock)
        assertEquals(11 * 60_000L, timerPreferences.getClock()?.elapsedMillis)

        val restored = processDeathAndRelaunch(afterMillis = 3_600_000)
        assertEquals("9:00", restored.levelClock)
        assertFalse(restored.state.isRunning)
    }

    @Test
    fun `end break now starts the next level at its full time`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.UpdateBreakEvery(4))
        viewModel.send(TimerIntent.EndBreakNow) // not on a break: nothing happens
        assertFalse(viewModel.state.hasTimerStarted)

        viewModel.goToLevel(5) // levels 1-4, then the break
        assertTrue(viewModel.state.isOnBreak)
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(90)

        viewModel.send(TimerIntent.EndBreakNow)

        assertFalse(viewModel.state.isOnBreak)
        assertEquals(5, viewModel.state.currentBlindLevel?.level)
        assertEquals("20:00", viewModel.levelClock)
        assertTrue(viewModel.state.isRunning)
        advanceSeconds(1)
        assertEquals("19:59", viewModel.levelClock)
        val restored = processDeathAndRelaunch(afterMillis = 0)
        assertEquals(5, restored.state.currentBlindLevel?.level)
    }

    @Test
    fun `color-up done is ticked per break, survives process death and goes with a reset`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.UpdateBreakEvery(4))
        viewModel.send(TimerIntent.MarkColorUpDone()) // not on a break: nothing to tick
        assertTrue(timerPreferences.getColorUpDoneAfterLevels().isEmpty())

        viewModel.goToLevel(5)
        assertEquals(listOf(50), viewModel.state.currentBreak?.colorUp)
        assertFalse(viewModel.state.colorUpDone)

        viewModel.send(TimerIntent.MarkColorUpDone())
        assertTrue(viewModel.state.colorUpDone)
        assertEquals(setOf(4), timerPreferences.getColorUpDoneAfterLevels())

        val restored = processDeathAndRelaunch(afterMillis = 1_000)
        assertTrue(restored.state.colorUpDone)
        restored.send(TimerIntent.MarkColorUpDone(done = false))
        assertFalse(restored.state.colorUpDone)
        restored.send(TimerIntent.MarkColorUpDone())

        restored.send(TimerIntent.NextBlindLevel) // the next level: not a break
        assertFalse(restored.state.colorUpDone)

        restored.send(TimerIntent.ResetTimer)
        assertTrue(timerPreferences.getColorUpDoneAfterLevels().isEmpty())
        assertTrue(restored.state.colorUpDoneAfterLevels.isEmpty())
    }

    @Test
    fun `the next break counts down with the clock`() {
        val viewModel = newViewModel()
        assertNull(viewModel.state.nextBreakInSeconds) // breaks are off

        viewModel.send(TimerIntent.UpdateBreakEvery(4))
        assertEquals(4 * roundSeconds, viewModel.state.nextBreakInSeconds)
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(100)
        assertEquals(4 * roundSeconds - 100, viewModel.state.nextBreakInSeconds)

        viewModel.goToLevel(5) // on break 1: the next one is after level 8
        assertEquals(1, viewModel.state.currentBreak?.number)
        val second = viewModel.state.timeline.segments.filterIsInstance<BreakSegment>()[1]
        assertEquals(second.startSeconds - viewModel.state.elapsedSeconds, viewModel.state.nextBreakInSeconds)
    }

    @Test
    fun `the projected end holds while running and slides with the wall clock while paused`() {
        val viewModel = newViewModel()
        val startWall = clock.wallClockMillis()
        assertEquals(startWall + durationSeconds * 1000L, viewModel.state.endsAtWallClock)

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(600)
        val runningEnd = startWall + durationSeconds * 1000L
        assertEquals(runningEnd, viewModel.state.endsAtWallClock)
        advanceSeconds(37)
        assertEquals(runningEnd, viewModel.state.endsAtWallClock)

        viewModel.send(TimerIntent.ToggleTimer) // paused: every minute of pause pushes the end back
        advanceSeconds(5 * 60)
        val pausedEnd = viewModel.state.endsAtWallClock ?: error("no end time while paused")
        assertTrue(pausedEnd in runningEnd + 4 * 60_000L..runningEnd + 5 * 60_000L)

        viewModel.goToLevel(10) // past the scheduled end: overtime has no end time
        assertTrue(viewModel.state.isOvertime)
        assertNull(viewModel.state.endsAtWallClock)
    }

    @Test
    fun `rebuys are open until the cutoff level and closed after it`() {
        tournamentPreferences.setRebuyAmount(10.0)
        bankPreferences.savePlayerRebuys(2, 1)
        val viewModel = newViewModel()
        assertEquals(RebuyState.Open(untilLevel = null, taken = 1), viewModel.state.rebuyState)

        viewModel.send(TimerIntent.UpdateRebuyUntil(2), TimerIntent.UpdateBreakEvery(2))
        assertEquals(2, tournamentPreferences.getRebuyUntilLevel())
        assertEquals(RebuyState.Open(untilLevel = 2, taken = 1), viewModel.state.rebuyState)

        viewModel.send(TimerIntent.ToggleTimer)
        viewModel.send(TimerIntent.NextBlindLevel) // level 2: still open
        assertEquals(RebuyState.Open(untilLevel = 2, taken = 1), viewModel.state.rebuyState)
        viewModel.send(TimerIntent.NextBlindLevel) // the break after level 2: closed
        assertTrue(viewModel.state.isOnBreak)
        assertEquals(RebuyState.Closed(afterLevel = 2, taken = 1), viewModel.state.rebuyState)
        viewModel.send(TimerIntent.NextBlindLevel)
        assertEquals(RebuyState.Closed(afterLevel = 2, taken = 1), viewModel.state.rebuyState)

        tournamentPreferences.setRebuyAmount(0.0)
        testDispatcher.scheduler.runCurrent()
        assertEquals(RebuyState.Off(taken = 1), viewModel.state.rebuyState)
    }

    @Test
    fun `the bell mutes and unmutes the chimes`() {
        val viewModel = newViewModel()
        assertFalse(viewModel.state.isMuted)

        viewModel.send(TimerIntent.ToggleMute)
        assertTrue(viewModel.state.isMuted)
        assertTrue(audioPreferences.getIsMuted())

        viewModel.send(TimerIntent.ToggleMute)
        assertFalse(viewModel.state.isMuted)
    }

    @Test
    fun `an unlocked blind change mid-game keeps the level and the time left`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(2 * roundSeconds + 8 * 60) // level 3, 12:00 left

        viewModel.send(TimerIntent.KeepingLevel(TimerIntent.UpdateRoundLength(15)))

        assertNull(viewModel.state.midGameProblem)
        assertEquals(15, viewModel.state.config.roundLengthMinutes)
        assertEquals(12, viewModel.state.baseBlindLevels.size)
        assertEquals(3, viewModel.state.currentBlindLevel?.level)
        assertEquals("12:00", viewModel.levelClock)
        assertTrue(viewModel.state.isRunning)
        assertTrue(viewModel.state.hasTimerStarted)
        assertEquals(15, timerPreferences.getRoundLengthAtStart())
        assertEquals(15, tournamentPreferences.getRoundLengthMinutes())
        advanceSeconds(1)
        assertEquals("11:59", viewModel.levelClock)

        // A restart finds the same clock
        val restored = processDeathAndRelaunch(afterMillis = 1_000)
        assertEquals(3, restored.state.currentBlindLevel?.level)
        assertEquals("11:58", restored.levelClock)
    }

    @Test
    fun `an unlocked blind change that can't be played isn't applied and offers the fixes`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(roundSeconds + 60) // level 2, 19:00 left

        viewModel.send(TimerIntent.KeepingLevel(TimerIntent.UpdateRoundLength(25)))

        val problem = viewModel.state.midGameProblem
        assertEquals(BlindSetupProblemKind.UNEVEN_ROUNDS, problem?.kind)
        assertEquals(20, viewModel.state.config.roundLengthMinutes) // the clock runs on as it was
        assertEquals("19:00", viewModel.levelClock)
        assertTrue(viewModel.state.isRunning)
        assertEquals(20, tournamentPreferences.getRoundLengthMinutes())

        viewModel.send(TimerIntent.KeepingLevel(TimerIntent.ApplyFix(problem!!.fixes.first())))
        assertNull(viewModel.state.midGameProblem)
        assertEquals(2, viewModel.state.currentBlindLevel?.level)
        assertEquals("19:00", viewModel.levelClock)
    }

    @Test
    fun `an unlocked change before the start is an ordinary setup change`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.KeepingLevel(TimerIntent.UpdateStartingChips(10_000)))

        assertFalse(viewModel.state.hasTimerStarted)
        assertEquals(10_000, viewModel.state.baseBlindLevels.last().smallBlind)
        assertEquals(10_000, tournamentPreferences.getStartingChips())
    }
}
