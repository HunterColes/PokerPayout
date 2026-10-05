package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.audio.SoundManager
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
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

/**
 * TimerViewModel on virtual time (StandardTestDispatcher) with real preferences.
 *
 * Only SoundManager is mocked. The preferences are real because MockK can't stub their Flow
 * properties: each one shares its JVM getter name with a plain getter (`timerRunning` vs
 * `getTimerRunning()`), and which one MockK records depends on JVM method order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TimerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private val soundManager: SoundManager = mockk(relaxed = true)
    private val store = ViewModelStore()

    // Defaults: 3 h, 20-minute rounds, smallest chip 50, starting stack 5000
    private val durationSeconds = 180 * 60

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context).apply { resetAllTimerData() }
    }

    @After
    fun tearDown() {
        store.clear() // cancels a running clock
        Dispatchers.resetMain()
    }

    private fun newViewModel(): TimerViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimerViewModel(timerPreferences, tournamentPreferences, soundManager) as T
        }
        return ViewModelProvider(store, factory)[TimerViewModel::class.java]
            .also { testDispatcher.scheduler.advanceUntilIdle() }
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

    @Test
    fun `default configuration produces a valid nine level schedule`() {
        val viewModel = newViewModel()

        val levels = viewModel.state.baseBlindLevels
        assertEquals(9, levels.size)
        assertEquals(50, levels.first().smallBlind)
        assertEquals(5000, levels.last().smallBlind)
        assertEquals((0..8).map { it * 20 }, levels.map { it.roundStartMinute })
        assertEquals((1..9).toList(), levels.map { it.level })
        assertTrue(levels.all { it.bigBlind == 2 * it.smallBlind })
        assertEquals(levels, viewModel.state.blindLevels)
        // The last regular level ends exactly when the tournament does
        assertEquals(durationSeconds, viewModel.state.finalTimeSeconds)
        assertTrue(viewModel.isValidBlindConfiguration(viewModel.state))
    }

    @Test
    fun `running clock counts down once per second, persists, and locks the tournament`() {
        val viewModel = newViewModel()

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(5)

        assertTrue(viewModel.state.isRunning)
        assertEquals(durationSeconds - 5, viewModel.state.currentTimeSeconds)
        assertEquals("2:59:55", viewModel.state.formattedTime)
        assertEquals(durationSeconds - 5, timerPreferences.getCurrentTimeSeconds())
        assertTrue(timerPreferences.getTimerRunning())
        assertTrue(tournamentPreferences.getTournamentLocked())

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(5)

        assertFalse(viewModel.state.isRunning)
        assertEquals(durationSeconds - 5, viewModel.state.currentTimeSeconds)
        assertFalse(timerPreferences.getTimerRunning())
        assertFalse(tournamentPreferences.getTournamentLocked())
    }

    @Test
    fun `blind level advances when the clock crosses a round boundary`() {
        val viewModel = newViewModel()
        // One second before level 2 (20 minutes in)
        viewModel.send(TimerIntent.TimerTick(durationSeconds - 20 * 60 + 1))
        assertEquals(0, viewModel.state.currentBlindLevelIndex)
        assertEquals(1, viewModel.state.nextLevelStartsInSeconds)

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(1)

        assertEquals(1, viewModel.state.currentBlindLevelIndex)
        assertEquals(100, viewModel.state.currentBlindLevel?.smallBlind)
    }

    @Test
    fun `level change sound plays four seconds before the next level`() {
        val viewModel = newViewModel()
        // Six seconds before level 2
        viewModel.send(TimerIntent.TimerTick(durationSeconds - 20 * 60 + 6), TimerIntent.ToggleTimer)

        advanceSeconds(1) // 5 s to go
        verify(exactly = 0) { soundManager.playSound(any()) }

        advanceSeconds(1) // 4 s to go
        verify(exactly = 1) { soundManager.playSound(R.raw.blind_level_up) }

        advanceSeconds(3) // past the change: no replay
        verify(exactly = 1) { soundManager.playSound(any()) }
    }

    @Test
    fun `countdown switches to overtime count-up when it reaches zero`() {
        val viewModel = newViewModel()
        viewModel.send(TimerIntent.TimerTick(3), TimerIntent.ToggleTimer)

        advanceSeconds(3)

        assertEquals(TimerDirection.COUNTUP, viewModel.state.timerDirection)
        assertEquals(0, viewModel.state.currentTimeSeconds)
        assertTrue(viewModel.state.isOvertime)
        assertEquals("COUNTUP", timerPreferences.getTimerDirection())
    }

    @Test
    fun `next level past the schedule reveals a doubled overtime level`() {
        val viewModel = newViewModel()
        repeat(8) { viewModel.send(TimerIntent.NextBlindLevel) }
        assertEquals(8, viewModel.state.currentBlindLevelIndex)
        assertEquals(TimerDirection.COUNTDOWN, viewModel.state.timerDirection)
        assertEquals(20 * 60, viewModel.state.currentTimeSeconds)

        viewModel.send(TimerIntent.NextBlindLevel)

        val overtime = viewModel.state.blindLevels.last()
        assertEquals(10, viewModel.state.blindLevels.size)
        assertEquals(9, viewModel.state.currentBlindLevelIndex)
        assertEquals(1, viewModel.state.overtimeLevelsRevealed)
        assertEquals(10_000, overtime.smallBlind)
        assertEquals(180, overtime.roundStartMinute)
        assertEquals((180 + 20) * 60, viewModel.state.finalTimeSeconds)
        assertEquals(TimerDirection.COUNTUP, viewModel.state.timerDirection)
        assertEquals(1, timerPreferences.getOvertimeLevelsRevealed())

        // Going back drops the overtime level again
        viewModel.send(TimerIntent.PreviousBlindLevel)
        assertEquals(9, viewModel.state.blindLevels.size)
        assertEquals(0, viewModel.state.overtimeLevelsRevealed)
    }

    @Test
    fun `an impossible blind setup refuses to start and shows the invalid config dialog`() {
        val viewModel = newViewModel()
        // Starting stack below the smallest chip: no schedule exists
        viewModel.send(TimerIntent.UpdateSmallestChip(100), TimerIntent.UpdateStartingChips(50))
        assertTrue(viewModel.state.baseBlindLevels.isEmpty())

        viewModel.send(TimerIntent.ToggleTimer)
        advanceSeconds(3)

        assertTrue(viewModel.state.showInvalidConfigDialog)
        assertFalse(viewModel.state.isRunning)
        assertEquals(durationSeconds, viewModel.state.currentTimeSeconds)
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
        assertFalse("blinds must strictly increase", viewModel.isValidBlindConfiguration(valid.copy(baseBlindLevels = flatStep)))
        val backwardsTime = levels.toMutableList().apply { this[3] = this[3].copy(roundStartMinute = this[2].roundStartMinute) }
        assertFalse("start times must increase", viewModel.isValidBlindConfiguration(valid.copy(baseBlindLevels = backwardsTime)))
        assertFalse(
            "levels end an hour before a 4 h tournament",
            viewModel.isValidBlindConfiguration(valid.copy(gameDurationMinutes = 240))
        )
    }
}
