package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.EntryPrice
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
 * Late entries and re-entries in the Bank (PP-116), through the real ViewModel on real preferences:
 * the late entry cutoff, a late arrival at today's price, a re-entry after a knockout (the bust
 * stays), places and payouts that count entries, every bounty mode, Undo, process death, and the
 * settle-up counting each player once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankLateEntryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    /** $20 buy-in, $5 food, $5 bounty: $30 to sit down. */
    private val entry = EntryPrice(buyInCents = 2_000L, foodCents = 500L, bountyCents = 500L)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
        kit.configure(players = 6, buyIn = 20.0, food = 5.0, bounty = 5.0, weights = listOf(3, 2, 1))
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    /** Six players named, all bought in, with the clock at [level] (breaks after 4 and 8). */
    private fun BankTestKit.night(level: Int = 2): BankViewModel {
        val viewModel = newViewModel()
        NAMES.forEachIndexed { index, name -> viewModel.send(BankIntent.PlayerNameChanged(index + 1, name)) }
        (1..NAMES.size).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }
        clock.at(level = level, breaks = listOf(4, 8))
        settle()
        return viewModel
    }

    private fun BankViewModel.state() = uiState.value

    // The cutoff --------------------------------------------------------------------------------------

    @Test
    fun lateEntryWaitsForTheClock() = with(kit) {
        val viewModel = newViewModel()
        assertFalse(viewModel.state().canTakeLateEntry)
        viewModel.send(BankIntent.OpenLateEntry)
        assertNull(viewModel.state().sheet)
        viewModel.send(BankIntent.AddLateEntry("Kai"))
        assertEquals(6, viewModel.state().players.size)
        assertEquals(6, tournamentPreferences.getPlayerCount())
    }

    @Test
    fun lateEntryIsOpenThroughTheCutoffLevelAndClosesWhenItEnds() = with(kit) {
        tournamentPreferences.setLateEntryUntilLevel(3)
        val viewModel = night(level = 3)
        assertEquals(PurchaseWindow.OpenUntilLevel(3), viewModel.state().lateEntryWindow)
        viewModel.send(BankIntent.OpenLateEntry)
        val sheet = viewModel.state().sheet as BankSheet.LateEntry
        assertEquals(entry, sheet.price)
        assertEquals(PurchaseWindow.OpenUntilLevel(3), sheet.window)
        viewModel.send(BankIntent.DismissSheet)

        clock.at(level = 4, breaks = listOf(4, 8))
        settle()
        assertEquals(PurchaseWindow.ClosedAfterLevel(3), viewModel.state().lateEntryWindow)
        assertFalse(viewModel.state().canTakeLateEntry)
        viewModel.send(BankIntent.OpenLateEntry)
        assertNull("no sheet after the cutoff", viewModel.state().sheet)
        viewModel.send(BankIntent.AddLateEntry("Kai"))
        assertEquals("no entry after the cutoff", 6, viewModel.state().players.size)

        // The host moves the cutoff: open again
        tournamentPreferences.setLateEntryUntilLevel(5)
        settle()
        assertTrue(viewModel.state().canTakeLateEntry)
    }

    @Test
    fun withNoCutoffLateEntryIsOpenAllNightUntilTheClockFinishesOrSomeoneWins() = with(kit) {
        val viewModel = night(level = 12)
        assertEquals(PurchaseWindow.NoCutoff, viewModel.state().lateEntryWindow)
        assertTrue(viewModel.state().canTakeLateEntry)
        clock.at(level = 12, finished = true)
        settle()
        assertFalse("a finished clock takes no entries", viewModel.state().canTakeLateEntry)
        clock.at(level = 12)
        settle()
        (2..NAMES.size).forEach { viewModel.knockOut(it, 1) }
        assertEquals(1, viewModel.state().championId)
        assertFalse("the night is won", viewModel.state().canTakeLateEntry)
        assertTrue(viewModel.state().reEntries.isEmpty())
    }

    // A late arrival ---------------------------------------------------------------------------------

    @Test
    fun aLateArrivalJoinsAtTodaysPriceWithTheBuyInPaid() = with(kit) {
        val viewModel = night()
        val poolBefore = viewModel.state().pool
        viewModel.act(BankIntent.AddLateEntry("  Kai "))
        assertEquals("Kai joins late · $30 paid", snackbarMessage())
        settle()

        val state = viewModel.state()
        assertEquals(7, state.players.size)
        assertEquals("the Tournament's player count follows", 7, tournamentPreferences.getPlayerCount())
        val kai = viewModel.player(7)
        assertEquals("Kai", kai.name)
        assertTrue(kai.buyIn)
        assertEquals(entry, kai.entryPrice)
        assertNull(kai.reEntryOf)
        assertEquals(entry, bankPreferences.getPlayerEntryPrice(7))
        assertEquals("Kai", bankPreferences.getPlayerName(7))
        // The pools, the collected money, the players left and the row grow by one entry
        assertEquals(poolBefore.totalCents + entry.totalCents, state.pool.totalCents)
        assertEquals(poolBefore.bountyCents + entry.bountyCents, state.pool.bountyCents)
        assertEquals(7 * entry.totalCents, state.totalPaidInCents)
        assertEquals(7, state.summary.playersLeft)
        assertEquals(entry.totalCents, viewModel.row(7).paidInCents)
        assertEquals(state.pool.prizePoolCents, state.payoutTable.totalCents)
        assertNull(state.sheet)
    }

    @Test
    fun aBlankNameIsPlayerN() = with(kit) {
        val viewModel = night()
        viewModel.send(BankIntent.AddLateEntry("   "))
        assertEquals("Player 7", viewModel.player(7).name)
    }

    @Test
    fun aLateEntryKeepsThePriceItPaidWhenTheAmountsChange() = with(kit) {
        val viewModel = night()
        viewModel.send(BankIntent.AddLateEntry("Kai"))
        tournamentPreferences.setBuyIn(25.0)
        settle()
        val state = viewModel.state()
        assertEquals("six at $25, Kai at the $20 he paid", 6 * 2_500L + 2_000L, state.pool.buyInCents)
        viewModel.send(BankIntent.OpenPayOut(7))
        val sheet = viewModel.state().sheet as BankSheet.PayOut
        assertEquals(entry, sheet.entry)
        assertEquals(entry.totalCents, sheet.owed.costCents)
    }

    @Test
    fun placesAndPayoutsCountEntries() = with(kit) {
        val viewModel = night()
        viewModel.knockOut(6, 1)
        assertEquals("6th of 6 so far", 6, viewModel.state().placeByPlayer[6])
        viewModel.send(BankIntent.AddLateEntry("Kai"))
        assertEquals("a late entry makes the first one out 7th", 7, viewModel.state().placeByPlayer[6])
        listOf(5, 4, 3, 2, 7).forEach { viewModel.knockOut(it, 1) }
        val state = viewModel.state()
        assertEquals(1, state.championId)
        assertEquals((1..7).toList(), state.placeByPlayer.values.sorted())
        assertEquals(state.payableCents, state.payoutEligiblePlayerIds.sumOf { id -> viewModel.payOutSheet(id).owed.winningsCents })
    }

    // Re-entries ------------------------------------------------------------------------------------

    @Test
    fun aPlayerWhoIsOutReEntersAsANewEntryAndTheBustStays() = with(kit) {
        val viewModel = night()
        viewModel.knockOut(CARA, 1)
        assertEquals(listOf(CARA), viewModel.state().reEntries.map { it.playerId })
        assertEquals(6, viewModel.state().reEntries.single().place)

        viewModel.act(BankIntent.ReEnter(CARA))
        assertEquals("Cara re-enters · $30 paid", snackbarMessage())
        settle()
        val state = viewModel.state()
        val again = viewModel.player(7)
        assertEquals("Cara", again.name)
        assertEquals(CARA, again.reEntryOf)
        assertTrue(again.buyIn)
        assertEquals(entry, again.entryPrice)
        // The first entry is still out, knocked out by Ana, and her knockout still counts
        assertTrue(viewModel.player(CARA).out)
        assertEquals(1, viewModel.player(CARA).eliminatedBy)
        assertEquals(1, state.knockoutCounts[1])
        assertEquals(7, state.placeByPlayer[CARA])
        assertEquals(6, state.activePlayers)
        assertEquals(2, viewModel.row(7).entryNumber)
        assertNull(viewModel.row(CARA).entryNumber)
        assertFalse("her first entry can't be brought back while she plays on", viewModel.row(CARA).out.enabled)
        assertTrue("she's in again", state.reEntries.isEmpty())
        assertEquals("seven entries in the pool", 7 * entry.totalCents, state.pool.totalCents)

        // Bring back on the first entry does nothing now
        viewModel.send(BankIntent.BringBack(CARA))
        assertTrue(viewModel.player(CARA).out)
    }

    @Test
    fun aSecondReEntryBelongsToTheSamePlayer() = with(kit) {
        val viewModel = night()
        viewModel.knockOut(CARA, 1)
        viewModel.send(BankIntent.ReEnter(CARA))
        viewModel.knockOut(7, 2)
        assertEquals(listOf(7), viewModel.state().reEntries.map { it.playerId })
        assertEquals(2, viewModel.state().reEntries.single().entries)
        viewModel.send(BankIntent.ReEnter(7))
        assertEquals(CARA, viewModel.player(8).reEntryOf)
        assertEquals(3, viewModel.row(8).entryNumber)
        assertEquals(8, viewModel.state().players.size)
    }

    @Test
    fun onlyAPlayerWhoIsOutCanReEnter() = with(kit) {
        val viewModel = night()
        viewModel.send(BankIntent.ReEnter(CARA))
        assertEquals(6, viewModel.state().players.size)
    }

    @Test
    fun aMysteryLateEntryAddsOneEnvelopeAndDealsNothingAgain() = with(kit) {
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        val viewModel = night()
        viewModel.knockOut(6, 1)
        viewModel.send(BankIntent.DismissSheet)
        val before = viewModel.state().envelopesLeft
        assertEquals(5, before.size)
        viewModel.send(BankIntent.AddLateEntry("Kai"))
        assertEquals((before + 500L).sortedDescending(), viewModel.state().envelopesLeft)
        viewModel.knockOut(5, 2)
        viewModel.send(BankIntent.DismissSheet)
        viewModel.send(BankIntent.ReEnter(5))
        val state = viewModel.state()
        val drawn = state.players.mapNotNull { it.bountyDrawCents }.sum()
        assertEquals("every envelope is in the pool or drawn", state.pool.bountyCents, drawn + state.envelopesLeft.sum())
    }

    @Test
    fun aProgressiveReEntryStartsWithAFreshBounty() = with(kit) {
        tournamentPreferences.setBountyMode(BountyMode.PROGRESSIVE)
        val viewModel = night()
        viewModel.knockOut(CARA, 1)
        viewModel.send(BankIntent.ReEnter(CARA))
        assertEquals(750L, viewModel.row(1).bountyCents)
        assertEquals(500L, viewModel.row(7).bountyCents)
    }

    // Undo and process death --------------------------------------------------------------------------

    @Test
    fun undoTakesALateEntryBackRowAndAll() = with(kit) {
        val viewModel = night()
        val before = viewModel.recorded()
        val poolBefore = viewModel.state().pool
        viewModel.send(BankIntent.AddLateEntry("Kai"))
        viewModel.send(BankIntent.Undo)
        assertEquals(before, viewModel.recorded())
        assertEquals(poolBefore, viewModel.state().pool)
        assertEquals(6, tournamentPreferences.getPlayerCount())
        assertNull(bankPreferences.getPlayerEntryPrice(7))
        assertEquals("the name goes with the row", "Player 7", bankPreferences.getPlayerName(7))
        // And from the snackbar
        viewModel.act(BankIntent.AddLateEntry("Kai"))
        pressSnackbarUndo()
        assertEquals(before, viewModel.recorded())
        assertEquals(6, tournamentPreferences.getPlayerCount())
    }

    @Test
    fun undoTakesAReEntryBackAndTheBustStays() = with(kit) {
        val viewModel = night()
        viewModel.knockOut(CARA, 1)
        val before = viewModel.recorded()
        viewModel.send(BankIntent.ReEnter(CARA))
        viewModel.send(BankIntent.Undo)
        assertEquals(before, viewModel.recorded())
        assertTrue(viewModel.player(CARA).out)
        assertEquals(listOf(CARA), viewModel.state().reEntries.map { it.playerId })
        assertNull(bankPreferences.getPlayerReEntryOf(7))
        // A fresh read of what was saved agrees
        clear()
        assertEquals(before, newViewModel().recorded())
    }

    @Test
    fun aNightWithLateEntriesAndReEntriesComesBackExactlyAfterProcessDeath() = with(kit) {
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        val viewModel = night()
        viewModel.knockOut(CARA, 1)
        viewModel.send(BankIntent.DismissSheet, BankIntent.ReEnter(CARA), BankIntent.AddLateEntry("Kai"))
        viewModel.knockOut(7, 8)
        viewModel.send(BankIntent.DismissSheet)
        val before = viewModel.state().copy(sheet = null, undoLabel = null)
        val after = restartProcess().uiState.value.copy(sheet = null, undoLabel = null)
        assertEquals(before, after)
    }

    // The settle-up ---------------------------------------------------------------------------------

    @Test
    fun aPlayerWhoReEnteredSettlesOnceForBothEntries() = with(kit) {
        val viewModel = night()
        // Cara hasn't paid for either entry yet
        viewModel.send(BankIntent.BuyInToggled(CARA))
        viewModel.knockOut(CARA, 1)
        viewModel.send(BankIntent.ReEnter(CARA))
        viewModel.send(BankIntent.BuyInToggled(7))
        listOf(7, 6, 5, 4, 2).forEach { viewModel.knockOut(it, 1) }
        val settleUp = requireNotNull(viewModel.state().settleUp)
        assertEquals("one line each for the six players", NAMES, settleUp.nights.map { it.name })
        assertEquals(2 * entry.totalCents, settleUp.nights.first { it.name == "Cara" }.inCents)
        val cara = settleUp.transfers.filter { it.fromId == CARA }.sumOf { it.amountCents }
        assertEquals("she owes both entries, as one player", 2 * entry.totalCents, cara)
        assertTrue(settleUp.transfers.none { it.fromId == 7 || it.toId == 7 })
    }

    private companion object {
        val NAMES = listOf("Ana", "Ben", "Cara", "Dev", "Eve", "Finn")
        const val CARA = 3
    }
}
