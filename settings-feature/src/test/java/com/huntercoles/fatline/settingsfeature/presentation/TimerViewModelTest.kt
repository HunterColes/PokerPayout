package com.huntercoles.fatline.settingsfeature.presentation

import com.huntercoles.fatline.core.preferences.TimerPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val timerPreferences: TimerPreferences = mockk(relaxed = true)
    private val tournamentPreferences: TournamentPreferences = mockk(relaxed = true)
    private lateinit var viewModel: TimerViewModel
    private lateinit var playerCountFlow: MutableStateFlow<Int>

    @BeforeEach
    fun setUp() {
        System.setProperty("io.mockk.useImplClassLoader", "true")
        Dispatchers.setMain(testDispatcher)

        playerCountFlow = MutableStateFlow(10)

        every { tournamentPreferences.playerCount } returns playerCountFlow
        every { tournamentPreferences.getPlayerCount() } returns 10
        justRun { tournamentPreferences.setTournamentLocked(any()) }

        every { timerPreferences.calculateActualTime() } returns 180 * 60
        every { timerPreferences.getTimerDirection() } returns "COUNTDOWN"
        every { timerPreferences.getTimerRunning() } returns false
        every { timerPreferences.getIsFinished() } returns false
        every { timerPreferences.getHasTimerStarted() } returns false
        every { timerPreferences.getGameDurationMinutes() } returns 180
        justRun { timerPreferences.resetTimer() }
        justRun { timerPreferences.setCurrentTimeSeconds(any()) }
        justRun { timerPreferences.setTimerRunning(any()) }
        justRun { timerPreferences.setIsFinished(any()) }
        justRun { timerPreferences.setHasTimerStarted(any()) }
        justRun { timerPreferences.setGameDurationMinutes(any()) }
        justRun { timerPreferences.setTimerDirection(any()) }

        viewModel = TimerViewModel(timerPreferences, tournamentPreferences)
        clearMocks(timerPreferences, answers = false)
        clearMocks(tournamentPreferences, answers = false)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `next blind level advances index and rewinds countdown`() {
        val initialIndex = viewModel.uiState.value.currentBlindLevelIndex

        viewModel.acceptIntent(TimerIntent.NextBlindLevel)

        val updated = viewModel.uiState.value
        assertEquals(initialIndex + 1, updated.currentBlindLevelIndex)

        val targetLevel = updated.blindLevels[updated.currentBlindLevelIndex]
        val expectedElapsedSeconds = targetLevel.roundStartMinute * 60
        val expectedCurrentSeconds = (updated.totalDurationSeconds - expectedElapsedSeconds)
            .coerceIn(0, updated.totalDurationSeconds)
        assertEquals(expectedCurrentSeconds, updated.currentTimeSeconds)

        verify { timerPreferences.setCurrentTimeSeconds(expectedCurrentSeconds) }
    }

    @Test
    fun `previous blind level steps back when not at start`() {
        viewModel.acceptIntent(TimerIntent.NextBlindLevel)
        clearMocks(timerPreferences, answers = false)

        viewModel.acceptIntent(TimerIntent.PreviousBlindLevel)

        val state = viewModel.uiState.value
        assertEquals(0, state.currentBlindLevelIndex)
    assertEquals(state.totalDurationSeconds, state.currentTimeSeconds)
    assertTrue(state.hasTimerStarted)
        verify(exactly = 0) { timerPreferences.resetTimer() }
        verify { timerPreferences.setCurrentTimeSeconds(state.currentTimeSeconds) }
    }

    @Test
    fun `previous blind level at start triggers full reset`() {
        viewModel.acceptIntent(TimerIntent.PreviousBlindLevel)

        val state = viewModel.uiState.value
        assertEquals(0, state.currentBlindLevelIndex)
        assertEquals(state.totalDurationSeconds, state.currentTimeSeconds)
        assertFalse(state.hasTimerStarted)

        verify(exactly = 1) { timerPreferences.resetTimer() }
    }
}
