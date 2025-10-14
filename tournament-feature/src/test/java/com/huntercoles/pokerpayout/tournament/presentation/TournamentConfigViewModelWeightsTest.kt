package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.tournament.domain.model.PayoutPosition
import com.huntercoles.pokerpayout.tournament.domain.model.TournamentConfig
import com.huntercoles.pokerpayout.tournament.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@kotlinx.coroutines.ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class TournamentConfigViewModelWeightsTest {

    private lateinit var viewModel: TournamentConfigViewModel
    private lateinit var calculatePayoutsUseCase: CalculatePayoutsUseCase
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var context: Context

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()

        clearTournamentPrefs()
        clearTimerPrefs()
        clearBankPrefs()
        
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)

        // Create and configure calculatePayoutsUseCase mock
        calculatePayoutsUseCase = mockk(relaxed = true)
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

        viewModel = TournamentConfigViewModel(
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
        clearTimerPrefs()
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
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(18))

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
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(customWeights))

        // Then: should save the weights and show 6 payout positions
        assertEquals(customWeights, tournamentPreferences.getPayoutWeights())
        assertEquals(customWeights, viewModel.uiState.value.tournamentConfig.payoutWeights)
        assertEquals(6, viewModel.uiState.value.payouts.size)
    }

    @Test
    fun `reset should reset payout weights to defaults`() {
        // Given: custom weights are set AND timer preferences are modified
        val customWeights = listOf(50, 30, 20) // 3 custom positions
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(customWeights))
        
        // Modify timer preferences to ensure they're not in default state
        timerPreferences.setGameDurationMinutes(240) // Change from default 180

        // Verify weights were updated and timer is not in default state
        assertEquals(customWeights, viewModel.uiState.value.tournamentConfig.payoutWeights)
        assertEquals(240, timerPreferences.getGameDurationMinutes())
        assertFalse(timerPreferences.isInDefaultState())

        // When: confirming reset
        viewModel.acceptIntent(TournamentConfigIntent.ConfirmReset)

        // Then: should reset both tournament AND timer preferences
        // After reset, should load default configuration which includes default weights
        assertTrue(tournamentPreferences.isInDefaultState())
        assertTrue(timerPreferences.isInDefaultState())
        assertEquals(180, timerPreferences.getGameDurationMinutes()) // Back to default
        val expectedDefaults = defaultWeightsFor(viewModel.uiState.value.tournamentConfig.numPlayers)
        assertEquals(expectedDefaults, viewModel.uiState.value.tournamentConfig.payoutWeights)
    }

    @Test
    fun `weights editor should receive current tournament config weights not old payout weights`() {
        // Given: we have some payouts calculated with old weights
        val oldWeights = listOf(35, 20, 15)
        val newWeights = listOf(40, 25, 20, 15) // 4 positions now

        // Set initial weights
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(oldWeights))
        // Update to new weights
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(newWeights))

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
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(6))
        val customWeights = listOf(40, 30, 20, 10) // 4 positions

        // When: updating weights to 4 positions
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(customWeights))

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

    private fun clearTimerPrefs() {
        context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
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
