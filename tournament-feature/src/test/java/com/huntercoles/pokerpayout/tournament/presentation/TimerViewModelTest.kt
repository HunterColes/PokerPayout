package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class TimerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var viewModel: TimerViewModel
    private lateinit var playerCountFlow: MutableStateFlow<Int>

    @BeforeEach
    fun setUp() {
        System.setProperty("io.mockk.useImplClassLoader", "true")
        Dispatchers.setMain(testDispatcher)

        playerCountFlow = MutableStateFlow(10)
        
        // Create relaxed mocks to avoid MockK exceptions
        timerPreferences = mockk(relaxed = true)
        tournamentPreferences = mockk(relaxed = true)

        every { tournamentPreferences.playerCount } returns playerCountFlow
        every { tournamentPreferences.getPlayerCount() } returns 10
        
        every { timerPreferences.calculateActualTime() } returns 180 * 60
        every { timerPreferences.getTimerDirection() } returns "COUNTDOWN"
        every { timerPreferences.getTimerRunning() } returns false
        every { timerPreferences.getIsFinished() } returns false
        every { timerPreferences.getHasTimerStarted() } returns false
        every { timerPreferences.getGameDurationMinutes() } returns 180

        viewModel = TimerViewModel(timerPreferences, tournamentPreferences)
        
        // Allow coroutines to complete
        testDispatcher.scheduler.advanceUntilIdle()
        
        clearMocks(timerPreferences, answers = false)
        clearMocks(tournamentPreferences, answers = false)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

}
