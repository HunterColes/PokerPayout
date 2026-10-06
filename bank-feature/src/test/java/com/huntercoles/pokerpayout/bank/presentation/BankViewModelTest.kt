package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.Money
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * BankViewModel against real preferences (Robolectric's in-memory SharedPreferences). Every amount
 * is in cents and compared exactly.
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BankViewModel(
                tournamentPreferences,
                bankPreferences,
                timerPreferences,
                SettleTournamentUseCase(CalculatePayoutsUseCase())
            ) as T
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

    /** Eliminates [playerId] through the knock-out dialog, crediting [eliminatorId] (null: "Nobody"). */
    private fun BankViewModel.knockOut(playerId: Int, eliminatorId: Int?) = send(
        BankIntent.ShowPlayerActionDialog(playerId, PlayerActionType.OUT),
        BankIntent.ConfirmPlayerActionWithCount(selectedPlayerId = eliminatorId)
    )

    /** Net pay the Pay-Out dialog shows for [playerId] (winnings minus everything they paid). */
    private fun BankViewModel.netPayShownFor(playerId: Int): Long {
        send(BankIntent.ShowPlayerActionDialog(playerId, PlayerActionType.PAYED_OUT))
        val pending = requireNotNull(uiState.value.pendingAction)
        assertTrue("dialog for player $playerId should offer to pay out", pending.apply)
        send(BankIntent.CancelPlayerAction)
        return pending.payoutAmountCents
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
        assertEquals(0L, viewModel.uiState.value.totalPaidInCents)
        assertEquals(12_500L, viewModel.uiState.value.totalPoolCents)

        (1..3).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        assertEquals(7_500L, viewModel.uiState.value.totalPaidInCents)

        (4..5).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        assertEquals(12_500L, viewModel.uiState.value.totalPaidInCents)
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
    fun totalPaidOutTracksTheRoundedPayoutTable() {
        // Prize pool 5 x 20 = 100, split 35:20:15 -> 2nd 28.57 -> $29, 3rd 21.43 -> $21, 1st $50
        configure(players = 5, buyIn = 20.0, food = 5.0, weights = listOf(35, 20, 15))
        val viewModel = newViewModel()
        assertEquals(listOf(5_000L, 2_900L, 2_100L), viewModel.uiState.value.payoutTable.places.map { it.amountCents })
        (1..5).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        (5 downTo 2).forEach { viewModel.send(BankIntent.OutToggled(it)) }

        listOf(3, 2, 1).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }
        assertEquals(10_000L, viewModel.uiState.value.totalPaidOutCents)

        // Un-paying 2nd place (player 2) takes its $29 back out of the total
        viewModel.send(BankIntent.PayedOutToggled(2))
        assertEquals(7_100L, viewModel.uiState.value.totalPaidOutCents)
    }

    @Test
    fun theBankUsesTheSamePayoutTableAsTheTournamentTab() {
        configure(players = 10, buyIn = 25.0, weights = listOf(35, 20, 15))
        val viewModel = newViewModel()

        val expected = CalculatePayoutsUseCase()(25_000, listOf(35, 20, 15), 10)
        assertEquals(expected, viewModel.uiState.value.payoutTable)
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
        assertEquals(13_500L, pending.buyInCostCents)

        viewModel.send(
            BankIntent.ConfirmPlayerAction,
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 2),
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN),
            BankIntent.ConfirmPlayerAction, // un-check
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.BUY_IN)
        )
        // 100 + 10 + 2 x 25
        assertEquals(16_000L, requireNotNull(viewModel.uiState.value.pendingAction).buyInCostCents)
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
        assertEquals(0L, viewModel.uiState.value.pool.rebuyCents)

        // ...and never past the cap.
        viewModel.send(
            BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY),
            BankIntent.ConfirmPlayerActionWithCount(count = 999)
        )
        assertEquals(MAX_PURCHASE_COUNT, viewModel.player(1).rebuys)
        assertEquals(MAX_PURCHASE_COUNT * 1_000L, viewModel.uiState.value.pool.rebuyCents)
    }

    @Test
    fun rebuyDialogIgnoredWhenRebuyDisabled() {
        tournamentPreferences.setRebuyAmount(0.0)
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.REBUY))

        assertNull(viewModel.uiState.value.pendingAction)
        assertFalse(viewModel.uiState.value.isRebuyEnabled)
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
        assertEquals(1_500L, viewModel.uiState.value.money.addOnCents)
        assertEquals(1_500L, viewModel.uiState.value.pool.addOnCents)

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))
        val pending = requireNotNull(viewModel.uiState.value.pendingAction)
        assertEquals(1, pending.baseCount)
        assertEquals(2, pending.targetCount)

        viewModel.send(BankIntent.ConfirmPlayerActionWithCount(count = 0))
        assertEquals(0, viewModel.player(1).addons)
        assertEquals(0L, viewModel.uiState.value.pool.addOnCents)
    }

    @Test
    fun addonDialogIgnoredWhenAddonDisabled() {
        tournamentPreferences.setAddOnAmount(0.0)
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowPlayerActionDialog(playerId = 1, action = PlayerActionType.ADDON))

        assertNull(viewModel.uiState.value.pendingAction)
        assertFalse(viewModel.uiState.value.isAddOnEnabled)
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
        assertEquals(mapOf(1 to 5), viewModel.uiState.value.placeByPlayer)

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
        assertEquals(40_000L, viewModel.uiState.value.totalPoolCents)
        assertEquals(20_000L, viewModel.uiState.value.totalPaidInCents)

        viewModel.send(
            BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2),
            BankIntent.PlayerRebuyChanged(playerId = 2, rebuys = 1)
        )
        with(viewModel.uiState.value) {
            assertEquals(30_000L, pool.rebuyCents)
            assertEquals(3, totalRebuyCount)
            assertEquals(70_000L, totalPoolCents)
            assertEquals(50_000L, totalPaidInCents)
        }

        viewModel.send(
            BankIntent.PlayerAddonChanged(playerId = 1, addons = 1),
            BankIntent.PlayerAddonChanged(playerId = 2, addons = 2)
        )
        with(viewModel.uiState.value) {
            assertEquals(15_000L, pool.addOnCents)
            assertEquals(3, totalAddonCount)
            assertEquals(85_000L, totalPoolCents)
            assertEquals(65_000L, totalPaidInCents)
            assertEquals(85_000L, prizePoolCents)
        }

        (4 downTo 2).forEach { viewModel.send(BankIntent.OutToggled(it)) }
        listOf(3, 2, 1).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }

        assertEquals(85_000L, viewModel.uiState.value.totalPaidOutCents)
    }

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
            assertEquals(7_500L, pool.rebuyCents)
        }
        assertEquals(2, bankPreferences.getPlayerRebuys(1))
        assertEquals(2, bankPreferences.getPlayerAddons(3))
    }

    @Test
    fun purchasesClearedOnTheTournamentTabDisappearFromTheBank() {
        configure(players = 3, buyIn = 20.0, rebuy = 25.0)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerRebuyChanged(playerId = 1, rebuys = 2))

        // The Tournament tab's "Clear rebuys" after the user confirmed a zero amount
        bankPreferences.clearAllRebuys()
        tournamentPreferences.setRebuyAmount(0.0)
        settle()

        assertEquals(0, viewModel.player(1).rebuys)
        assertEquals(0L, viewModel.uiState.value.pool.rebuyCents)
    }

    @Test
    fun bountyPoolIsSeparateFromPrizePool() {
        configure(players = 4, buyIn = 100.0, bounty = 10.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()

        with(viewModel.uiState.value) {
            assertEquals(40_000L, pool.buyInCents)
            assertEquals(4_000L, pool.bountyPoolCents)
            assertEquals(40_000L, prizePoolCents)
            assertEquals(44_000L, totalPoolCents)
            assertEquals(44_000L, payableCents)
        }
    }

    @Test
    fun withNoKnockoutsCreditedTheChampionCollectsEveryBounty() {
        configure(players = 4, buyIn = 100.0, bounty = 10.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()
        (1..4).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        (4 downTo 2).forEach { viewModel.send(BankIntent.OutToggled(it)) }

        // The champion's Pay-Out breakdown lists both
        viewModel.send(BankIntent.ShowPlayerActionDialog(1, PlayerActionType.PAYED_OUT))
        val pending = requireNotNull(viewModel.uiState.value.pendingAction)
        assertEquals(20_000L, pending.buyInPayoutCents)
        assertEquals(1_000L, pending.kingsBountyCents)
        assertEquals(3_000L, pending.unclaimedBountyCents)
        viewModel.send(BankIntent.CancelPlayerAction)

        listOf(3, 2, 1).forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }

        // 400 prize pool + the winner's own 10 bounty + the 3 bounties nobody claimed
        assertEquals(44_000L, viewModel.uiState.value.totalPaidOutCents)
    }

    @Test
    fun bankTotalsFollowTournamentConfigChanges() {
        configure(players = 4, buyIn = 100.0, weights = listOf(3, 2, 1))
        val viewModel = newViewModel()

        tournamentPreferences.setBountyPerPlayer(10.0)
        tournamentPreferences.setBuyIn(50.0)
        settle()

        with(viewModel.uiState.value) {
            assertEquals(4_000L, pool.bountyPoolCents)
            assertEquals(20_000L, prizePoolCents)
            assertEquals(20_000L, payoutTable.totalCents)
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

        // 1st (player 2): $200 + 10 KO + 10 king's bounty + player 3's unclaimed 10;
        // 2nd (player 1): $133 + 10 KO; 3rd (player 3): $67
        assertEquals(44_000L, viewModel.uiState.value.totalPaidOutCents)
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

        // Prize pool 400 + 50 + 25 = 475, split 3:2:1 -> 2nd 158.33 -> $158, 3rd 79.17 -> $79, 1st $238
        // P1: 238 + 3 KOs x 10 + king's bounty 10 - (130 + 50 rebuy) =  98
        // P2: 158 - (130 + 25 add-on)                                 =   3
        // P3:  79 - 130                                               = -51
        // P4:   0 - 130                                               = -130
        val netPays = (1..4).map { viewModel.netPayShownFor(it) }
        assertEquals(listOf(9_800L, 300L, -5_100L, -13_000L), netPays)
        // Players as a group are down exactly what was spent on food
        assertEquals(-8_000L, netPays.sum())
    }

    @Test
    fun buyInsSumToTotalPool() {
        configure(players = 4, buyIn = 50.0, food = 10.0, bounty = 5.0)
        val viewModel = newViewModel()

        (1..4).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }

        // 4 x (50 + 10 + 5)
        assertEquals(26_000L, viewModel.uiState.value.totalPoolCents)
        assertEquals(26_000L, viewModel.uiState.value.totalPaidInCents)
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
            val cost = requireNotNull(viewModel.uiState.value.pendingAction).buyInCostCents
            viewModel.send(BankIntent.CancelPlayerAction)
            cost
        }

        // P1: 130 + 2 x 50 + 1 x 25; P2: 130 + 50; P3: 130 + 2 x 25
        assertEquals(listOf(25_500L, 18_000L, 18_000L), buyInCosts)
        assertEquals(61_500L, viewModel.uiState.value.totalPoolCents)
    }

    // ---- PP-018: bank data hygiene ------------------------------------------------------------

    @Test
    fun removedPlayersDoNotComeBackAfterARestart() {
        configure(players = 10, buyIn = 20.0, rebuy = 10.0)
        val viewModel = newViewModel()
        viewModel.send(
            BankIntent.PlayerNameChanged(10, "Zed"),
            BankIntent.PlayerRebuyChanged(10, 2),
            BankIntent.BuyInToggled(10)
        )
        assertEquals(2, viewModel.uiState.value.totalRebuyCount)

        // Slide to 9 players and back to 10 on the Tournament tab
        tournamentPreferences.setPlayerCount(9)
        settle()
        tournamentPreferences.setPlayerCount(10)
        settle()
        assertEquals("Player 10", viewModel.player(10).name)

        // "Restart": a fresh ViewModel over the same preferences
        clearViewModels()
        val restarted = newViewModel()
        with(restarted.player(10)) {
            assertEquals("Player 10", name)
            assertEquals(0, rebuys)
            assertFalse(buyIn)
        }
        assertEquals(0, restarted.uiState.value.totalRebuyCount)
        assertEquals(0, bankPreferences.getTotalRebuyCount())
    }

    @Test
    fun knockoutsCreditedToARemovedPlayerBecomeUnclaimed() {
        configure(players = 6, buyIn = 10.0, bounty = 5.0, weights = listOf(1))
        val viewModel = newViewModel()
        viewModel.knockOut(playerId = 3, eliminatorId = 6)
        assertEquals(mapOf(6 to 1), viewModel.uiState.value.knockoutCounts)

        tournamentPreferences.setPlayerCount(5)
        settle()

        assertNull(viewModel.player(3).eliminatedBy)
        assertEquals(emptyMap<Int, Int>(), viewModel.uiState.value.knockoutCounts)
        assertNull(bankPreferences.getPlayerEliminatedBy(3))
    }

    @Test
    fun namesAreSavedAndABlankNameFallsBackToTheDefault() {
        val viewModel = newViewModel()

        viewModel.send(BankIntent.PlayerNameChanged(2, "Ana"))
        assertEquals("Ana", bankPreferences.getPlayerName(2))
        assertEquals("Ana", newViewModel().player(2).name)

        viewModel.send(BankIntent.PlayerNameChanged(2, "  "))
        assertEquals("Player 2", viewModel.player(2).name)
        assertEquals("Player 2", bankPreferences.getPlayerName(2))
    }

    /**
     * Money conservation over a range of tournaments. With every knockout credited and everyone in
     * the money paid: what the Bank paid out = what it took in - the food pool, to the cent, and the
     * players' net pays sum to -food pool.
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
            MoneyCase(
                "prime players", 7, 13.0, food = 7.0, bounty = 3.0, rebuy = 5.0, rebuysEach = 1,
                weights = listOf(4, 3, 2, 1)
            ),
            MoneyCase("equal weights", 4, 25.0, food = 25.0, bounty = 25.0, rebuy = 25.0, weights = listOf(1, 1, 1)),
            MoneyCase("single player", 1, 100.0, food = 50.0, bounty = 25.0, weights = listOf(1)),
            MoneyCase(
                "max purchases", 3, 10.0, food = 5.0, bounty = 2.0, rebuy = 1.0, addOn = 1.0,
                rebuysEach = MAX_PURCHASE_COUNT, addOnsEach = MAX_PURCHASE_COUNT, weights = listOf(2, 1)
            ),
            MoneyCase("bounty only", 3, 0.0, food = 100.0, bounty = 20.0, weights = listOf(2, 1)),
            MoneyCase(
                "odd amounts", 3, 14.14, food = 7.07, bounty = 3.54, rebuy = 1.77, rebuysEach = 1,
                weights = listOf(2, 1)
            )
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

            val perPlayer = Money.centsOf(case.buyIn) + Money.centsOf(case.food) + Money.centsOf(case.bounty) +
                case.rebuysEach * Money.centsOf(case.rebuy) + case.addOnsEach * Money.centsOf(case.addOn)
            val state = viewModel.uiState.value
            assertEquals("${case.name}: total paid in", case.players * perPlayer, state.totalPaidInCents)
            assertEquals("${case.name}: total pool", case.players * perPlayer, state.totalPoolCents)

            val netPays = (1..case.players).map { viewModel.netPayShownFor(it) }
            assertEquals("${case.name}: sum of net pays", -state.pool.foodCents, netPays.sum())

            state.payoutEligiblePlayerIds.forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }
            val paid = viewModel.uiState.value
            assertEquals("${case.name}: paid out", paid.totalPaidInCents - paid.pool.foodCents, paid.totalPaidOutCents)
            assertEquals("${case.name}: paid out", paid.payableCents, paid.totalPaidOutCents)
        }
    }

    /**
     * The conservation property through the ViewModel: random sequences of buy-ins, rebuys,
     * add-ons, knockouts (credited or not), payouts and undos. After every action the Bank has paid
     * out no more than the prize pool + bounty pool; once the tournament is over and everyone owed
     * has been paid, it has paid out exactly that, to the cent. Seeded for reproducible failures.
     */
    @Test
    fun moneyIsConservedThroughRandomBankSessions() {
        val random = Random(4_10_2026)
        repeat(SESSIONS) { session ->
            clearViewModels()
            bankPreferences.resetAllBankData()
            val players = random.nextInt(2, 11)
            configure(
                players = players,
                buyIn = listOf(5.0, 20.0, 14.14, 100.0).random(random),
                food = listOf(0.0, 5.0, 7.07).random(random),
                bounty = listOf(0.0, 2.5, 10.0).random(random),
                rebuy = listOf(0.0, 10.0, 1.77).random(random),
                addOn = listOf(0.0, 15.0).random(random),
                weights = listOf(listOf(1), listOf(3, 2, 1), listOf(35, 20, 15, 10), listOf(50, 30, 20)).random(random)
            )
            val viewModel = newViewModel()
            val context = { "session $session: ${viewModel.uiState.value.eliminationOrder}" }

            repeat(random.nextInt(5, ACTIONS)) {
                randomAction(viewModel, random, players)
                val state = viewModel.uiState.value
                assertTrue(context(), state.totalPaidOutCents <= state.payableCents)
                assertEquals(context(), state.prizePoolCents, state.payoutTable.totalCents)
            }

            // Finish: knock out all but one (some credited, some not), then pay everyone owed
            while (viewModel.uiState.value.activePlayers > 1) {
                val stillIn = viewModel.uiState.value.players.filterNot { it.out }.map { it.id }
                val target = stillIn.random(random)
                viewModel.knockOut(target, (stillIn - target).randomOrNull(random)?.takeIf { random.nextBoolean() })
            }
            val paidEarly = viewModel.uiState.value.players.filter { it.payedOut }.map { it.id }
            paidEarly.forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }
            val netPays = (1..players).sumOf { viewModel.netPayShownFor(it) }
            assertEquals(context(), -viewModel.uiState.value.pool.foodCents, netPays)

            viewModel.uiState.value.payoutEligiblePlayerIds.forEach { viewModel.send(BankIntent.PayedOutToggled(it)) }
            val done = viewModel.uiState.value
            assertEquals(context(), done.payableCents, done.totalPaidOutCents)
        }
    }

    private fun randomAction(viewModel: BankViewModel, random: Random, players: Int) {
        val id = random.nextInt(1, players + 1)
        val stillIn = viewModel.uiState.value.players.filterNot { it.out }.map { it.id }
        when (random.nextInt(7)) {
            0 -> viewModel.send(BankIntent.BuyInToggled(id))
            1 -> viewModel.send(BankIntent.PlayerRebuyChanged(id, random.nextInt(0, 4)))
            2 -> viewModel.send(BankIntent.PlayerAddonChanged(id, random.nextInt(0, 3)))
            3 -> if (id in stillIn && stillIn.size > 1) {
                viewModel.knockOut(id, (stillIn - id).randomOrNull(random)?.takeIf { random.nextBoolean() })
            }
            4 -> if (id !in stillIn) viewModel.send(BankIntent.OutToggled(id)) // back in
            else -> viewModel.send(BankIntent.PayedOutToggled(id))
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

    private companion object {
        const val SESSIONS = 60
        const val ACTIONS = 30
    }
}
