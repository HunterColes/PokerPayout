package com.huntercoles.pokerpayout.bank.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PP-085: each rebuy and add-on keeps the price it was bought at. Changing the rebuy amount
 * mid-game used to re-value every rebuy already recorded (Paid In went from $35 to $40 after $10
 * became $15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankPricesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
        kit.configure(players = 3, buyIn = 25.0, rebuy = 10.0, addOn = 5.0, weights = listOf(2, 1))
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun changingTheRebuyAmountDoesNotRevalueRebuysAlreadyBought() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.AddPurchase(1, Purchase.REBUY))
        assertEquals(3_500L, viewModel.uiState.value.totalPaidInCents) // $25 + one $10 rebuy

        tournamentPreferences.setRebuyAmount(15.0)
        settle()

        with(viewModel.uiState.value) {
            assertEquals("the $10 rebuy stays $10", 3_500L, totalPaidInCents)
            assertEquals(1_000L, pool.rebuyCents)
            assertEquals(7_500L + 1_000L, prizePoolCents)
        }

        // The next one costs the new amount
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY))
        assertEquals(listOf(1_000L, 1_500L), viewModel.player(1).rebuyPrices)
        assertEquals(2_500L, viewModel.uiState.value.pool.rebuyCents)
        assertEquals(listOf(1_000L, 1_500L), bankPreferences.getPlayerRebuyPrices(1))
    }

    @Test
    fun addOnsKeepTheirPriceToo() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.AddPurchase(2, Purchase.ADD_ON))
        tournamentPreferences.setAddOnAmount(20.0)
        settle()
        viewModel.send(BankIntent.AddPurchase(3, Purchase.ADD_ON))

        assertEquals(500L + 2_000L, viewModel.uiState.value.pool.addOnCents)
        assertEquals(500L, viewModel.payOutSheet(2).owed.addOnCostCents)
        assertEquals(2_000L, viewModel.payOutSheet(3).owed.addOnCostCents)
    }

    @Test
    fun theCountSheetRemovesTheNewestAndAddsAtTodaysPrice() = with(kit) {
        val viewModel = newViewModel()
        viewModel.setRebuys(1, 2) // 2 x $10
        tournamentPreferences.setRebuyAmount(12.5)
        settle()

        viewModel.send(BankIntent.OpenCount(1, Purchase.REBUY))
        val sheet = viewModel.uiState.value.sheet as BankSheet.Count
        assertEquals(listOf(1_000L, 1_000L), sheet.prices)
        assertEquals(1_250L, sheet.priceCents)

        viewModel.setRebuys(1, 4)
        assertEquals(listOf(1_000L, 1_000L, 1_250L, 1_250L), viewModel.player(1).rebuyPrices)

        viewModel.setRebuys(1, 3)
        assertEquals("the newest goes first", listOf(1_000L, 1_000L, 1_250L), viewModel.player(1).rebuyPrices)
    }

    @Test
    fun theWinnerAndThePayoutTableUseTheRecordedPrices() = with(kit) {
        val viewModel = newViewModel()
        (1..3).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY)) // $10
        tournamentPreferences.setRebuyAmount(30.0)
        settle()
        viewModel.send(BankIntent.AddPurchase(2, Purchase.REBUY)) // $30
        viewModel.knockOut(3, 1)
        viewModel.knockOut(2, 1)

        // Prize pool 3 x 25 + 10 + 30 = 115; 2:1 -> 2nd $38, 1st $77
        with(viewModel.uiState.value) {
            assertEquals(11_500L, payoutTable.prizePoolCents)
            assertEquals(listOf(7_700L, 3_800L), payoutTable.places.map { it.amountCents })
        }
        assertEquals(7_700L - 3_500L, viewModel.netPayShownFor(1))
        assertEquals(3_800L - 5_500L, viewModel.netPayShownFor(2))
        assertEquals(-2_500L, viewModel.netPayShownFor(3))
    }

    @Test
    fun theRecordedPricesSurviveARestart() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY))
        tournamentPreferences.setRebuyAmount(50.0)
        settle()
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY))

        clear()
        val restarted = newViewModel()

        assertEquals(listOf(1_000L, 5_000L), restarted.player(1).rebuyPrices)
        assertEquals(6_000L, restarted.uiState.value.pool.rebuyCents)
    }
}
