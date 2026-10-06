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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Feature parity with the Bank before the makeover: one test per row of design spec section 10
 * ("Bank feature inventory, today -> S5 v2"), through the ViewModel. The owner: "We don't want to get
 * rid of any of the working good stuff." The screen-level checks (taps, sheets, labels) are in
 * `BankContentTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankParityTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
        // The mockups' game: 9 players, $40 buy-in, $5 food, $5 bounty, $40 rebuy, $10 add-on
        kit.configure(players = 9, buyIn = 40.0, food = 5.0, bounty = 5.0, rebuy = 40.0, addOn = 10.0)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun row01_oneRowPerPlayerFollowingTheTournamentPlayerCountLive() = with(kit) {
        val viewModel = newViewModel()
        assertEquals(9, viewModel.uiState.value.rows.size)
        tournamentPreferences.setPlayerCount(11)
        settle()
        assertEquals((1..11).toList(), viewModel.uiState.value.rows.map { it.playerId })
    }

    @Test
    fun row02_renameInPlaceIsSavedAndADefaultNameComesBackWhenBlank() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerNameChanged(1, "Dana"))
        assertEquals("Dana", viewModel.row(1).name)
        assertEquals("Dana", bankPreferences.getPlayerName(1))
        viewModel.send(BankIntent.PlayerNameChanged(1, ""))
        assertEquals("Player 1", viewModel.row(1).name)
    }

    @Test
    fun row03_buyInAppliesAtOnceWithTheAmountOnTheUndoSnackbar() = with(kit) {
        val viewModel = newViewModel()
        viewModel.act(BankIntent.BuyInToggled(1))
        assertEquals("Player 1 paid the buy-in · $50", snackbarMessage())
        assertEquals(CellStatus.Done, viewModel.row(1).buyIn.status)
        settle()
        viewModel.send(BankIntent.Undo)
        assertEquals(CellStatus.Open, viewModel.row(1).buyIn.status)
    }

    @Test
    fun row04_rebuyTapAddsOneHoldSetsACountBadgeFromTwoHiddenAtZeroWithACutoff() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.AddPurchase(2, Purchase.REBUY))
        assertEquals(CellStatus.Done, viewModel.row(2).rebuy.status)
        assertEquals(1, viewModel.row(2).rebuy.count)
        viewModel.send(BankIntent.OpenCount(2, Purchase.REBUY))
        assertTrue(viewModel.uiState.value.sheet is BankSheet.Count)
        viewModel.setRebuys(2, 3)
        assertEquals(3, viewModel.row(2).rebuy.count) // the cell draws a "3" badge from 2
        // New: the cutoff
        tournamentPreferences.setRebuyUntilLevel(4)
        clock.at(level = 5)
        settle()
        assertFalse(viewModel.uiState.value.rebuyWindow.isOpen)
        // Column hidden at $0 (the cells go with it; see BankLayout)
        tournamentPreferences.setRebuyAmount(0.0)
        bankPreferences.clearAllRebuys()
        settle()
        assertFalse(viewModel.uiState.value.isRebuyEnabled)
    }

    @Test
    fun row05_addOnTheSameWithItsOwnCutoffAtTheEndOfTheBreakAfterTheRebuys() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.AddPurchase(3, Purchase.ADD_ON))
        viewModel.setAddOns(3, 2)
        assertEquals(2, viewModel.row(3).addOn.count)
        tournamentPreferences.setRebuyUntilLevel(4)
        clock.at(level = 4, onBreak = true, breaks = listOf(4, 8))
        settle()
        assertTrue(viewModel.uiState.value.addOnWindow.isOpen)
        clock.at(level = 5, breaks = listOf(4, 8))
        settle()
        assertEquals(PurchaseWindow.ClosedAfterBreak(1), viewModel.uiState.value.addOnWindow)
    }

    @Test
    fun row06_outOpensTheKnockoutSheetWithEveryoneAndNobodyAndTheChampionCantBeKnockedOut() = with(kit) {
        tournamentPreferences.setPlayerCount(3)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.OpenKnockout(3))
        val sheet = viewModel.uiState.value.sheet as BankSheet.Knockout
        assertEquals(listOf(1, 2), sheet.candidates.map { it.playerId })
        viewModel.knockOut(3, null) // "Nobody"
        assertNull(viewModel.player(3).eliminatedBy)
        // Bring back: tap the place disc
        assertEquals(CellStatus.OutPlace, viewModel.row(3).out.status)
        viewModel.send(BankIntent.BringBack(3))
        assertFalse(viewModel.player(3).out)
        viewModel.knockOut(3, 1)
        viewModel.knockOut(2, 1)
        assertEquals(CellStatus.Champion, viewModel.row(1).out.status)
        assertFalse(viewModel.row(1).out.enabled)
    }

    @Test
    fun row07_knockoutCountsShowOnTheEliminatorsRow() = with(kit) {
        val viewModel = newViewModel()
        viewModel.knockOut(9, 1)
        viewModel.knockOut(8, 1)
        assertEquals(2, viewModel.row(1).knockouts)
    }

    @Test
    fun row08_theFinishingPlaceIsInTheOutCellWithWhoAndWhenUnderTheName() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerNameChanged(2, "Marcus"))
        clock.at(level = 5)
        settle()
        viewModel.knockOut(8, 2)
        with(viewModel.row(8)) {
            assertEquals(9, out.place)
            assertEquals(9, place)
            assertEquals("Marcus", knockedOutByName)
            assertEquals(5, outAtLevel)
        }
    }

    @Test
    fun row09_activeFirstThenOutPlayersLatestFirstInSections() = with(kit) {
        val viewModel = newViewModel()
        viewModel.knockOut(9, null)
        viewModel.knockOut(2, null)
        val rows = viewModel.uiState.value.rows
        assertEquals(listOf(1, 3, 4, 5, 6, 7, 8, 2, 9), rows.map { it.playerId })
        assertEquals(List(7) { BankSection.PLAYING } + List(2) { BankSection.OUT }, rows.map { it.section })
    }

    @Test
    fun row10_aKnockedOutRowMovesToTheOutSectionWithoutAFlash() = with(kit) {
        val viewModel = newViewModel()
        viewModel.knockOut(4, null)
        // No transient "knocking out" state any more: the row is simply in Out (DangerWash)
        assertEquals(BankSection.OUT, viewModel.row(4).section)
        assertEquals(viewModel.uiState.value.rows.last().playerId, 4)
    }

    @Test
    fun row11_theChampionHasItsOwnSectionCrownAndFirstPlace() = with(kit) {
        tournamentPreferences.setPlayerCount(2)
        val viewModel = newViewModel()
        viewModel.knockOut(2, 1)
        with(viewModel.uiState.value.rows.first()) {
            assertEquals(1, playerId)
            assertEquals(BankSection.CHAMPION, section)
            assertEquals(1, place)
            assertEquals(CellStatus.Champion, out.status)
        }
    }

    @Test
    fun row12_paidShowsATickOrTheAmountOwedAndOpensTheBreakdown() = with(kit) {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setPayoutWeights(listOf(2, 1))
        val viewModel = newViewModel()
        assertEquals(CellStatus.Muted, viewModel.row(1).paid.status)
        viewModel.knockOut(3, 1)
        viewModel.knockOut(2, 1)
        val champion = viewModel.row(1).paid
        assertEquals(CellStatus.Owed, champion.status)
        val sheet = viewModel.payOutSheet(1)
        assertEquals(champion.amountCents, sheet.owed.winningsCents)
        // Prize + 2 knockout bounties + King's Bounty
        assertEquals(sheet.owed.prizeCents + 1_000L + 500L, sheet.owed.winningsCents)
        viewModel.send(BankIntent.SetPaid(1, true))
        assertEquals(CellStatus.Paid, viewModel.row(1).paid.status)
    }

    @Test
    fun row13_collectedAndPaidOutMetersCompleteToTheCent() = with(kit) {
        tournamentPreferences.setPlayerCount(2)
        val viewModel = newViewModel()
        (1..2).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        with(viewModel.uiState.value) { assertEquals(totalPoolCents, totalPaidInCents) }
        viewModel.knockOut(2, 1)
        viewModel.send(BankIntent.SetPaid(1, true))
        with(viewModel.uiState.value) { assertEquals(payableCents, totalPaidOutCents) }
    }

    @Test
    fun row14_theBreakdownSheetHasTheWholePoolAndThePayoutTable() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.AddPurchase(1, Purchase.REBUY), BankIntent.AddPurchase(2, Purchase.ADD_ON))
        viewModel.send(BankIntent.ShowPoolBreakdown)
        assertEquals(BankSheet.PoolBreakdown, viewModel.uiState.value.sheet)
        with(viewModel.uiState.value) {
            assertEquals(36_000L + 4_000L + 1_000L, pool.prizePoolCents)
            assertEquals(4_500L, pool.foodCents)
            assertEquals(4_500L, pool.bountyCents)
            assertEquals(pool.prizePoolCents, payoutTable.totalCents)
        }
    }

    @Test
    fun row15_thePayoutStructureOpensFromTheBankAndIsLockedWhileTheClockRuns() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.ShowPayoutStructure)
        assertEquals(BankSheet.PayoutStructure, viewModel.uiState.value.sheet)
        timerPreferences.setTimerRunning(true)
        settle()
        assertTrue(viewModel.uiState.value.isTimerRunning)
    }

    @Test
    fun row16_resetAsksFirstAndDoesNothingWhenThereIsNothingToReset() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.ShowResetConfirm)
        assertNull(viewModel.uiState.value.sheet)
        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.ShowResetConfirm)
        assertEquals(BankSheet.ResetConfirm(9), viewModel.uiState.value.sheet)
        viewModel.send(BankIntent.ConfirmReset)
        assertFalse(viewModel.player(1).buyIn)
    }

    @Test
    fun row17_newUndoForEveryActionAndTheCutoffsInState() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.BuyInToggled(1))
        assertTrue(viewModel.uiState.value.canUndo)
        tournamentPreferences.setRebuyUntilLevel(4)
        settle()
        assertEquals(PurchaseWindow.OpenUntilLevel(4), viewModel.uiState.value.rebuyWindow)
    }
}
