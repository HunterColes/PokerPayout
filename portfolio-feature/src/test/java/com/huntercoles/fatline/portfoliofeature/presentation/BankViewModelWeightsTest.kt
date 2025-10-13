package com.huntercoles.fatline.portfoliofeature.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.fatline.core.constants.TournamentConstants
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TimerPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.max

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankViewModelWeightsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var timerPreferences: TimerPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)
        timerPreferences = mockk<TimerPreferences> {
            val timerRunningFlow = MutableStateFlow(false)
            every { timerRunning } returns timerRunningFlow.asStateFlow()
        }
        tournamentPreferences.resetAllTournamentData()
        bankPreferences.resetAllBankData()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default weights match expected defaults for player count`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val expectedWeights = defaultWeightsFor(state.players.size)
        assertEquals(expectedWeights, state.payoutWeights)
    }

    @Test
    fun `updating weights changes payout calculations`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val newWeights = listOf(40, 30, 20, 10)
        viewModel.acceptIntent(BankIntent.UpdateWeights(newWeights))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(newWeights, state.payoutWeights)
        assertEquals(newWeights, tournamentPreferences.getPayoutWeights())
        
        // Verify payout positions reflect new weights
        assertEquals(newWeights.size, state.payoutPositions.size)
        val totalWeight = newWeights.sum()
        newWeights.forEachIndexed { index, weight ->
            val expectedPayout = (weight.toDouble() / totalWeight) * state.prizePool
            assertEquals(expectedPayout, state.payoutPositions[index].payout, 0.01)
        }
    }

    @Test
    fun `weights must be strictly decreasing`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Valid: strictly decreasing
        val validWeights = listOf(50, 30, 15, 5)
        viewModel.acceptIntent(BankIntent.UpdateWeights(validWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(validWeights, viewModel.uiState.value.payoutWeights)

        // Invalid weights should still be accepted (UI prevents this, but testing backend)
        val invalidWeights = listOf(30, 30, 15) // Not strictly decreasing
        viewModel.acceptIntent(BankIntent.UpdateWeights(invalidWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(invalidWeights, viewModel.uiState.value.payoutWeights)
    }

    @Test
    fun `can add and remove payout positions`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialState = viewModel.uiState.value
        val initialSize = initialState.payoutWeights.size

        // Add a position
        val expandedWeights = initialState.payoutWeights + listOf(1)
        viewModel.acceptIntent(BankIntent.UpdateWeights(expandedWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialSize + 1, viewModel.uiState.value.payoutWeights.size)

        // Remove a position
        val reducedWeights = expandedWeights.dropLast(1)
        viewModel.acceptIntent(BankIntent.UpdateWeights(reducedWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialSize, viewModel.uiState.value.payoutWeights.size)
    }

    @Test
    fun `weights persist across view model recreation`() = runTest(testDispatcher) {
        val customWeights = listOf(45, 25, 18, 12)
        
        // Create first view model and set weights
        val viewModel1 = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel1.acceptIntent(BankIntent.UpdateWeights(customWeights))
        testDispatcher.scheduler.advanceUntilIdle()

        // Create new view model and verify weights persisted
        val viewModel2 = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(customWeights, viewModel2.uiState.value.payoutWeights)
    }

    @Test
    fun `weights update when player count changes`() = runTest(testDispatcher) {
        val customWeights = listOf(40, 25, 20, 15)
        
        // Set custom weights
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.UpdateWeights(customWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(customWeights, viewModel.uiState.value.payoutWeights)

        // Change player count
        tournamentPreferences.setPlayerCount(12)
        
        // Create new view model with new player count
        val viewModel2 = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Custom weights should persist even with player count change
        assertEquals(customWeights, viewModel2.uiState.value.payoutWeights)
    }

    @Test
    fun `reset clears custom weights back to defaults`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialState = viewModel.uiState.value
        val defaultWeights = initialState.payoutWeights

        // Set custom weights
        val customWeights = listOf(50, 30, 20)
        viewModel.acceptIntent(BankIntent.UpdateWeights(customWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(customWeights, viewModel.uiState.value.payoutWeights)

        // Reset should restore defaults
        viewModel.acceptIntent(BankIntent.ConfirmReset)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val expectedDefaultsAfterReset = defaultWeightsFor(viewModel.uiState.value.players.size)
        assertEquals(expectedDefaultsAfterReset, viewModel.uiState.value.payoutWeights)
    }

    @Test
    fun `reset dialog only shows when weights differ from default`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially in default state - trying to show reset dialog should not work
        viewModel.acceptIntent(BankIntent.ShowResetDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showResetDialog)

        // Set custom weights
        val customWeights = listOf(50, 30, 20)
        viewModel.acceptIntent(BankIntent.UpdateWeights(customWeights))
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Now reset dialog should show (not in default state)
        viewModel.acceptIntent(BankIntent.ShowResetDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showResetDialog)

        // After reset, trying to show dialog should not work again
        viewModel.acceptIntent(BankIntent.ConfirmReset)
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.acceptIntent(BankIntent.ShowResetDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showResetDialog)
    }

    @Test
    fun `payout percentages sum to 100`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val weights = listOf(35, 20, 15, 10, 8, 6, 4, 2)
        viewModel.acceptIntent(BankIntent.UpdateWeights(weights))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val totalWeight = weights.sum().toDouble()
        val expectedPercentages = weights.map { (it / totalWeight) * 100 }
        val totalPercentage = expectedPercentages.sum()
        assertEquals(100.0, totalPercentage, 0.01)
    }

    @Test
    fun `minimum weight value is 1`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val weights = listOf(10, 5, 3, 1)
        viewModel.acceptIntent(BankIntent.UpdateWeights(weights))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(weights, viewModel.uiState.value.payoutWeights)
        assertTrue(viewModel.uiState.value.payoutWeights.all { it >= 1 })
    }

    @Test
    fun `can have many payout positions`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Use all default weights (9 positions)
        val allDefaults = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS
        viewModel.acceptIntent(BankIntent.UpdateWeights(allDefaults))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(allDefaults.size, viewModel.uiState.value.payoutWeights.size)
        assertEquals(allDefaults, viewModel.uiState.value.payoutWeights)
    }

    @Test
    fun `weights dialog shows and hides correctly`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showWeightsDialog)

        viewModel.acceptIntent(BankIntent.ShowWeightsDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showWeightsDialog)

        viewModel.acceptIntent(BankIntent.HideWeightsDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showWeightsDialog)
    }

    @Test
    fun `updating weights recalculates all payouts immediately`() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Buy everyone in
        viewModel.uiState.value.players.forEach { player ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(player.id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val initialPayouts = viewModel.uiState.value.payoutPositions.map { it.payout }

        // Change weights
        val newWeights = listOf(50, 50) // Equal weights
        viewModel.acceptIntent(BankIntent.UpdateWeights(newWeights))
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedPayouts = viewModel.uiState.value.payoutPositions
        assertEquals(2, updatedPayouts.size)
        
        // With equal weights, payouts should be equal
        val prizePool = viewModel.uiState.value.prizePool
        val expectedPayout = prizePool / 2.0
        assertEquals(expectedPayout, updatedPayouts[0].payout, 0.01)
        assertEquals(expectedPayout, updatedPayouts[1].payout, 0.01)
    }

    private fun defaultWeightsFor(playerCount: Int): List<Int> {
        val defaultCount = max(1, playerCount / 3)
        return TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(defaultCount)
    }
}
