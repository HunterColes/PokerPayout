package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BankViewModel against real preferences (Robolectric's in-memory SharedPreferences).
 *
 * The preferences are deliberately not mocked. TimerPreferences and TournamentPreferences declare
 * a Flow property and a same-named getter (`val timerRunning: Flow<Boolean>` next to
 * `fun getTimerRunning(): Boolean`), which compile to two JVM methods that differ only in return
 * type. MockK cannot tell them apart, so `every { timerRunning }` failed with "Missing mocked
 * calls inside every { ... } block" depending on JVM method order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var timerPreferences: TimerPreferences
    private val viewModelStores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "bank_prefs", "timer_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)
        timerPreferences = TimerPreferences(context)
    }

    @After
    fun tearDown() {
        clearViewModels()
        Dispatchers.resetMain()
    }

    /** Creates the ViewModel in a store so [clearViewModels] can cancel its coroutines. */
    private fun newViewModel(): BankViewModel {
        val store = ViewModelStore().also { viewModelStores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BankViewModel(tournamentPreferences, bankPreferences, timerPreferences) as T
        }
        val viewModel = ViewModelProvider(store, factory)[BankViewModel::class.java]
        settle()
        return viewModel
    }

    private fun clearViewModels() {
        viewModelStores.forEach { it.clear() }
        viewModelStores.clear()
    }

    private fun settle() = testDispatcher.scheduler.advanceUntilIdle()

    private fun BankViewModel.send(vararg intents: BankIntent) {
        intents.forEach { acceptIntent(it) }
        settle()
    }

    private fun BankViewModel.player(id: Int) = uiState.value.players.first { it.id == id }

    /** Eliminates [playerId] through the knock-out dialog, crediting [eliminatorId]. */
    private fun BankViewModel.knockOut(playerId: Int, eliminatorId: Int) = send(
        BankIntent.ShowPlayerActionDialog(playerId, PlayerActionType.OUT),
        BankIntent.ConfirmPlayerActionWithCount(selectedPlayerId = eliminatorId)
    )

    /** Net pay the Pay-Out dialog shows for [playerId] (winnings minus everything they paid). */
    private fun BankViewModel.netPayShownFor(playerId: Int): Double {
        send(BankIntent.ShowPlayerActionDialog(playerId, PlayerActionType.PAYED_OUT))
        val pending = requireNotNull(uiState.value.pendingAction)
        assertTrue("dialog for player $playerId should offer to pay out", pending.apply)
        send(BankIntent.CancelPlayerAction)
        return pending.payoutAmount
    }

    private fun configure(
        players: Int,
        buyIn: Double,
        food: Double = 0.0,
        bounty: Double = 0.0,
        rebuy: Double = 0.0,
        addOn: Double = 0.0,
        weights: List<Int>? = null
    ) {
        tournamentPreferences.setPlayerCount(players)
        tournamentPreferences.setBuyIn(buyIn)
        tournamentPreferences.setFoodPerPlayer(food)
        tournamentPreferences.setBountyPerPlayer(bounty)
        tournamentPreferences.setRebuyAmount(rebuy)
        tournamentPreferences.setAddOnAmount(addOn)
        weights?.let { tournamentPreferences.setPayoutWeights(it) }
    }

    @Test
    fun totalPaidInReflectsBuyIns() {
        configure(players = 5, buyIn = 20.0, food = 5.0)
        val viewModel = newViewModel()
        assertEquals(0.0, viewModel.uiState.value.totalPaidIn, 0.001)
        assertEquals(125.0, viewModel.uiState.value.totalPool, 0.001)

        (1..3).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        assertEquals(75.0, viewModel.uiState.value.totalPaidIn, 0.001)

        (4..5).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        assertEquals(125.0, viewModel.uiState.value.totalPaidIn, 0.001)
    }

    @Test
    fun timerRunningStateIsMirroredFromTimerPreferences() {
        val viewModel = newViewModel()
        assertFalse(viewModel.uiState.value.isTimerRunning)

        timerPreferences.setTimerRunning(true)
        settle()
        assertTrue(viewModel.uiState.value.isTimerRunning)

        timerPreferences.setTimerRunning(false)
        settle()
        assertFalse(viewModel.uiState.value.isTimerRunning)
    }

    @Test
    fun totalPayedOutTracksPayoutPositions() {
        // Prize pool 5 x 20 = 100, split 35:20:15 -> 50.00, 28.57, 21.43
        configure(players = 5, buyIn = 20.0, food = 5.0, weights = listOf(35, 20, 15))
        val viewModel = newViewModel()
        (1..5).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        (5 downTo 2).forEach { viewModel.send(BankIntent.OutToggled(it)) }

        listOf(3, 2, 1).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }
        assertEquals(100.0, viewModel.uiState.value.totalPayedOut, 0.001)

        // Un-paying 2nd place (player 2) takes its 20/70 share back out of the total
        viewModel.send(BankIntent.PayedOutToggled(2))
        assertEquals(71.428571, viewModel.uiState.value.totalPayedOut, 0.0001)
    }

    @Test
    fun confirmationDialogAppliesAndUndoesBuyIn() {
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN))
        assertFalse(viewModel.player(1).buyIn)
        assertEquals(PlayerActionType.BUY_IN, viewModel.uiState.value.pendingAction?.actionType)
        assertEquals(true, viewModel.uiState.value.pendingAction?.apply)

        viewModel.send(BankIntent.ConfirmPlayerAction)
        assertTrue(viewModel.player(1).buyIn)
        assertNull(viewModel.uiState.value.pendingAction)

        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN),
            BankIntent.ConfirmPlayerAction
        )
        assertFalse(viewModel.player(1).buyIn)
    }

    @Test
    fun buyInCostCalculationIncludesAddons() {
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(10.0)
        tournamentPreferences.setAddOnAmount(25.0)
        val viewModel = newViewModel()

        viewModel.send(
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 1),
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN)
        )
        val pending = requireNotNull(viewModel.uiState.value.pendingAction)
        assertEquals(PlayerActionType.BUY_IN, pending.actionType)
        assertTrue(pending.apply)
        // 100 buy-in + 10 food + 1 x 25 add-on
        assertEquals(135.0, pending.buyInCost, 0.001)

        viewModel.send(
            BankIntent.ConfirmPlayerAction,
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 2),
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN),
            BankIntent.ConfirmPlayerAction, // un-check
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN)
        )
        // 100 + 10 + 2 x 25
        assertEquals(160.0, requireNotNull(viewModel.uiState.value.pendingAction).buyInCost, 0.001)
    }

    @Test
    fun rebuyDialogSuggestsOneMoreAndAcceptsAnyCountUpToTheCap() {
        tournamentPreferences.setRebuyAmount(10.0)
        val viewModel = newViewModel()

        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY),
            BankIntent.ConfirmPlayerAction
        )
        assertEquals(1, viewModel.player(1).rebuys)

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY))
        val pending = requireNotNull(viewModel.uiState.value.pendingAction)
        assertTrue(pending.apply)
        assertEquals(1, pending.baseCount)
        assertEquals(2, pending.targetCount)

        // The dialog's stepper can take the count back down to zero...
        viewModel.send(BankIntent.ConfirmPlayerActionWithCount(count = 0))
        assertEquals(0, viewModel.player(1).rebuys)
        assertEquals(0.0, viewModel.uiState.value.rebuyPool, 0.001)

        // ...and never past the cap.
        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY),
            BankIntent.ConfirmPlayerActionWithCount(count = 999)
        )
        assertEquals(MAX_PURCHASE_COUNT, viewModel.player(1).rebuys)
        assertEquals(MAX_PURCHASE_COUNT * 10.0, viewModel.uiState.value.rebuyPool, 0.001)
    }

    @Test
    fun rebuyDialogIgnoredWhenRebuyDisabled() {
        tournamentPreferences.setRebuyAmount(0.0)
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY))

        assertNull(viewModel.uiState.value.pendingAction)
        assertEquals(0.0, viewModel.uiState.value.rebuyAmount, 0.001)
    }

    @Test
    fun addonDialogSuggestsOneMoreAndCanBeSetBackToZero() {
        tournamentPreferences.setAddOnAmount(15.0)
        val viewModel = newViewModel()

        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON),
            BankIntent.ConfirmPlayerAction
        )
        assertEquals(1, viewModel.player(1).addons)
        assertEquals(15.0, viewModel.uiState.value.addonAmount, 0.001)
        assertEquals(15.0, viewModel.uiState.value.addonPool, 0.001)

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))
        val pending = requireNotNull(viewModel.uiState.value.pendingAction)
        assertEquals(1, pending.baseCount)
        assertEquals(2, pending.targetCount)

        viewModel.send(BankIntent.ConfirmPlayerActionWithCount(count = 0))
        assertEquals(0, viewModel.player(1).addons)
        assertEquals(0.0, viewModel.uiState.value.addonPool, 0.001)
    }

    @Test
    fun addonDialogIgnoredWhenAddonDisabled() {
        tournamentPreferences.setAddOnAmount(0.0)
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))

        assertNull(viewModel.uiState.value.pendingAction)
        assertEquals(0.0, viewModel.uiState.value.addonAmount, 0.001)
    }

    @Test
    fun lastActivePlayerCannotBeEliminated() {
        tournamentPreferences.setPlayerCount(2)
        val viewModel = newViewModel()

        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT),
            BankIntent.ConfirmPlayerAction
        )
        assertTrue(viewModel.player(2).out)
        assertEquals(1, viewModel.uiState.value.activePlayers)

        // The dialog for the final active player never opens
        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        assertNull(viewModel.uiState.value.pendingAction)
        assertFalse(viewModel.player(1).out)
        assertEquals(1, viewModel.uiState.value.activePlayers)
    }

    @Test
    fun knockoutDialogUpdatesEliminationOrderAndUndoRestores() {
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        assertEquals(true, viewModel.uiState.value.pendingAction?.apply)
        viewModel.send(BankIntent.ConfirmPlayerAction)
        assertTrue(viewModel.player(1).out)
        assertEquals(listOf(1), viewModel.uiState.value.eliminationOrder)
        assertEquals(listOf(1), bankPreferences.getEliminationOrder())

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.OUT))
        assertEquals(false, viewModel.uiState.value.pendingAction?.apply)
        viewModel.send(BankIntent.ConfirmPlayerAction)
        assertFalse(viewModel.player(1).out)
        assertEquals(emptyList<Int>(), viewModel.uiState.value.eliminationOrder)
    }

    @Test
    fun assigningKnockoutCreditsEliminator() {
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT))
        val pending = requireNotNull(viewModel.uiState.value.pendingAction)
        assertEquals(listOf(1, 3, 4, 5), pending.selectablePlayerIds)
        assertEquals(1, pending.selectedPlayerId)

        viewModel.send(BankIntent.ConfirmPlayerActionWithCount(selectedPlayerId = 3))
        assertEquals(3, viewModel.player(2).eliminatedBy)
        assertEquals(mapOf(3 to 1), viewModel.uiState.value.knockoutCounts)

        // Bringing player 2 back clears the credit
        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT),
            BankIntent.ConfirmPlayerAction
        )
        assertNull(viewModel.player(2).eliminatedBy)
        assertEquals(emptyMap<Int, Int>(), viewModel.uiState.value.knockoutCounts)
    }

    @Test
    fun confirmingAKnockoutWithoutChoosingLeavesItUnassigned() {
        val viewModel = newViewModel()

        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 2, action = PlayerActionType.OUT),
            BankIntent.ConfirmPlayerAction
        )

        assertTrue(viewModel.player(2).out)
        assertNull(viewModel.player(2).eliminatedBy)
        assertEquals(emptyMap<Int, Int>(), viewModel.uiState.value.knockoutCounts)
    }

    @Test
    fun payoutEligiblePlayersReflectStandings() {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))
        val viewModel = newViewModel()

        viewModel.send(BankIntent.OutToggled(3))
        assertEquals(setOf(3), viewModel.uiState.value.payoutEligiblePlayerIds)

        viewModel.send(BankIntent.OutToggled(2))
        assertEquals(setOf(1, 2, 3), viewModel.uiState.value.payoutEligiblePlayerIds)
    }

    @Test
    fun rebuysAndAddonsAdjustTotalsAndPayouts() {
        configure(players = 4, buyIn = 100.0, rebuy = 100.0, addOn = 50.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()

        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.BuyInToggled(2))
        assertEquals(400.0, viewModel.uiState.value.totalPool, 0.001)
        assertEquals(200.0, viewModel.uiState.value.totalPaidIn, 0.001)

        viewModel.send(
            BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2),
            BankIntent.PlayerRebuyChanged(playerId = 2, rebuys = 1)
        )
        with(viewModel.uiState.value) {
            assertEquals(300.0, rebuyPool, 0.001)
            assertEquals(3, totalRebuyCount)
            assertEquals(700.0, totalPool, 0.001)
            assertEquals(500.0, totalPaidIn, 0.001)
        }

        viewModel.send(
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 1),
            BankIntent.PlayerAddonChanged(playerId = 2, addons = 2)
        )
        with(viewModel.uiState.value) {
            assertEquals(150.0, addonPool, 0.001)
            assertEquals(3, totalAddonCount)
            assertEquals(850.0, totalPool, 0.001)
            assertEquals(650.0, totalPaidIn, 0.001)
            assertEquals(850.0, prizePool, 0.001)
        }

        (4 downTo 2).forEach { viewModel.send(BankIntent.OutToggled(it)) }
        listOf(3, 2, 1).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }

        assertEquals(850.0, viewModel.uiState.value.totalPayedOut, 0.001)
    }

    @Ignore(
        "PP-014: clearing the Rebuy/Add-on amount field (which emits 0) wipes every recorded " +
            "purchase. Enable this when PP-014 lands; it is the spec for the fix."
    )
    @Test
    fun purchasesSurviveTheAmountBeingClearedAndRetyped() {
        configure(players = 3, buyIn = 20.0, rebuy = 25.0, addOn = 15.0)
        val viewModel = newViewModel()
        viewModel.send(
            BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2),
            BankIntent.PlayerRebuyChanged(playerId = 2, rebuys = 1),
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 1),
            BankIntent.PlayerAddonChanged(playerId = 3, addons = 2)
        )

        // What the Tournament tab writes while the user clears the field and types the amount again
        tournamentPreferences.setRebuyAmount(0.0)
        tournamentPreferences.setAddOnAmount(0.0)
        settle()
        tournamentPreferences.setRebuyAmount(25.0)
        tournamentPreferences.setAddOnAmount(15.0)
        settle()

        with(viewModel.uiState.value) {
            assertEquals(3, totalRebuyCount)
            assertEquals(3, totalAddonCount)
        }
        assertEquals(2, bankPreferences.getPlayerRebuys(1))
        assertEquals(2, bankPreferences.getPlayerAddons(3))
    }

    @Test
    fun bountyPoolIsSeparateFromPrizePool() {
        configure(players = 4, buyIn = 100.0, bounty = 10.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()

        with(viewModel.uiState.value) {
            assertEquals(400.0, buyInPool, 0.001)
            assertEquals(40.0, bountyPool, 0.001)
            assertEquals(400.0, prizePool, 0.001)
            assertEquals(440.0, totalPool, 0.001)
        }
    }

    @Test
    fun winnerCollectsKingsBountyWhenNoKnockoutsWereCredited() {
        configure(players = 4, buyIn = 100.0, bounty = 10.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()
        (1..4).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        (4 downTo 2).forEach { viewModel.send(BankIntent.OutToggled(it)) }

        listOf(3, 2, 1).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }

        // 400 prize pool + the winner's own 10 bounty
        assertEquals(410.0, viewModel.uiState.value.totalPayedOut, 0.001)
    }

    @Ignore(
        "PP-018: BankViewModel doesn't observe buy-in, food or bounty changes, so a live Bank " +
            "screen shows stale pools until its next action. Enable when the Bank recalculates."
    )
    @Test
    fun bankTotalsFollowTournamentConfigChanges() {
        configure(players = 4, buyIn = 100.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()

        tournamentPreferences.setBountyPerPlayer(10.0)
        tournamentPreferences.setBuyIn(50.0)
        settle()

        with(viewModel.uiState.value) {
            assertEquals(40.0, bountyPool, 0.001)
            assertEquals(200.0, prizePool, 0.001)
        }
    }

    @Test
    fun knockoutBonusesAreAddedToLeaderboardPayouts() {
        configure(players = 4, buyIn = 100.0, bounty = 10.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()
        (1..4).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }

        // Player 1 knocks out player 4, then player 2 knocks out player 1; player 3 goes out unassigned
        viewModel.knockOut(playerId = 4, eliminatorId = 1)
        viewModel.knockOut(playerId = 1, eliminatorId = 2)
        viewModel.send(BankIntent.OutToggled(3))
        assertEquals(mapOf(1 to 1, 2 to 1), viewModel.uiState.value.knockoutCounts)

        listOf(2, 1, 3).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }

        // 1st (player 2): 200 + 10 KO + 10 king's bounty; 2nd (player 1): 133.33 + 10 KO;
        // 3rd (player 3): 66.67
        assertEquals(430.0, viewModel.uiState.value.totalPayedOut, 0.001)
    }

    @Test
    fun netPayShownInThePayOutDialogIsWinningsMinusEverythingPaid() {
        configure(
            players = 4, buyIn = 100.0, food = 20.0, bounty = 10.0, rebuy = 50.0, addOn = 25.0,
            weights = listOf(3, 2, 1)
        )
        val viewModel = newViewModel()
        (1..4).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        viewModel.send(
            BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 1),
            BankIntent.PlayerAddonChanged(playerId = 2, addons = 1)
        )
        // Player 1 knocks everyone out
        (4 downTo 2).forEach { viewModel.knockOut(playerId = it, eliminatorId = 1) }

        // Prize pool 400 + 50 + 25 = 475, split 3:2:1 -> 237.50, 158.33, 79.17
        // P1: 237.50 + 3 KOs x 10 + king's bounty 10 - (130 + 50 rebuy) =  97.50
        // P2: 158.33 - (130 + 25 add-on)                                  =   3.33
        // P3:  79.17 - 130                                                = -50.83
        // P4:   0.00 - 130                                                = -130.00
        val netPays = (1..4).map { viewModel.netPayShownFor(it) }
        assertEquals(97.5, netPays[0], 0.0001)
        assertEquals(3.333333, netPays[1], 0.0001)
        assertEquals(-50.833333, netPays[2], 0.0001)
        assertEquals(-130.0, netPays[3], 0.0001)
        // Players as a group are down exactly what was spent on food
        assertEquals(-80.0, netPays.sum(), 0.0001)
    }

    @Test
    fun buyInsSumToTotalPool() {
        configure(players = 4, buyIn = 50.0, food = 10.0, bounty = 5.0)
        val viewModel = newViewModel()

        (1..4).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }

        // 4 x (50 + 10 + 5)
        assertEquals(260.0, viewModel.uiState.value.totalPool, 0.001)
        assertEquals(260.0, viewModel.uiState.value.totalPaidIn, 0.001)
    }

    @Test
    fun buyInCostsSumToTotalPool() {
        configure(players = 3, buyIn = 100.0, food = 20.0, bounty = 10.0, rebuy = 50.0, addOn = 25.0)
        val viewModel = newViewModel()
        viewModel.send(
            BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2),
            BankIntent.PlayerRebuyChanged(playerId = 2, rebuys = 1),
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 1),
            BankIntent.PlayerAddonChanged(playerId = 3, addons = 2)
        )

        val buyInCosts = (1..3).map { id ->
            viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = id, action = PlayerActionType.BUY_IN))
            val cost = requireNotNull(viewModel.uiState.value.pendingAction).buyInCost
            viewModel.send(BankIntent.CancelPlayerAction)
            cost
        }

        // P1: 130 + 2 x 50 + 1 x 25; P2: 130 + 50; P3: 130 + 2 x 25
        assertEquals(listOf(255.0, 180.0, 180.0), buyInCosts)
        assertEquals(615.0, viewModel.uiState.value.totalPool, 0.001)
    }

    /**
     * Money conservation over a range of tournaments. With every knockout credited and everyone in
     * the money paid: what the Bank paid out = what it took in - the food pool, and the players'
     * net pays sum to -food pool.
     */
    @Test
    fun moneyIsConservedAcrossTournamentConfigurations() {
        val cases = listOf(
            MoneyCase("plain", 4, 100.0, weights = listOf(3, 2, 1)),
            MoneyCase("bounties", 4, 100.0, bounty = 10.0, weights = listOf(3, 2, 1)),
            MoneyCase("rebuys", 4, 100.0, rebuy = 20.0, rebuysEach = 1, weights = listOf(3, 2, 1)),
            MoneyCase("add-ons", 4, 100.0, addOn = 15.0, addOnsEach = 1, weights = listOf(3, 2, 1)),
            MoneyCase(
                "everything", 4, 100.0, food = 10.0, bounty = 5.0, rebuy = 25.0, addOn = 20.0,
                rebuysEach = 2, addOnsEach = 1, weights = listOf(3, 2, 1)
            ),
            MoneyCase("6 players", 6, 50.0, bounty = 15.0, weights = listOf(4, 3, 2, 1)),
            MoneyCase("penny", 2, 0.01, weights = listOf(1)),
            MoneyCase(
                "high roller", 3, 10_000.0, food = 5_000.0, bounty = 1_000.0, rebuy = 2_000.0,
                rebuysEach = 1, weights = listOf(2, 1)
            ),
            MoneyCase("prime players", 7, 13.0, food = 7.0, bounty = 3.0, rebuy = 5.0, rebuysEach = 1, weights = listOf(4, 3, 2, 1)),
            MoneyCase("equal weights", 4, 25.0, food = 25.0, bounty = 25.0, rebuy = 25.0, weights = listOf(1, 1, 1)),
            MoneyCase("single player", 1, 100.0, food = 50.0, bounty = 25.0, weights = listOf(1)),
            MoneyCase(
                "max purchases", 3, 10.0, food = 5.0, bounty = 2.0, rebuy = 1.0, addOn = 1.0,
                rebuysEach = MAX_PURCHASE_COUNT, addOnsEach = MAX_PURCHASE_COUNT, weights = listOf(2, 1)
            ),
            MoneyCase("bounty only", 3, 0.0, food = 100.0, bounty = 20.0, weights = listOf(2, 1)),
            MoneyCase("odd amounts", 3, 14.14, food = 7.07, bounty = 3.54, rebuy = 1.77, rebuysEach = 1, weights = listOf(2, 1))
        )

        cases.forEach { case ->
            clearViewModels()
            bankPreferences.resetAllBankData()
            configure(case.players, case.buyIn, case.food, case.bounty, case.rebuy, case.addOn, case.weights)
            val viewModel = newViewModel()

            (1..case.players).forEach { id ->
                viewModel.send(
                    BankIntent.BuyInToggled(id),
                    BankIntent.PlayerRebuyChanged(id, case.rebuysEach),
                    BankIntent.PlayerAddonChanged(id, case.addOnsEach)
                )
            }
            // Player 1 wins and is credited with every knockout
            (case.players downTo 2).forEach { viewModel.knockOut(playerId = it, eliminatorId = 1) }

            // Amounts are stored as Float, so compare to the cent
            val expectedPaidIn = case.players * (case.buyIn + case.food + case.bounty +
                case.rebuysEach * case.rebuy + case.addOnsEach * case.addOn)
            val state = viewModel.uiState.value
            assertEquals("${case.name}: total paid in", expectedPaidIn, state.totalPaidIn, 0.01)
            assertEquals("${case.name}: total pool", expectedPaidIn, state.totalPool, 0.01)

            val netPays = (1..case.players).map { viewModel.netPayShownFor(it) }
            assertEquals("${case.name}: sum of net pays", -state.foodPool, netPays.sum(), 0.01)

            state.payoutEligiblePlayerIds.forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }
            val paid = viewModel.uiState.value
            assertEquals("${case.name}: places paid", case.weights.size, paid.payedOutCount)
            assertEquals("${case.name}: paid out", paid.totalPaidIn - paid.foodPool, paid.totalPayedOut, 0.01)
        }
    }

    private data class MoneyCase(
        val name: String,
        val players: Int,
        val buyIn: Double,
        val food: Double = 0.0,
        val bounty: Double = 0.0,
        val rebuy: Double = 0.0,
        val addOn: Double = 0.0,
        val rebuysEach: Int = 0,
        val addOnsEach: Int = 0,
        val weights: List<Int>
    )
}
