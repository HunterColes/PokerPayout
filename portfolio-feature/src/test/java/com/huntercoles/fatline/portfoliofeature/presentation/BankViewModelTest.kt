package com.huntercoles.fatline.portfoliofeature.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun totalPaidInReflectsBuyIns() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
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
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
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
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
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
        tournamentPreferences.setRebuyAmount(10.0)
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
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
    fun rebuyDialogIgnoredWhenRebuyDisabled() = runTest(testDispatcher) {
        tournamentPreferences.setRebuyAmount(0.0)
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pendingAction)
        assertEquals(0.0, state.rebuyAmount, 0.001)
    }

    @Test
    fun addonDialogRespectConfiguration() = runTest(testDispatcher) {
        tournamentPreferences.setAddOnAmount(15.0)
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.players.first { it.id == 1 }.addons)
        assertEquals(15.0, state.addonAmount, 0.001)

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(0, state.players.first { it.id == 1 }.addons)
    }

    @Test
    fun addonDialogIgnoredWhenAddonDisabled() = runTest(testDispatcher) {
        tournamentPreferences.setAddOnAmount(0.0)
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.pendingAction)
        assertEquals(0.0, state.addonAmount, 0.001)
    }

    @Test
    fun lastActivePlayerCannotBeEliminated() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Knock out player 2 to leave player 1 as the only active participant
        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(true, state.players.first { it.id == 2 }.out)
        assertEquals(1, state.activePlayers)

        // Attempt to eliminate the final active player
        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(null, state.pendingAction)
        assertEquals(false, state.players.first { it.id == 1 }.out)
        assertEquals(1, state.activePlayers)
    }

    @Test
    fun knockoutDialogUpdatesEliminationOrderAndUndoRestores() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
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

    @Test
    fun assigningKnockoutCreditsEliminator() = runTest(testDispatcher) {
        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT))
        testDispatcher.scheduler.advanceUntilIdle()

        val pending = viewModel.uiState.value.pendingAction
    assertTrue(pending?.selectablePlayerIds?.contains(1) == true)
    assertEquals(1, pending?.selectedPlayerId)

    viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(1, state.players.first { it.id == 2 }.eliminatedBy)
        assertEquals(1, state.knockoutCounts[1])

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptIntent(BankIntent.ConfirmPlayerAction)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.players.first { it.id == 2 }.eliminatedBy)
        assertNull(state.knockoutCounts[1])
    }

    @Test
    fun payoutEligiblePlayersReflectStandings() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))

        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.OutToggled(3))
        viewModel.acceptIntent(BankIntent.OutToggled(2))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf(1, 2, 3), state.payoutEligiblePlayerIds)
    }

    @Test
    fun rebuysAndAddonsAdjustTotalsAndPayouts() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(0.0)
        tournamentPreferences.setRebuyAmount(100.0)
        tournamentPreferences.setAddOnAmount(50.0)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))

        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        listOf(1, 2).forEach { id ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val baselineState = viewModel.uiState.value
        assertEquals(400.0, baselineState.totalPool, 0.001)
        assertEquals(200.0, baselineState.totalPaidIn, 0.001)
        val baselinePercent = baselineState.totalPaidIn / baselineState.totalPool
        assertEquals(0.5, baselinePercent, 0.001)

        viewModel.acceptIntent(BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2))
        viewModel.acceptIntent(BankIntent.PlayerRebuyChanged(playerId = 2, rebuys = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        val afterRebuys = viewModel.uiState.value
        assertEquals(300.0, afterRebuys.rebuyPool, 0.001)
        assertEquals(3, afterRebuys.totalRebuyCount)
        assertEquals(700.0, afterRebuys.totalPool, 0.001)
        assertEquals(500.0, afterRebuys.totalPaidIn, 0.001)
        val percentAfterRebuys = afterRebuys.totalPaidIn / afterRebuys.totalPool
        assertTrue(percentAfterRebuys > baselinePercent)

        viewModel.acceptIntent(BankIntent.PlayerAddonChanged(playerId = 1, addons = 1))
        viewModel.acceptIntent(BankIntent.PlayerAddonChanged(playerId = 2, addons = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        val afterAddons = viewModel.uiState.value
        assertEquals(150.0, afterAddons.addonPool, 0.001)
        assertEquals(3, afterAddons.totalAddonCount)
        assertEquals(850.0, afterAddons.totalPool, 0.001)
        assertEquals(650.0, afterAddons.totalPaidIn, 0.001)
        val percentAfterAddons = afterAddons.totalPaidIn / afterAddons.totalPool
        assertTrue(percentAfterAddons > percentAfterRebuys)
        assertEquals(850.0, afterAddons.prizePool, 0.001)

        (4 downTo 2).forEach { id ->
            viewModel.acceptIntent(BankIntent.OutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        listOf(3, 2, 1).forEach { id ->
            viewModel.acceptIntent(BankIntent.PayedOutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val payoutState = viewModel.uiState.value
        assertEquals(850.0, payoutState.totalPayedOut, 0.001)
    }

    @Test
    fun clearingRebuyOrAddonAmountsResetsPurchases() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setRebuyAmount(25.0)
        tournamentPreferences.setAddOnAmount(15.0)

        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2))
        viewModel.acceptIntent(BankIntent.PlayerRebuyChanged(playerId = 2, rebuys = 1))
        viewModel.acceptIntent(BankIntent.PlayerAddonChanged(playerId = 1, addons = 1))
        viewModel.acceptIntent(BankIntent.PlayerAddonChanged(playerId = 3, addons = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(3, state.totalRebuyCount)
        assertEquals(3, state.totalAddonCount)

        tournamentPreferences.setRebuyAmount(0.0)
        tournamentPreferences.setAddOnAmount(0.0)
        testDispatcher.scheduler.advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(0, state.totalRebuyCount)
        assertEquals(0, state.totalAddonCount)
        assertEquals(0, state.players.count { it.rebuys > 0 })
        assertEquals(0, state.players.count { it.addons > 0 })
        assertEquals(0.0, state.rebuyPool, 0.001)
        assertEquals(0.0, state.addonPool, 0.001)
    }

    @Test
    fun bountyPoolIsIncludedInPrizePool() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(10.0)
        tournamentPreferences.setRebuyAmount(0.0)
        tournamentPreferences.setAddOnAmount(0.0)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))

        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(400.0, state.buyInPool, 0.001)
        assertEquals(40.0, state.bountyPool, 0.001)
        assertEquals(400.0, state.prizePool, 0.001) // buyInPool only - bounties are separate
    }

    @Test
    fun bountyPoolAffectsPayoutPercentages() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(0.0)
        tournamentPreferences.setRebuyAmount(0.0)
        tournamentPreferences.setAddOnAmount(0.0)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))

        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Set up players and eliminate them
        listOf(1, 2, 3, 4).forEach { id ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(id))
        }
        (4 downTo 2).forEach { id ->
            viewModel.acceptIntent(BankIntent.OutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val noBountyState = viewModel.uiState.value
        assertEquals(400.0, noBountyState.prizePool, 0.001)

        // Enable bounties
        tournamentPreferences.setBountyPerPlayer(10.0)
        testDispatcher.scheduler.advanceUntilIdle()

        val withBountyState = viewModel.uiState.value
        assertEquals(400.0, withBountyState.prizePool, 0.001) // prize pool unchanged
        assertEquals(40.0, withBountyState.bountyPool, 0.001) // bounty pool separate

        // Payout should equal prize pool + king's bounty (no knockouts in this test)
        listOf(3, 2, 1).forEach { id ->
            viewModel.acceptIntent(BankIntent.PayedOutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val payoutState = viewModel.uiState.value
        assertEquals(410.0, payoutState.totalPayedOut, 0.001) // prize pool + king's bounty
    }

    @Test
    fun knockoutBonusesAreAddedToLeaderboardPayouts() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(10.0)
        tournamentPreferences.setRebuyAmount(0.0)
        tournamentPreferences.setAddOnAmount(0.0)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))

        val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        testDispatcher.scheduler.advanceUntilIdle()

        // Set up players
        listOf(1, 2, 3, 4).forEach { id ->
            viewModel.acceptIntent(BankIntent.BuyInToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Player 1 knocks out player 4, then player 2 knocks out player 1
        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 4, action = PlayerActionType.OUT))
        viewModel.acceptIntent(BankIntent.ConfirmPlayerActionWithCount(selectedPlayerId = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        viewModel.acceptIntent(BankIntent.ConfirmPlayerActionWithCount(selectedPlayerId = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        // Eliminate remaining players
        viewModel.acceptIntent(BankIntent.OutToggled(3))
        testDispatcher.scheduler.advanceUntilIdle()

        // Check knockout counts
        val stateAfterEliminations = viewModel.uiState.value
        assertEquals(1, stateAfterEliminations.knockoutCounts[1]) // Player 1 has 1 knockout
        assertEquals(1, stateAfterEliminations.knockoutCounts[2]) // Player 2 has 1 knockout

        // Calculate expected payouts
        val prizePool = 400.0 // buy-in only
        val totalWeight = 6.0 // 3 + 2 + 1
        val firstPlaceBase = (3.0 / totalWeight) * prizePool // 3/6 * 400 = 200
        val secondPlaceBase = (2.0 / totalWeight) * prizePool // 2/6 * 400 = 133.33
        val thirdPlaceBase = (1.0 / totalWeight) * prizePool // 1/6 * 400 = 66.67

        // Player 2 (1st place) gets base + 1 knockout bonus + king's bounty = 200 + 10 + 10 = 220
        // Player 1 (2nd place) gets base + 1 knockout bonus = 133.33 + 10 = 143.33
        // Player 3 (3rd place) gets base + 0 knockout bonus = 66.67 + 0 = 66.67
        // Total: 220 + 143.33 + 66.67 = 430

        // Payout players
        listOf(2, 1, 3).forEach { id ->
            viewModel.acceptIntent(BankIntent.PayedOutToggled(id))
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val payoutState = viewModel.uiState.value
        assertEquals(430.0, payoutState.totalPayedOut, 0.001) // prize pool + knockout bonuses + king's bounty
    }

    @Test
    fun moneyConservationInvariant() = runTest(testDispatcher) {
        // Test various tournament configurations to ensure money conservation
        val testCases = listOf(
            // (playerCount, buyIn, bounty, rebuy, addon, weights, description)
            TestCase(4, 100.0, 0.0, 0.0, 0.0, listOf(3, 2, 1), "No bounties/rebuys/addons"),
            TestCase(4, 100.0, 10.0, 0.0, 0.0, listOf(3, 2, 1), "Bounties only"),
            TestCase(4, 100.0, 0.0, 20.0, 0.0, listOf(3, 2, 1), "Rebuys only"),
            TestCase(4, 100.0, 0.0, 0.0, 15.0, listOf(3, 2, 1), "Addons only"),
            TestCase(4, 100.0, 5.0, 25.0, 20.0, listOf(3, 2, 1), "All features"),
            TestCase(6, 50.0, 15.0, 0.0, 0.0, listOf(4, 3, 2, 1), "6 players, different weights")
        )

        testCases.forEach { testCase ->
            tournamentPreferences.setPlayerCount(testCase.playerCount)
            tournamentPreferences.setBuyIn(testCase.buyIn)
            tournamentPreferences.setFoodPerPlayer(0.0)
            tournamentPreferences.setBountyPerPlayer(testCase.bounty)
            tournamentPreferences.setRebuyAmount(testCase.rebuy)
            tournamentPreferences.setAddOnAmount(testCase.addon)
            tournamentPreferences.setPayoutWeights(testCase.weights)

            val viewModel = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
            testDispatcher.scheduler.advanceUntilIdle()

            // All players buy in
            (1..testCase.playerCount).forEach { id ->
                viewModel.acceptIntent(BankIntent.BuyInToggled(id))
            }
            testDispatcher.scheduler.advanceUntilIdle()

            // Add rebuys and addons if configured
            if (testCase.rebuy > 0) {
                viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(1, PlayerActionType.REBUY))
                viewModel.acceptIntent(BankIntent.ConfirmPlayerActionWithCount(count = 2))
                testDispatcher.scheduler.advanceUntilIdle()
            }
            if (testCase.addon > 0) {
                viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(2, PlayerActionType.ADDON))
                viewModel.acceptIntent(BankIntent.ConfirmPlayerActionWithCount(count = 1))
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Eliminate players to leave only the number needed for payouts
            val playersToEliminate = testCase.playerCount - testCase.weights.size
            (testCase.playerCount downTo testCase.weights.size + 1).forEach { id ->
                if (testCase.bounty > 0 && id == testCase.playerCount) {
                    // Last elimination gets a knockout
                    viewModel.acceptIntent(BankIntent.ShowPlayerActionDialog(id, PlayerActionType.OUT))
                    viewModel.acceptIntent(BankIntent.ConfirmPlayerActionWithCount(selectedPlayerId = 1))
                } else {
                    viewModel.acceptIntent(BankIntent.OutToggled(id))
                }
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Pay out remaining players
            (1..testCase.weights.size).forEach { id ->
                viewModel.acceptIntent(BankIntent.PayedOutToggled(id))
            }
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value

            // Invariant: totalPaidIn should equal totalPayedOut when all payouts are made
            assertEquals(state.totalPaidIn, state.totalPayedOut, 0.001)
        }
    }

    private data class TestCase(
        val playerCount: Int,
        val buyIn: Double,
        val bounty: Double,
        val rebuy: Double,
        val addon: Double,
        val weights: List<Int>,
        val description: String
    )
}
