package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.PurchaseWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rebuy and add-on cutoffs in the Bank (PP-030), at their boundaries: "rebuys until level 4"
 * with the clock's level and breaks coming from the clock. Rebuys close when level 4 ends; add-ons
 * at the end of the first break after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankGatingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
        kit.configure(players = 9, buyIn = 40.0, rebuy = 40.0, addOn = 10.0)
        kit.tournamentPreferences.setRebuyUntilLevel(4)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    private val breaksEvery4 = listOf(4, 8)

    @Test
    fun beforeTheClockStartsEverythingIsOpen() = with(kit) {
        val viewModel = newViewModel()
        assertEquals(PurchaseWindow.OpenUntilLevel(4), viewModel.uiState.value.rebuyWindow)
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY), BankIntent.AddPurchase(1, Purchase.ADD_ON))
        assertEquals(1, viewModel.player(1).rebuys)
        assertEquals(1, viewModel.player(1).addons)
    }

    @Test
    fun rebuysStayOpenThroughTheLastMinuteOfTheCutoffLevel() = with(kit) {
        val viewModel = newViewModel()
        clock.at(level = 4, breaks = breaksEvery4)
        settle()

        assertTrue(viewModel.uiState.value.rebuyWindow.isOpen)
        assertEquals(CellStatus.Open, viewModel.row(1).rebuy.status)
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY))
        assertEquals(1, viewModel.player(1).rebuys)
    }

    @Test
    fun rebuysCloseWhenTheCutoffLevelEndsAndTheBreakStarts() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.AddPurchase(2, Purchase.REBUY))
        clock.at(level = 4, onBreak = true, breaks = breaksEvery4)
        settle()

        with(viewModel.uiState.value) {
            assertEquals(PurchaseWindow.ClosedAfterLevel(4), rebuyWindow)
            // The break after the cutoff is when add-ons are sold
            assertEquals(PurchaseWindow.OpenUntilBreak(number = 1, afterLevel = 4), addOnWindow)
        }
        // A tap does nothing now: no rebuy is recorded
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY))
        assertEquals(0, viewModel.player(1).rebuys)
        // Taken ones stay filled; untaken ones show the closed dot
        assertEquals(CellStatus.Done, viewModel.row(2).rebuy.status)
        assertFalse(viewModel.row(2).rebuy.enabled)
        assertTrue(viewModel.row(2).rebuy.holdable)
        assertEquals(CellStatus.ClosedNotTaken, viewModel.row(1).rebuy.status)
        assertFalse(viewModel.row(1).rebuy.enabled)
        // Add-ons still go in during the break
        viewModel.send(BankIntent.AddPurchase(1, Purchase.ADD_ON))
        assertEquals(1, viewModel.player(1).addons)
    }

    @Test
    fun addOnsCloseWhenTheBreakAfterTheCutoffEnds() = with(kit) {
        val viewModel = newViewModel()
        clock.at(level = 5, breaks = breaksEvery4)
        settle()

        with(viewModel.uiState.value) {
            assertEquals(PurchaseWindow.ClosedAfterLevel(4), rebuyWindow)
            assertEquals(PurchaseWindow.ClosedAfterBreak(1), addOnWindow)
        }
        viewModel.send(BankIntent.AddPurchase(1, Purchase.ADD_ON))
        assertEquals(0, viewModel.player(1).addons)
    }

    @Test
    fun theAddOnBreakIsTheFirstOneAfterTheCutoffNotTheFirstOneOverall() = with(kit) {
        // Breaks after levels 3 and 6: the cutoff at 4 is between them, so add-ons run to break 2
        val viewModel = newViewModel()
        clock.at(level = 6, onBreak = true, breaks = listOf(3, 6))
        settle()
        assertEquals(PurchaseWindow.OpenUntilBreak(number = 2, afterLevel = 6), viewModel.uiState.value.addOnWindow)

        clock.at(level = 7, breaks = listOf(3, 6))
        settle()
        assertEquals(PurchaseWindow.ClosedAfterBreak(2), viewModel.uiState.value.addOnWindow)
    }

    @Test
    fun withNoBreakAfterTheCutoffAddOnsCloseWithTheRebuys() = with(kit) {
        val viewModel = newViewModel()
        clock.at(level = 4)
        settle()
        assertEquals(PurchaseWindow.OpenUntilLevel(4), viewModel.uiState.value.addOnWindow)

        clock.at(level = 5)
        settle()
        assertEquals(PurchaseWindow.ClosedAfterLevel(4), viewModel.uiState.value.addOnWindow)
    }

    @Test
    fun noCutoffMeansOpenAllNight() = with(kit) {
        tournamentPreferences.setRebuyUntilLevel(0)
        val viewModel = newViewModel()
        clock.at(level = 12, breaks = breaksEvery4)
        settle()

        assertEquals(PurchaseWindow.NoCutoff, viewModel.uiState.value.rebuyWindow)
        assertEquals(PurchaseWindow.NoCutoff, viewModel.uiState.value.addOnWindow)
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY))
        assertEquals(1, viewModel.player(1).rebuys)
    }

    @Test
    fun aFinishedClockClosesBoth() = with(kit) {
        val viewModel = newViewModel()
        clock.at(level = 3, finished = true)
        settle()
        assertFalse(viewModel.uiState.value.rebuyWindow.isOpen)
        assertFalse(viewModel.uiState.value.addOnWindow.isOpen)
    }

    @Test
    fun movingTheCutoffOrTheClockBackReopens() = with(kit) {
        val viewModel = newViewModel()
        clock.at(level = 6)
        settle()
        assertFalse(viewModel.uiState.value.rebuyWindow.isOpen)

        tournamentPreferences.setRebuyUntilLevel(6)
        settle()
        assertTrue(viewModel.uiState.value.rebuyWindow.isOpen)

        tournamentPreferences.setRebuyUntilLevel(4)
        clock.at(level = 2)
        settle()
        assertTrue(viewModel.uiState.value.rebuyWindow.isOpen)
    }

    @Test
    fun afterTheCutoffTheCountSheetCanOnlyGoDown() = with(kit) {
        val viewModel = newViewModel()
        viewModel.setRebuys(1, 2)
        clock.at(level = 5)
        settle()

        viewModel.send(BankIntent.OpenCount(1, Purchase.REBUY))
        val sheet = viewModel.uiState.value.sheet as BankSheet.Count
        assertEquals(2, sheet.maxCount)
        assertEquals(PurchaseWindow.ClosedAfterLevel(4), sheet.window)

        viewModel.setRebuys(1, 5)
        assertEquals("can't add after the cutoff", 2, viewModel.player(1).rebuys)
        viewModel.setRebuys(1, 1)
        assertEquals("can still remove one recorded by mistake", 1, viewModel.player(1).rebuys)
    }

    @Test
    fun undoCanStillBringBackARebuyAfterTheCutoff() = with(kit) {
        val viewModel = newViewModel()
        viewModel.setRebuys(1, 1)
        clock.at(level = 5)
        settle()
        viewModel.setRebuys(1, 0)

        viewModel.send(BankIntent.Undo)

        assertEquals(1, viewModel.player(1).rebuys)
    }

    @Test
    fun aKnockoutRemembersTheLevelItHappenedAt() = with(kit) {
        val viewModel = newViewModel()
        viewModel.knockOut(9, null)
        assertEquals("no level before the clock starts", null, viewModel.row(9).outAtLevel)

        clock.at(level = 5)
        settle()
        viewModel.knockOut(8, 1)
        assertEquals(5, viewModel.row(8).outAtLevel)
        assertEquals(5, bankPreferences.getPlayerOutLevel(8))

        viewModel.send(BankIntent.BringBack(8))
        assertEquals(null, bankPreferences.getPlayerOutLevel(8))
    }
}
