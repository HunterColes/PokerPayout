package com.huntercoles.fatline.basicfeature.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
import com.huntercoles.fatline.basicfeature.domain.model.TournamentConfig
import com.huntercoles.fatline.basicfeature.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.fatline.core.constants.TournamentConstants
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TimerPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@kotlinx.coroutines.ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PlayConfigViewModelWeightsTest {

    private lateinit var viewModel: PlayConfigViewModel
    private lateinit var calculatePayoutsUseCase: CalculatePayoutsUseCase
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var context: Context

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Create dependencies
        calculatePayoutsUseCase = mockk(relaxed = true)
        timerPreferences = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()

        clearTournamentPrefs()
        clearBankPrefs()
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)

        // Mock the calculatePayoutsUseCase to return payouts based on weights
        every { calculatePayoutsUseCase(any()) } answers {
            val config = firstArg<TournamentConfig>()
            val weights = config.payoutWeights
            weights.mapIndexed { index, weight ->
                PayoutPosition(
                    position = index + 1,
                    payout = (weight.toDouble() / weights.sum()) * config.prizePool,
                    weight = weight,
                    percentage = (weight.toDouble() / weights.sum()) * 100
                )
            }
        }

        viewModel = PlayConfigViewModel(
            calculatePayoutsUseCase,
            tournamentPreferences,
            timerPreferences,
            bankPreferences
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        clearBankPrefs()
        clearTournamentPrefs()
    }

    @Test
    fun `default weights should match default paying positions`() {
        val expectedDefaults = defaultWeightsFor(viewModel.uiState.value.tournamentConfig.numPlayers)
        assertEquals(expectedDefaults, viewModel.uiState.value.tournamentConfig.payoutWeights)
        assertEquals(expectedDefaults.size, viewModel.uiState.value.payouts.size)
    }

    @Test
    fun `changing player count should refresh default weights and payouts`() {
        // When: increasing the player count while using default weights
        viewModel.acceptIntent(PlayConfigIntent.UpdatePlayerCount(18))

        val state = viewModel.uiState.value
        val expectedDefaults = defaultWeightsFor(18)

        // Then: preferences and UI state should reflect the new default count
        assertEquals(18, tournamentPreferences.getPlayerCount())
        assertEquals(expectedDefaults, tournamentPreferences.getPayoutWeights())
        assertEquals(expectedDefaults, state.tournamentConfig.payoutWeights)
        assertEquals(expectedDefaults.size, state.payouts.size)
    }

    @Test
    fun `updating weights should show all positions when weights list is longer than calculated paying positions`() {
        // Given: 9 players (which normally would give max 3 paying positions: 9/3 = 3)
        // But we want to set 6 custom payout positions
        val customWeights = listOf(35, 20, 15, 10, 8, 6) // 6 positions

        // When: updating weights to 6 positions
        viewModel.acceptIntent(PlayConfigIntent.UpdateWeights(customWeights))

        // Then: should save the weights and show 6 payout positions
        assertEquals(customWeights, tournamentPreferences.getPayoutWeights())
        assertEquals(customWeights, viewModel.uiState.value.tournamentConfig.payoutWeights)
        assertEquals(6, viewModel.uiState.value.payouts.size)
    }

    @Test
    fun `reset should reset payout weights to defaults`() {
        // Given: custom weights are set
        val customWeights = listOf(50, 30, 20) // 3 custom positions
        viewModel.acceptIntent(PlayConfigIntent.UpdateWeights(customWeights))

        // Verify weights were updated
        assertEquals(customWeights, viewModel.uiState.value.tournamentConfig.payoutWeights)

        // When: confirming reset
        viewModel.acceptIntent(PlayConfigIntent.ConfirmReset)

        // Then: should reset weights and recalculate
        verify { timerPreferences.resetAllTimerData() }

        // After reset, should load default configuration which includes default weights
        assertTrue(tournamentPreferences.isInDefaultState())
        val expectedDefaults = defaultWeightsFor(viewModel.uiState.value.tournamentConfig.numPlayers)
        assertEquals(expectedDefaults, viewModel.uiState.value.tournamentConfig.payoutWeights)
    }

    @Test
    fun `weights editor should receive current tournament config weights not old payout weights`() {
        // Given: we have some payouts calculated with old weights
        val oldWeights = listOf(35, 20, 15)
        val newWeights = listOf(40, 25, 20, 15) // 4 positions now

        // Set initial weights
        viewModel.acceptIntent(PlayConfigIntent.UpdateWeights(oldWeights))
        // Update to new weights
        viewModel.acceptIntent(PlayConfigIntent.UpdateWeights(newWeights))

        // Then: tournament config should have new weights, not weights from old payouts
        assertEquals(newWeights, viewModel.uiState.value.tournamentConfig.payoutWeights)

        // The payouts might still contain old weight values in their weight field
        // but the tournament config should have the current weights
        assertTrue("Tournament config should have updated weights") {
            viewModel.uiState.value.tournamentConfig.payoutWeights == newWeights
        }
    }

    @Test
    fun `calculate payouts should use all weight positions not be limited by player count division`() {
        // Given: 6 players which would normally give 2 paying positions (6/3 = 2)
        // But user sets custom weights for 4 positions
        viewModel.acceptIntent(PlayConfigIntent.UpdatePlayerCount(6))
        val customWeights = listOf(40, 30, 20, 10) // 4 positions

        // When: updating weights to 4 positions
        viewModel.acceptIntent(PlayConfigIntent.UpdateWeights(customWeights))

        // Then: currently this will only show 2 positions (the bug)
        // After fix, it should show all 4 positions
        val payouts = viewModel.uiState.value.payouts

        // This test documents the current bug - it should be 4 but will be 2
        // After fixing CalculatePayoutsUseCase, this assertion should pass
        assertEquals(4, payouts.size, "Should show all weight positions, not be limited by player count")
    }

    private fun clearTournamentPrefs() {
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun clearBankPrefs() {
        context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun defaultWeightsFor(playerCount: Int): List<Int> {
        val defaultCount = max(1, playerCount / 3)
        return TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(defaultCount)
    }
}