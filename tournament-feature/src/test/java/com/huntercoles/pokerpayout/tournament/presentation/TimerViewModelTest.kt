package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.core.utils.BlindSetupProblemKind
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The tournament clock on virtual time with a fake [TimeSource] (PP-015).
 *
 * The coroutine scheduler's virtual time drives the tick loop, and the fake monotonic clock follows it
 * unless a test moves the clock on its own: a "sleep" moves the monotonic and wall clocks while no tick
 * runs (deep sleep stalls `delay`), a "process death" clears the ViewModel and builds a new one from the
 * saved preferences, and a "reboot" restarts the monotonic clock. No test sleeps or reads real time.
 *
 * Only SoundManager is mocked; the preferences are real (see the note in the batch-A version of this
 * file on why MockK can't stub them).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeTimeSource(testDispatcher.scheduler)
    private lateinit var context: Context
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private val soundManager: SoundManager = mockk(relaxed = true)
    private var store = ViewModelStore()

    // Defaults: 3 h, 20-minute rounds, smallest chip 50, starting stack 5000:
    // 50, 100, 150, 300, 500, 800, 1500, 3000, 5000, then overtime 10000, 20000, 40000
    private val durationSeconds = 180 * 60
    private val roundSeconds = 20 * 60

    /** Monotonic and wall clocks that follow virtual time, plus whatever a test adds. */
    class FakeTimeSource(private val scheduler: TestCoroutineScheduler) : TimeSource {
        private var realtimeOffset = 1_000_000L
        private var wallOffset = 1_760_000_000_000L
        var boot = 7

        /** How much real time passes per millisecond of virtual (tick) time; > 1 means late ticks. */
        var rate = 1.0

        override fun elapsedRealtimeMillis() = realtimeOffset + (scheduler.currentTime * rate).toLong()
        override fun wallClockMillis() = wallOffset + (scheduler.currentTime * rate).toLong()
        override fun bootCount() = boot

        /** The device sleeps: real time passes, no coroutine runs. */
        fun sleep(millis: Long) {
            realtimeOffset += millis
            wallOffset += millis
        }

        /** The device reboots after [downMillis]: the monotonic clock restarts near zero. */
        fun reboot(downMillis: Long) {
            wallOffset += downMillis
            realtimeOffset = -(scheduler.currentTime * rate).toLong() + 5_000
            boot++
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        newPreferences()
        tournamentPreferences.setPlayerCount(10)
    }

    @After
    fun tearDown() {
        store.clear() // cancels a running clock
        Dispatchers.resetMain()
    }

    private fun newPreferences() {
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
    }

    private fun newViewModel(): TimerViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimerViewModel(timerPreferences, tournamentPreferences, bankPreferences, soundManager, clock) as T
        }
        // runCurrent, not advanceUntilIdle: a restored running clock ticks forever
        return ViewModelProvider(store, factory)[TimerViewModel::class.java]
            .also { testDispatcher.scheduler.runCurrent() }
    }

    /** The process dies (ViewModel and singletons gone) and the app is opened again [afterMillis] later. */
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

    /** Jump to the start of level [number] (1-based) of the default schedule, paused. */
    private fun TimerViewModel.goToLevel(number: Int) {
        repeat(number - 1) { send(TimerIntent.NextBlindLevel) }
    }

    // ------------------------------------------------------------------ schedule (batch A, adapted)

    @Test
    fun `default configuration produces a valid nine level schedule`() {
        val viewModel = newViewModel()

        val levels = viewModel.state.baseBlindLevels
        assertEquals(9, levels.size)
        assertEquals(listOf(50, 100, 150, 300, 500, 800, 1_500, 3_000, 5_000), levels.map { it.smallBlind })
        assertEquals((0..8).map { it * 20 }, levels.map { it.roundStartMinute })
        assertEquals((1..9).toList(), levels.map { it.level })
        assertTrue(levels.all { it.bigBlind == 2 * it.smallBlind })
        assertEquals(levels, viewModel.state.blindLevels)
        // The last regular level ends exactly when the tournament does
        assertEquals(durationSeconds, viewModel.state.timeline.regularEndSeconds)
        assertTrue(viewModel.isValidBlindConfiguration(viewModel.state))
        assertNull(viewModel.state.setupProblem)
    }

    @Test
    fun `before the start the clock shows level 1 and its full time`() {
        val viewModel = newViewModel()

        assertFalse(viewModel.state.hasTimerStarted)
        assertEquals("20:00", viewModel.levelClock)
        assertEquals(50, viewModel.state.currentBlindLevel?.smallBlind)
        assertEquals(100, viewModel.state.nextBlindLevel?.smallBlind)
        assertEquals(durationSeconds, viewModel.state.tournamentRemainingSeconds)
    }

    @Test
    fun `running clock counts the level down once per second, locks the tournament, and pauses`() {
        val viewModel = newViewModel()

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(5)

        assertTrue(viewModel.state.isRunning)
        assertEquals(5, viewModel.state.elapsedSeconds)
        assertEquals("19:55", viewModel.levelClock)
        assertEquals("2:59:55", ClockFormat.long(viewModel.state.tournamentRemainingSeconds))
        assertTrue(timerPreferences.getTimerRunning())
        assertTrue(tournamentPreferences.getTournamentLocked())

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(5)

        assertFalse(viewModel.state.isRunning)
        assertEquals(5, viewModel.state.elapsedSeconds)
        assertEquals(5_000L, timerPreferences.getClock()?.elapsedMillis)
        assertFalse(timerPreferences.getTimerRunning())
        assertFalse(tournamentPreferences.getTournamentLocked())
    }

    @Test
    fun `blind level advances when the clock crosses a round boundary`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(roundSeconds - 1)
        assertEquals(0, viewModel.state.currentBlindLevelIndex)
        assertEquals("0:01", viewModel.levelClock)

        advanceSeconds(1)
        assertEquals(1, viewModel.state.currentBlindLevelIndex)
        assertEquals(100, viewModel.state.currentBlindLevel?.smallBlind)
        assertEquals("20:00", viewModel.levelClock)
        assertEquals(150, viewModel.state.nextBlindLevel?.smallBlind)
    }

    @Test
    fun `level change sound plays four seconds before the next level`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(roundSeconds - 5) // 5 s to go
        verify(exactly = 0) { soundManager.playSound(any()) }

        advanceSeconds(1) // 4 s to go
        verify(exactly = 1) { soundManager.playSound(R.raw.blind_level_up) }

        advanceSeconds(10) // past the change: no replay
        verify(exactly = 1) { soundManager.playSound(any()) }
    }

    @Test
    fun `the clock rolls into overtime when the schedule runs out`() {
        val viewModel = newViewModel()
        viewModel.goToLevel(9)
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(roundSeconds - 1)
        assertFalse(viewModel.state.isOvertime)
        advanceSeconds(1)

        assertTrue(viewModel.state.isOvertime)
        assertEquals(1, viewModel.state.overtimeLevelsRevealed)
        assertEquals(10, viewModel.state.currentBlindLevel?.level)
        assertEquals(10_000, viewModel.state.currentBlindLevel?.smallBlind)
        assertEquals(0, viewModel.state.tournamentRemainingSeconds)
        assertEquals("20:00", viewModel.levelClock)
        assertTrue(viewModel.state.isRunning)
    }

    @Test
    fun `next level past the schedule reveals a doubled overtime level`() {
        val viewModel = newViewModel()
        viewModel.goToLevel(9)
        assertEquals(8, viewModel.state.currentBlindLevelIndex)
        assertEquals(roundSeconds, viewModel.state.tournamentRemainingSeconds)

        viewModel.send(TimerIntent.NextBlindLevel)

        val overtime = viewModel.state.blindLevels.last()
        assertEquals(10, viewModel.state.blindLevels.size)
        assertEquals(9, viewModel.state.currentBlindLevelIndex)
        assertEquals(1, viewModel.state.overtimeLevelsRevealed)
        assertEquals(10_000, overtime.smallBlind)
        assertEquals(180, overtime.roundStartMinute)
        assertTrue(viewModel.state.isOvertime)
        // The jump is saved, so it survives the process
        assertEquals(durationSeconds * 1000L, timerPreferences.getClock()?.elapsedMillis)

        // Going back drops the overtime level again
        viewModel.send(TimerIntent.PreviousBlindLevel)
        assertEquals(9, viewModel.state.blindLevels.size)
        assertEquals(0, viewModel.state.overtimeLevelsRevealed)
    }

    @Test
    fun `an impossible blind setup refuses to start and says why`() {
        val viewModel = newViewModel()
        // Starting stack below the smallest chip: no schedule exists
        viewModel.send(TimerIntent.UpdateSmallestChip(100), TimerIntent.UpdateStartingChips(50))
        assertTrue(viewModel.state.baseBlindLevels.isEmpty())
        assertEquals(BlindSetupProblemKind.STACK_TOO_SMALL, viewModel.state.setupProblem?.kind)

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(3)

        assertTrue(viewModel.state.showInvalidConfigDialog)
        assertFalse(viewModel.state.isRunning)
        assertEquals(0, viewModel.state.elapsedSeconds)
        assertFalse(timerPreferences.getTimerRunning())

        viewModel.send(TimerIntent.HideInvalidConfigDialog)
        assertFalse(viewModel.state.showInvalidConfigDialog)
    }

    @Test
    fun `validation rejects schedules that don't reach the stack, aren't increasing, or end early`() {
        val viewModel = newViewModel()
        val valid = viewModel.state
        val levels = valid.baseBlindLevels
        assertTrue(viewModel.isValidBlindConfiguration(valid))

        assertFalse("empty", viewModel.isValidBlindConfiguration(valid.copy(baseBlindLevels = emptyList())))
        assertFalse(
            "last level isn't the starting stack",
            viewModel.isValidBlindConfiguration(valid.copy(baseBlindLevels = levels.dropLast(1)))
        )
        val flatStep = levels.toMutableList().apply { this[3] = this[3].copy(smallBlind = this[2].smallBlind) }
        assertFalse(
            "blinds must strictly increase",
            viewModel.isValidBlindConfiguration(valid.copy(baseBlindLevels = flatStep))
        )
        val backwardsTime = levels.toMutableList().apply {
            this[3] = this[3].copy(roundStartMinute = this[2].roundStartMinute)
        }
        assertFalse(
            "start times must increase",
            viewModel.isValidBlindConfiguration(valid.copy(baseBlindLevels = backwardsTime))
        )
        assertFalse(
            "levels end an hour before a 4 h tournament",
            viewModel.isValidBlindConfiguration(valid.copy(config = valid.config.copy(gameDurationMinutes = 240)))
        )
    }

    // ------------------------------------------------------------------ PP-015: a clock you can trust

    @Test
    fun `late ticks don't make the clock drift`() {
        // Every tick wakes 5% late (a busy main thread). Counting ticks would show 10:00 after 600 of
        // them; the clock must show the real 10:30.
        clock.rate = 1.05
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(600)

        // The next look may still be a few ms of virtual time away, so allow the second it's on
        val shown = viewModel.state.elapsedSeconds
        assertTrue("shows $shown s after 630 s of real time", shown in 629..630)
    }

    @Test
    fun `a sleep gap is caught up on the next tick`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(10)

        clock.sleep(10 * 60_000L) // screen off, CPU asleep: no tick runs for 10 minutes
        assertEquals(10, viewModel.state.elapsedSeconds)
        advanceSeconds(1)

        assertEquals(611, viewModel.state.elapsedSeconds)
        assertEquals(0, viewModel.state.currentBlindLevelIndex)
    }

    @Test
    fun `sleeping through a level change lands on the right level without a stale chime`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(60)

        clock.sleep(30 * 60_000L)
        advanceSeconds(1)

        assertEquals(31 * 60 + 1, viewModel.state.elapsedSeconds)
        assertEquals(1, viewModel.state.currentBlindLevelIndex)
        assertEquals("8:59", viewModel.levelClock)
        verify(exactly = 0) { soundManager.playSound(any()) }
    }

    @Test
    fun `pausing stops the clock however much time passes`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(100)
        viewModel.send(TimerIntent.ToggleTimer)

        advanceSeconds(3_600)
        clock.sleep(3_600_000)
        advanceSeconds(1)
        assertEquals(100, viewModel.state.elapsedSeconds)

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(50)
        assertEquals(150, viewModel.state.elapsedSeconds)
    }

    @Test
    fun `a running clock survives process death mid-level`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(300)
        assertEquals(300, viewModel.state.elapsedSeconds)

        val restored = processDeathAndRelaunch(afterMillis = 600_000)

        assertTrue(restored.state.isRunning)
        assertTrue(restored.state.hasTimerStarted)
        assertEquals(900, restored.state.elapsedSeconds)
        assertEquals("5:00", restored.levelClock)
        advanceSeconds(1)
        assertEquals(901, restored.state.elapsedSeconds)
    }

    @Test
    fun `a clock killed near the end restores into overtime (B19)`() {
        val viewModel = newViewModel()
        viewModel.goToLevel(9)
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(15 * 60) // 5 minutes before the scheduled end

        // Kill the app 5 minutes before the end, reopen 20 minutes later
        val restored = processDeathAndRelaunch(afterMillis = 20 * 60_000L)

        assertTrue(restored.state.isOvertime)
        assertEquals(-15 * 60, restored.state.tournamentRemainingSeconds) // shown as Overtime +0:15:00
        assertEquals(1, restored.state.overtimeLevelsRevealed)
        assertEquals(10_000, restored.state.currentBlindLevel?.smallBlind)
        assertEquals("5:00", restored.levelClock)
        assertTrue(restored.state.isRunning)
    }

    @Test
    fun `a clock killed past the last overtime level restores finished and silent`() {
        val viewModel = newViewModel()
        viewModel.goToLevel(12)
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(60)

        val restored = processDeathAndRelaunch(afterMillis = 60 * 60_000L)

        assertTrue(restored.state.isFinished)
        assertFalse(restored.state.isRunning)
        assertEquals(restored.state.timeline.endSeconds, restored.state.elapsedSeconds)
        assertTrue(timerPreferences.getIsFinished())
        assertFalse(timerPreferences.getTimerRunning())
        verify(exactly = 0) { soundManager.playSound(any()) }
    }

    @Test
    fun `a reboot falls back to wall time`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(120)

        store.clear()
        store = ViewModelStore()
        clock.reboot(downMillis = 5 * 60_000L) // monotonic clock restarts; 5 minutes pass on the wall
        newPreferences()
        val restored = newViewModel()

        assertEquals(120 + 5 * 60, restored.state.elapsedSeconds)
        assertTrue(restored.state.isRunning)
    }

    @Test
    fun `a v1_1 clock is migrated, overtime included`() {
        // v1.1.x saved "5 minutes left, counting down, running" with a wall-clock timestamp
        val legacy = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        legacy.edit()
            .putInt("game_duration_minutes", 180)
            .putInt("current_time_seconds", 5 * 60)
            .putString("timer_direction", "COUNTDOWN")
            .putBoolean("timer_running", true)
            .putBoolean("has_timer_started", true)
            .putLong("last_update_time", clock.wallClockMillis())
            .commit()
        clock.sleep(20 * 60_000L) // the update was installed 20 minutes later
        newPreferences()

        val viewModel = newViewModel()

        assertEquals(durationSeconds + 15 * 60, viewModel.state.elapsedSeconds)
        assertTrue(viewModel.state.isOvertime)
        assertTrue(viewModel.state.isRunning)
        assertNotNull(timerPreferences.getClock())
        assertFalse(legacy.contains("current_time_seconds"))
    }

    @Test
    fun `the end chime plays when the final level is resumed after a pause (B17)`() {
        val viewModel = newViewModel()
        viewModel.goToLevel(12) // the last overtime level
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(roundSeconds - 10)
        viewModel.send(TimerIntent.ToggleTimer) // pause 10 s before the end
        viewModel.send(TimerIntent.ToggleTimer) // and resume
        verify(exactly = 0) { soundManager.playSound(any()) }

        advanceSeconds(6) // 4 s to go
        verify(exactly = 1) { soundManager.playSound(R.raw.blind_level_up) }

        advanceSeconds(4)
        assertTrue(viewModel.state.isFinished)
        assertFalse(viewModel.state.isRunning)
        assertTrue(timerPreferences.getIsFinished())

        // A finished clock doesn't restart on tap
        viewModel.send(TimerIntent.ToggleTimer)
        assertFalse(viewModel.state.isRunning)
        verify(exactly = 1) { soundManager.playSound(any()) }
    }

    @Test
    fun `the clock writes preferences on events, not on every tick`() {
        val viewModel = newViewModel()
        val prefs = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
        var writes = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> writes++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        viewModel.send(TimerIntent.ToggleTimer)
        val writesToStart = writes

        advanceSeconds(30 * 60) // half an hour, across a level change

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        assertTrue("starting writes the anchor", writesToStart > 0)
        assertEquals("no writes while the clock runs", writesToStart, writes)
    }

    // ------------------------------------------------------------------ PP-026: breaks and antes

    @Test
    fun `breaks appear in the schedule and on the clock, with a chime at start and end`() {
        val viewModel = newViewModel()
        viewModel.send(
            TimerIntent.UpdateBreakEvery(4),
            TimerIntent.UpdateBreakLength(10),
            TimerIntent.UpdateBreakMessage("Last rebuy")
        )
        assertEquals(2, viewModel.state.timeline.segments.count { it is BreakSegment })

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(4 * roundSeconds)

        assertTrue(viewModel.state.isOnBreak)
        assertEquals("Last rebuy", viewModel.state.currentBreak?.message)
        assertEquals(listOf(50), viewModel.state.currentBreak?.colorUp) // level 4 on doesn't need the 50s
        assertEquals("10:00", viewModel.levelClock)
        assertEquals(5, viewModel.state.nextBlindLevel?.level)
        assertNull(viewModel.state.currentBlindLevel)
        verify(exactly = 4) { soundManager.playSound(any()) } // 3 level changes + break start

        advanceSeconds(10 * 60)
        assertFalse(viewModel.state.isOnBreak)
        assertEquals(5, viewModel.state.currentBlindLevel?.level)
        verify(exactly = 5) { soundManager.playSound(any()) } // + break end

        // The break and the overtime both push the scheduled end back
        assertEquals(durationSeconds + 2 * 10 * 60, viewModel.state.timeline.regularEndSeconds)
    }

    @Test
    fun `changing breaks keeps the clock on the same level and second`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(2 * roundSeconds + 100) // 100 s into level 3

        viewModel.send(TimerIntent.UpdateBreakEvery(2))

        assertEquals(3, viewModel.state.currentBlindLevel?.level)
        assertEquals(roundSeconds - 100, viewModel.state.segmentRemainingSeconds)
        assertEquals(2 * roundSeconds + 10 * 60 + 100, viewModel.state.elapsedSeconds)
        assertTrue(viewModel.state.isRunning)
        advanceSeconds(1)
        assertEquals(roundSeconds - 101, viewModel.state.segmentRemainingSeconds)
    }

    @Test
    fun `a big-blind ante applies from its level without resetting the clock`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(roundSeconds + 30)

        viewModel.send(TimerIntent.UpdateBigBlindAnte(3))

        val levels = viewModel.state.baseBlindLevels
        assertEquals(listOf(0, 0), levels.take(2).map { it.ante })
        assertTrue(levels.drop(2).all { it.ante == it.bigBlind })
        assertEquals(roundSeconds + 30, viewModel.state.elapsedSeconds)
        assertTrue(viewModel.state.isRunning)
        assertEquals(3, timerPreferences.getBigBlindAnteFromLevel())
    }

    // ------------------------------------------------------------------ PP-020 and PP-051

    @Test
    fun `a suggested fix makes an invalid setup playable`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.UpdateRoundLength(25)) // 3 h doesn't divide into 25-minute rounds
        val problem = viewModel.state.setupProblem
        assertEquals(BlindSetupProblemKind.UNEVEN_ROUNDS, problem?.kind)
        viewModel.send(TimerIntent.ToggleTimer)
        assertTrue(viewModel.state.showInvalidConfigDialog)

        viewModel.send(TimerIntent.ApplyFix(problem!!.fixes.first()))

        assertEquals(BlindSetupFix.UseRoundLength(20, 9), problem.fixes.first())
        assertNull(viewModel.state.setupProblem)
        assertFalse(viewModel.state.showInvalidConfigDialog)
        assertEquals(20, tournamentPreferences.getRoundLengthMinutes())
        viewModel.send(TimerIntent.ToggleTimer)
        assertTrue(viewModel.state.isRunning)
    }

    @Test
    fun `changing the levels while paused starts a fresh clock`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(90)
        viewModel.send(TimerIntent.ToggleTimer)

        viewModel.send(TimerIntent.UpdateStartingChips(10_000))

        assertFalse(viewModel.state.hasTimerStarted)
        assertEquals(0, viewModel.state.elapsedSeconds)
        assertEquals(10_000, viewModel.state.baseBlindLevels.last().smallBlind)
        assertEquals(10_000, tournamentPreferences.getStartingChips())
        assertEquals(0L, timerPreferences.getClock()?.elapsedMillis)
    }

    @Test
    fun `a stored free-entry smallest chip is migrated to a real chip`() {
        tournamentPreferences.setSmallestChip(15)

        val viewModel = newViewModel()

        assertEquals(5, tournamentPreferences.getSmallestChip())
        assertEquals(5, viewModel.state.config.smallestChip)
    }

    // ------------------------------------------------------------------ PP-025: the table numbers

    @Test
    fun `players left, average stack and prize pool come from the tournament and bank`() {
        tournamentPreferences.setBuyIn(20.0)
        tournamentPreferences.setRebuyAmount(10.0)
        val viewModel = newViewModel()
        assertEquals(
            TableStats(playerCount = 10, playersLeft = 10, averageStack = 5_000, prizePool = 200.0),
            viewModel.state.table
        )

        bankPreferences.saveEliminationOrder(listOf(3, 7))
        bankPreferences.savePlayerRebuys(3, 2)
        testDispatcher.scheduler.runCurrent()

        // 12 stacks of 5,000 among 8 players; 10 buy-ins and 2 rebuys in the pool
        assertEquals(
            TableStats(playerCount = 10, playersLeft = 8, averageStack = 7_500, prizePool = 220.0),
            viewModel.state.table
        )
    }

    @Test
    fun `time tone checks critical before low (B14)`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.ToggleTimer)
        assertEquals(TimeTone.NORMAL, viewModel.state.tone)

        advanceSeconds(roundSeconds * 3 / 4) // 25% left
        assertEquals(TimeTone.LOW, viewModel.state.tone)

        advanceSeconds(roundSeconds * 3 / 20) // 10% left
        assertEquals(TimeTone.CRITICAL, viewModel.state.tone)
    }

    @Test
    fun `reset clears the clock and keeps the setup`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.UpdateBreakEvery(3), TimerIntent.ToggleTimer)
        advanceSeconds(500)

        viewModel.send(TimerIntent.ResetTimer)

        assertFalse(viewModel.state.isRunning)
        assertFalse(viewModel.state.hasTimerStarted)
        assertEquals(0, viewModel.state.elapsedSeconds)
        assertEquals(3, viewModel.state.config.breaks.everyLevels)
        assertFalse(tournamentPreferences.getTournamentLocked())
        assertFalse(timerPreferences.getTimerRunning())
    }
}
