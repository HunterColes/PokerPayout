package com.huntercoles.fatline.portfoliofeature.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)
        tournamentPreferences.resetAllTournamentData()
        bankPreferences.resetAllBankData()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun totalPaidInReflectsBuyIns() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialState = viewModel.uiState.value
        assertEquals(0.0, initialState.totalPaidIn, 0.001)

        val totalPerPlayer = initialState.totalPool / initialState.players.size

        (1..3).forEach { id ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertEquals(totalPerPlayer * 3, updatedState.totalPaidIn, 0.001)

        (4..initialState.players.size).forEach { id ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val fullState = viewModel.uiState.value
        assertEquals(fullState.totalPool, fullState.totalPaidIn, 0.001)
    }

    @Test
    fun totalPayedOutTracksPayoutPositions() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialState = viewModel.uiState.value
        val weights = listOf(35, 20, 15)
        val totalWeight = weights.sum().toDouble()
        val expectedPayouts = weights.map { weight ->
            (weight / totalWeight) * initialState.prizePool
        }

        // Everyone buys in
        (1..initialState.players.size).forEach { id ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Eliminate players from last place to heads-up so elimination order is deterministic
        (initialState.players.size downTo 2).forEach { id ->
            viewModel.acceptIntent(BankIntent.OutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Pay out third, second, and first place
        listOf(3, 2, 1).forEach { id ->
            viewModel.acceptIntent(BankIntent.PayedOutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val payoutState = viewModel.uiState.value
        assertEquals(expectedPayouts.sum(), payoutState.totalPayedOut, 0.001)

        // Remove second place payout and verify adjustment
        viewModel.acceptIntent(BankIntent.PayedOutToggled(2))
        testDispatcher.scheduler.advanceUntilIdle()

        val adjustedState = viewModel.uiState.value
        val expectedAfterToggle = expectedPayouts[0] + expectedPayouts[2]
        assertEquals(expectedAfterToggle, adjustedState.totalPayedOut, 0.001)
    }

    @Test
    fun confirmationDialogAppliesAndUndoesBuyIn() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN))
        testDispatcher.scheduler.advanceUntilIdle()
        val afterShow = viewModel.uiState.value
        assertEquals(false, afterShow.players.first { it.id == 1 }.buyIn)
        assertEquals(PlayerActionType.BUY_IN, afterShow.pendingAction?.actionType)
        assertEquals(true, afterShow.pendingAction?.apply)

        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        val afterConfirm = viewModel.uiState.value
        assertEquals(true, afterConfirm.players.first { it.id == 1 }.buyIn)
        assertEquals(null, afterConfirm.pendingAction)

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        val afterUndo = viewModel.uiState.value
        assertEquals(false, afterUndo.players.first { it.id == 1 }.buyIn)
    }

    @Test
    fun rebuyDialogTogglesCounts() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.players.first { it.id == 1 }.rebuys)

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.pendingAction?.apply)

        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(0, state.players.first { it.id == 1 }.rebuys)
    }

    @Test
    fun knockoutDialogUpdatesEliminationOrderAndUndoRestores() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.pendingAction?.apply)
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(true, state.players.first { it.id == 1 }.out)
        assertEquals(listOf(1), state.eliminationOrder.takeLast(1))

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.pendingAction?.apply)
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(false, state.players.first { it.id == 1 }.out)
        assertEquals(false, state.eliminationOrder.contains(1))
    }
}
