package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindLevel
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
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `isValidBlindConfiguration returns true for valid configuration`() {
        // Create a valid blind configuration (default 3-hour tournament)
        val validLevels = listOf(
            BlindLevel(1, 25, 50, 0, 0),      // Level 1: 0-20 minutes
            BlindLevel(2, 50, 100, 0, 20),    // Level 2: 20-40 minutes
            BlindLevel(3, 75, 150, 0, 40),    // Level 3: 40-60 minutes
            BlindLevel(4, 100, 200, 0, 60),   // Level 4: 60-80 minutes
            BlindLevel(5, 150, 300, 25, 80),  // Level 5: 80-100 minutes
            BlindLevel(6, 200, 400, 50, 100), // Level 6: 100-120 minutes
            BlindLevel(7, 300, 600, 75, 120), // Level 7: 120-140 minutes
            BlindLevel(8, 400, 800, 100, 140), // Level 8: 140-160 minutes
            BlindLevel(9, 600, 1200, 150, 160), // Level 9: 160-180 minutes
            BlindLevel(10, 800, 1600, 200, 180), // Level 10: 180-200 minutes (overtime)
            BlindLevel(11, 1000, 2000, 250, 200), // Level 11: 200-220 minutes (overtime)
            BlindLevel(12, 1500, 3000, 375, 220)  // Level 12: 220-240 minutes (overtime)
        )
        
        val state = TimerUiState(
            gameDurationMinutes = 180,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 20),
            blindLevels = validLevels
        )
        
        // Use reflection to access private method
        val method = TimerViewModel::class.java.getDeclaredMethod("isValidBlindConfiguration", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Boolean
        
        assertTrue(result, "Valid blind configuration should return true")
    }

    @Test
    fun `isValidBlindConfiguration returns false for empty levels`() {
        val state = TimerUiState(
            gameDurationMinutes = 180,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 20),
            blindLevels = emptyList()
        )
        
        val method = TimerViewModel::class.java.getDeclaredMethod("isValidBlindConfiguration", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Boolean
        
        assertFalse(result, "Empty blind levels should return false")
    }

    @Test
    fun `isValidBlindConfiguration returns false for non-monotonic start times`() {
        val invalidLevels = listOf(
            BlindLevel(1, 25, 50, 0, 0),
            BlindLevel(2, 50, 100, 0, 40), // Starts at 40, but should be 20
            BlindLevel(3, 75, 150, 0, 20)  // Starts at 20, which is before level 2
        )
        
        val state = TimerUiState(
            gameDurationMinutes = 180,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 20),
            blindLevels = invalidLevels
        )
        
        val method = TimerViewModel::class.java.getDeclaredMethod("isValidBlindConfiguration", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Boolean
        
        assertFalse(result, "Non-monotonic start times should return false")
    }

    @Test
    fun `isValidBlindConfiguration returns false for non-increasing blinds`() {
        val invalidLevels = listOf(
            BlindLevel(1, 25, 50, 0, 0),
            BlindLevel(2, 50, 100, 0, 20),
            BlindLevel(3, 25, 50, 0, 40) // Same blinds as level 1
        )
        
        val state = TimerUiState(
            gameDurationMinutes = 180,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 20),
            blindLevels = invalidLevels
        )
        
        val method = TimerViewModel::class.java.getDeclaredMethod("isValidBlindConfiguration", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Boolean
        
        assertFalse(result, "Non-increasing blinds should return false")
    }

    @Test
    fun `isValidBlindConfiguration returns false when final level ends before tournament duration`() {
        val invalidLevels = listOf(
            BlindLevel(1, 25, 50, 0, 0),
            BlindLevel(2, 50, 100, 0, 20) // Ends at 40 minutes, but tournament is 180 minutes
        )
        
        val state = TimerUiState(
            gameDurationMinutes = 180,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 20),
            blindLevels = invalidLevels
        )
        
        val method = TimerViewModel::class.java.getDeclaredMethod("isValidBlindConfiguration", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Boolean
        
        assertFalse(result, "Final level ending before tournament duration should return false")
    }

    @Test
    fun `isValidBlindConfiguration returns true for 1-hour game with 5-minute rounds`() {
        // 1 hour = 60 minutes, 5 minute rounds = 15 levels total
        // Level 15 starts at 70 minutes (10 minutes overtime)
        // Final time should be 70 + 5 = 75 minutes (15 minutes overtime)
        val validLevels = listOf(
            BlindLevel(1, 25, 50, 0, 0),
            BlindLevel(2, 50, 100, 0, 5),
            BlindLevel(3, 75, 150, 0, 10),
            BlindLevel(4, 100, 200, 0, 15),
            BlindLevel(5, 150, 300, 25, 20),
            BlindLevel(6, 200, 400, 50, 25),
            BlindLevel(7, 300, 600, 75, 30),
            BlindLevel(8, 400, 800, 100, 35),
            BlindLevel(9, 600, 1200, 150, 40),
            BlindLevel(10, 800, 1600, 200, 45),
            BlindLevel(11, 1000, 2000, 250, 50),
            BlindLevel(12, 1500, 3000, 375, 55),  // Base level 12 ends at 60 minutes
            BlindLevel(13, 2000, 4000, 500, 60),  // Overtime starts
            BlindLevel(14, 3000, 6000, 750, 65),
            BlindLevel(15, 4000, 8000, 1000, 70) // Level 15 starts at 70 minutes (10 min overtime)
        )
        
        val state = TimerUiState(
            gameDurationMinutes = 60,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 5),
            blindLevels = validLevels
        )
        
        // Use reflection to access private method
        val method = TimerViewModel::class.java.getDeclaredMethod("isValidBlindConfiguration", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Boolean
        
        assertTrue(result, "1-hour game with 5-minute rounds should be valid")
    }

    @Test
    fun `calculateFinalTimeSeconds returns correct time for 1-hour game with 5-minute rounds`() {
        // 1 hour = 60 minutes, 5 minute rounds = 15 levels total
        // Level 15 starts at 70 minutes (10 minutes overtime)
        // Final time should be 70 + 5 = 75 minutes = 4500 seconds
        val validLevels = listOf(
            BlindLevel(1, 25, 50, 0, 0),
            BlindLevel(2, 50, 100, 0, 5),
            BlindLevel(3, 75, 150, 0, 10),
            BlindLevel(4, 100, 200, 0, 15),
            BlindLevel(5, 150, 300, 25, 20),
            BlindLevel(6, 200, 400, 50, 25),
            BlindLevel(7, 300, 600, 75, 30),
            BlindLevel(8, 400, 800, 100, 35),
            BlindLevel(9, 600, 1200, 150, 40),
            BlindLevel(10, 800, 1600, 200, 45),
            BlindLevel(11, 1000, 2000, 250, 50),
            BlindLevel(12, 1500, 3000, 375, 55),  // Base level 12 ends at 60 minutes
            BlindLevel(13, 2000, 4000, 500, 60),  // Overtime starts
            BlindLevel(14, 3000, 6000, 750, 65),
            BlindLevel(15, 4000, 8000, 1000, 70) // Level 15 starts at 70 minutes
        )
        
        val state = TimerUiState(
            gameDurationMinutes = 60,
            blindConfiguration = BlindConfiguration(roundLengthMinutes = 5),
            blindLevels = validLevels
        )
        
        // Use reflection to access private method
        val method = TimerViewModel::class.java.getDeclaredMethod("calculateFinalTimeSeconds", TimerUiState::class.java)
        method.isAccessible = true
        val result = method.invoke(viewModel, state) as Int
        
        val expectedSeconds = 75 * 60 // 75 minutes * 60 seconds
        assertEquals(expectedSeconds, result, "Final time should be 75 minutes (4500 seconds) for 1-hour game with 5-minute rounds")
    }
}
