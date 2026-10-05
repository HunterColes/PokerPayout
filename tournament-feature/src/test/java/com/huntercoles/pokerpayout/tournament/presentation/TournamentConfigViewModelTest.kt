package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TournamentConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settle() = testDispatcher.scheduler.advanceUntilIdle()

    private fun createViewModel() = TournamentConfigViewModel(
        calculatePayoutsUseCase = CalculatePayoutsUseCase(),
        tournamentPreferences = tournamentPreferences,
        timerPreferences = timerPreferences,
        bankPreferences = bankPreferences
    ).also { settle() }

    private fun TournamentConfigViewModel.send(vararg intents: TournamentConfigIntent) {
        intents.forEach { acceptIntent(it) }
        settle()
    }

    private val TournamentConfigViewModel.state get() = uiState.value

    @Test
    fun purchaseTotalsInfluenceSummaryAndPayouts() {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(0.0)
        tournamentPreferences.setRebuyAmount(100.0)
        tournamentPreferences.setAddOnAmount(50.0)
        val viewModel = createViewModel()

        // Recorded on the Bank tab
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        bankPreferences.savePlayerRebuys(playerId = 2, rebuys = 1)
        bankPreferences.savePlayerAddons(playerId = 1, addons = 1)
        bankPreferences.savePlayerAddons(playerId = 3, addons = 2)
        settle()

        val state = viewModel.state
        assertEquals(3, state.rebuyPurchases)
        assertEquals(3, state.addOnPurchases)
        // 4 x 100 buy-ins + 3 x 100 rebuys + 3 x 50 add-ons, all to the single default place
        assertEquals(85_000L, state.pool.prizePoolCents)
        assertEquals(listOf(85_000L), state.payoutTable.places.map { it.amountCents })
    }

    @Test
    fun purchasesSurviveTheAmountBeingClearedAndRetyped() {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setBuyIn(50.0)
        tournamentPreferences.setRebuyAmount(25.0)
        tournamentPreferences.setAddOnAmount(10.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 1)
        bankPreferences.savePlayerAddons(playerId = 2, addons = 2)
        settle()

        // The user clears each field and types the amount again
        viewModel.send(
            TournamentConfigIntent.UpdateRebuyAmount(0),
            TournamentConfigIntent.UpdateAddOnAmount(0),
            TournamentConfigIntent.UpdateRebuyAmount(2_500),
            TournamentConfigIntent.UpdateAddOnAmount(1_000)
        )

        val state = viewModel.state
        assertEquals(1, state.rebuyPurchases)
        assertEquals(2, state.addOnPurchases)
        assertEquals(1, bankPreferences.getPlayerRebuys(1))
        assertEquals(2, bankPreferences.getPlayerAddons(2))
        assertNull(state.purchaseClearPrompt)
        // 3 x 50 + 25 + 2 x 10
        assertEquals(19_500L, state.pool.prizePoolCents)
    }

    @Test
    fun aZeroWhileTypingIsNotSavedWhenPurchasesExist() {
        tournamentPreferences.setRebuyAmount(25.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        settle()

        viewModel.send(TournamentConfigIntent.UpdateRebuyAmount(0))

        assertEquals(2_500L, tournamentPreferences.getMoneySettings().rebuyCents)
        assertEquals(2, viewModel.state.rebuyPurchases)
    }

    @Test
    fun leavingTheFieldAtZeroAsksBeforeClearingPurchases() {
        tournamentPreferences.setRebuyAmount(25.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        bankPreferences.savePlayerRebuys(playerId = 3, rebuys = 1)
        settle()

        viewModel.send(TournamentConfigIntent.CommitRebuyAmount(0, centsBeforeEdit = 2_500))

        val prompt = viewModel.state.purchaseClearPrompt
        assertEquals(PurchaseClearPrompt(PurchaseKind.REBUY, count = 3, keptAmountCents = 2_500), prompt)
        // Nothing is cleared or saved until the user answers
        assertEquals(3, bankPreferences.getTotalRebuyCount())
        assertEquals(2_500L, tournamentPreferences.getMoneySettings().rebuyCents)
    }

    @Test
    fun keepingPurchasesLeavesTheAmountAndTheRebuys() {
        tournamentPreferences.setAddOnAmount(10.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerAddons(playerId = 2, addons = 1)
        settle()

        viewModel.send(
            TournamentConfigIntent.CommitAddOnAmount(0, centsBeforeEdit = 1_000),
            TournamentConfigIntent.DismissClearPurchases
        )

        assertNull(viewModel.state.purchaseClearPrompt)
        assertEquals(1_000L, tournamentPreferences.getMoneySettings().addOnCents)
        assertEquals(1, bankPreferences.getPlayerAddons(2))
    }

    @Test
    fun keepingPutsBackTheAmountFromBeforeTheEdit() {
        tournamentPreferences.setRebuyAmount(15.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 1)
        settle()

        // Backspacing "15" passes through "1", which is a valid amount and is saved on the way
        viewModel.send(
            TournamentConfigIntent.UpdateRebuyAmount(100),
            TournamentConfigIntent.CommitRebuyAmount(0, centsBeforeEdit = 1_500)
        )
        assertEquals(1_500L, viewModel.state.purchaseClearPrompt?.keptAmountCents)

        viewModel.send(TournamentConfigIntent.DismissClearPurchases)

        assertEquals(1_500L, tournamentPreferences.getMoneySettings().rebuyCents)
        assertEquals(1, bankPreferences.getPlayerRebuys(1))
    }

    @Test
    fun confirmingTheZeroClearsThePurchasesAndTheAmount() {
        tournamentPreferences.setRebuyAmount(25.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        settle()

        viewModel.send(
            TournamentConfigIntent.CommitRebuyAmount(0, centsBeforeEdit = 2_500),
            TournamentConfigIntent.ConfirmClearPurchases
        )

        assertNull(viewModel.state.purchaseClearPrompt)
        assertEquals(0L, tournamentPreferences.getMoneySettings().rebuyCents)
        assertEquals(0, bankPreferences.getPlayerRebuys(1))
        assertEquals(0, viewModel.state.rebuyPurchases)
    }

    @Test
    fun withoutPurchasesAZeroIsSavedStraightAway() {
        tournamentPreferences.setRebuyAmount(25.0)
        val viewModel = createViewModel()

        viewModel.send(
            TournamentConfigIntent.UpdateRebuyAmount(0),
            TournamentConfigIntent.CommitRebuyAmount(0, centsBeforeEdit = 2_500)
        )

        assertEquals(0L, tournamentPreferences.getMoneySettings().rebuyCents)
        assertNull(viewModel.state.purchaseClearPrompt)
    }

    @Test
    fun resetClearsRecordedPurchasesTheDialogMentioned() {
        tournamentPreferences.setRebuyAmount(25.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        settle()

        viewModel.send(TournamentConfigIntent.ConfirmReset)

        assertEquals(0, bankPreferences.getTotalRebuyCount())
        assertEquals(0L, tournamentPreferences.getMoneySettings().rebuyCents)
    }

    @Test
    fun moneyIsKeptInCents() {
        val viewModel = createViewModel()

        viewModel.send(
            TournamentConfigIntent.UpdateBuyIn(12_345_678),
            TournamentConfigIntent.UpdateFoodPerPlayer(1_250),
            TournamentConfigIntent.UpdateBountyPerPlayer(5)
        )

        // $123,456.78 was stored as 123456.78125 in a Float before v1.2.0
        val money = tournamentPreferences.getMoneySettings()
        assertEquals(12_345_678L, money.buyInCents)
        assertEquals(1_250L, money.foodCents)
        assertEquals(5L, money.bountyCents)
        assertEquals(5 * 12_345_678L, viewModel.state.pool.prizePoolCents)
    }

    @Test
    fun thePayoutTableIsRoundedAndAddsUpToThePrizePool() {
        tournamentPreferences.setPlayerCount(10)
        tournamentPreferences.setBuyIn(25.0)
        val viewModel = createViewModel()

        // 10 x $25 = $250, standard 3 places 35/20/15 -> 2nd 71.43 -> $71, 3rd 53.57 -> $54
        assertEquals(listOf(12_500L, 7_100L, 5_400L), viewModel.state.payoutTable.places.map { it.amountCents })
        assertEquals(25_000L, viewModel.state.payoutTable.totalCents)
        assertEquals(PayoutPreset.STANDARD, viewModel.state.payoutPreset)

        viewModel.send(TournamentConfigIntent.UpdatePayoutRounding(PayoutRounding.FIVE_DOLLARS))
        assertEquals(listOf(12_500L, 7_000L, 5_500L), viewModel.state.payoutTable.places.map { it.amountCents })
        assertEquals(PayoutRounding.FIVE_DOLLARS, tournamentPreferences.getPayoutRounding())
    }

    @Test
    fun presetsApplyToTheCurrentNumberOfPlacesAndPersist() {
        tournamentPreferences.setPlayerCount(10)
        tournamentPreferences.setBuyIn(20.0)
        val viewModel = createViewModel()

        viewModel.send(TournamentConfigIntent.ApplyPayoutPreset(PayoutPreset.TOP_HEAVY))

        assertEquals(PayoutPreset.TOP_HEAVY, viewModel.state.payoutPreset)
        assertEquals(listOf(60, 30, 10), tournamentPreferences.getPayoutWeights())
        assertEquals(listOf(12_000L, 6_000L, 2_000L), viewModel.state.payoutTable.places.map { it.amountCents })

        viewModel.send(TournamentConfigIntent.SetPaidPlaces(4))
        assertEquals(listOf(55, 25, 13, 7), tournamentPreferences.getPayoutWeights())
        assertEquals(PayoutPreset.TOP_HEAVY, createViewModel().state.payoutPreset)
    }

    @Test
    fun paidPlacesNeverExceedThePlayers() {
        tournamentPreferences.setPlayerCount(3)
        val viewModel = createViewModel()

        viewModel.send(TournamentConfigIntent.SetPaidPlaces(9))

        assertEquals(3, viewModel.state.paidPlaces)
        assertEquals(1, viewModel.state.recommendedPlaces)
    }

    @Test
    fun theBankWeightsEditorUpdatesTheTournamentTable() {
        val viewModel = createViewModel()

        // Saved from the Bank tab while this ViewModel is alive
        tournamentPreferences.setPayoutWeights(listOf(3, 2))
        settle()

        assertEquals(2, viewModel.state.paidPlaces)
    }

    @Test
    fun placeNamesFollowTheEliminationOrderFromTheBottom() {
        tournamentPreferences.setPlayerCount(5)
        tournamentPreferences.setPayoutWeights(listOf(3, 2, 1))
        bankPreferences.savePlayerName(2, "Ana")
        bankPreferences.savePlayerName(4, "Bo")
        val viewModel = createViewModel()

        // Two players out: Bo first (5th), then Ana (4th). Places 1-3 are undecided. v1.1.12
        // labelled Ana "1st" here (B21).
        bankPreferences.saveEliminationOrder(listOf(4, 2))
        settle()
        assertTrue(viewModel.state.placeNames.isEmpty())

        bankPreferences.saveEliminationOrder(listOf(4, 2, 1))
        settle()
        assertEquals(mapOf(3 to "Player 1"), viewModel.state.placeNames)

        bankPreferences.saveEliminationOrder(listOf(4, 2, 1, 5))
        settle()
        assertEquals(mapOf(3 to "Player 1", 2 to "Player 5", 1 to "Player 3"), viewModel.state.placeNames)
    }

    @Test
    fun shrinkingThePlayerCountForgetsRemovedPlayersInTheBank() {
        tournamentPreferences.setPlayerCount(10)
        tournamentPreferences.setRebuyAmount(10.0)
        bankPreferences.savePlayerName(10, "Zed")
        bankPreferences.savePlayerRebuys(10, 2)
        val viewModel = createViewModel()
        assertEquals(2, viewModel.state.rebuyPurchases)

        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(9), TournamentConfigIntent.UpdatePlayerCount(10))

        assertEquals(0, viewModel.state.rebuyPurchases)
        assertEquals(0, bankPreferences.getPlayerRebuys(10))
        assertEquals("Player 10", bankPreferences.getPlayerName(10))
    }
}
