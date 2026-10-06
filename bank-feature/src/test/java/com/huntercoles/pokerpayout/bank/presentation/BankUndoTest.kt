package com.huntercoles.pokerpayout.bank.presentation

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
 * Undo (PP-030): every routine action can be taken back, from the snackbar or the top bar, newest
 * first, up to 20 deep, and takes back everything it changed: places, bounty credits, payouts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankUndoTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
        kit.configure(players = 5, buyIn = 40.0, food = 5.0, bounty = 5.0, rebuy = 40.0, addOn = 10.0, weights = listOf(3, 2, 1))
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    /** Applies [action], checks it changed something, then undoes it and checks everything came back. */
    private fun BankTestKit.assertUndoes(viewModel: BankViewModel, label: String, action: () -> Unit) {
        val before = viewModel.recorded()
        val moneyBefore = viewModel.uiState.value.let { Triple(it.totalPaidInCents, it.totalPaidOutCents, it.knockoutCounts) }
        action()
        assertTrue("$label changed nothing", viewModel.recorded() != before)
        viewModel.send(BankIntent.Undo)
        assertEquals("$label: Undo restores what was recorded", before, viewModel.recorded())
        val moneyAfter = viewModel.uiState.value.let { Triple(it.totalPaidInCents, it.totalPaidOutCents, it.knockoutCounts) }
        assertEquals("$label: Undo restores the money", moneyBefore, moneyAfter)
        // And what was saved, as a fresh read of the preferences shows
        clear()
        assertEquals("$label: Undo is saved", before, newViewModel().recorded())
    }

    @Test
    fun undoTakesBackABuyIn() = with(kit) {
        val viewModel = newViewModel()
        assertUndoes(viewModel, "buy-in") { viewModel.send(BankIntent.BuyInToggled(1)) }
    }

    @Test
    fun undoTakesBackClearingABuyIn() = with(kit) {
        newViewModel().send(BankIntent.BuyInToggled(1))
        val viewModel = newViewModel()
        assertUndoes(viewModel, "buy-in cleared") { viewModel.send(BankIntent.BuyInToggled(1)) }
    }

    @Test
    fun undoTakesBackARebuyAndAnAddOn() = with(kit) {
        val viewModel = newViewModel()
        assertUndoes(viewModel, "rebuy") { viewModel.send(BankIntent.AddPurchase(2, Purchase.REBUY)) }
        val again = newViewModel()
        assertUndoes(again, "add-on") { again.send(BankIntent.AddPurchase(2, Purchase.ADD_ON)) }
    }

    @Test
    fun undoTakesBackACountFromTheSheet() = with(kit) {
        newViewModel().setRebuys(3, 2)
        val viewModel = newViewModel()
        assertUndoes(viewModel, "count up") { viewModel.setRebuys(3, 5) }
        val again = newViewModel()
        assertUndoes(again, "count down") { again.setRebuys(3, 0) }
    }

    @Test
    fun undoTakesBackAKnockoutWithItsCredit() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.BuyInToggled(1))
        assertUndoes(viewModel, "knockout") { viewModel.knockOut(4, 1) }
        assertEquals(emptyMap<Int, Int>(), viewModel.uiState.value.knockoutCounts)
    }

    @Test
    fun undoingTheLastKnockoutGivesTheChampionsBountiesBack() = with(kit) {
        val viewModel = newViewModel()
        listOf(5, 4, 3).forEach { viewModel.knockOut(it, 1) }
        val beforeChampion = viewModel.uiState.value
        assertNull(beforeChampion.championId)

        viewModel.knockOut(2, null)
        assertEquals(1, viewModel.uiState.value.championId)
        assertEquals(500L, viewModel.payOutSheet(1).owed.unclaimedBountyCents)

        viewModel.send(BankIntent.Undo)
        assertNull(viewModel.uiState.value.championId)
        assertEquals(beforeChampion.placeByPlayer, viewModel.uiState.value.placeByPlayer)
        assertEquals(mapOf(1 to 3), viewModel.uiState.value.knockoutCounts)
    }

    @Test
    fun undoTakesBackBringingAPlayerBack() = with(kit) {
        newViewModel().knockOut(2, 3)
        val viewModel = newViewModel()
        assertUndoes(viewModel, "back in") { viewModel.send(BankIntent.BringBack(2)) }
        assertEquals(3, viewModel.player(2).eliminatedBy)
    }

    @Test
    fun undoTakesBackAPayout() = with(kit) {
        val viewModel = newViewModel()
        (5 downTo 2).forEach { viewModel.knockOut(it, 1) }
        assertUndoes(viewModel, "paid") { viewModel.send(BankIntent.SetPaid(1, true)) }
        viewModel.send(BankIntent.SetPaid(1, true))
        val again = newViewModel()
        assertUndoes(again, "unpaid") { again.send(BankIntent.SetPaid(1, false)) }
    }

    @Test
    fun theSnackbarSaysWhatHappenedAndItsUndoTakesItBack() = with(kit) {
        tournamentPreferences.setPlayerCount(9)
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerNameChanged(8, "Rita"), BankIntent.PlayerNameChanged(2, "Marcus"))

        viewModel.act(BankIntent.KnockOut(8, 2))
        assertEquals("Rita is out in 9th · bounty to Marcus", snackbarMessage())
        assertTrue(viewModel.player(8).out)

        pressSnackbarUndo()
        assertFalse(viewModel.player(8).out)
        assertNull(viewModel.player(8).eliminatedBy)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun snackbarMessagesNameTheAmounts() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.PlayerNameChanged(1, "Dana"))

        viewModel.act(BankIntent.BuyInToggled(1))
        assertEquals("Dana paid the buy-in · $50", snackbarMessage())
        viewModel.act(BankIntent.AddPurchase(1, Purchase.REBUY))
        assertEquals("Rebuy for Dana · $40", snackbarMessage())
        viewModel.act(BankIntent.KnockOut(2, null))
        assertEquals("Player 2 is out in 5th · bounty to the champion", snackbarMessage())
        settle()
    }

    @Test
    fun aNewActionReplacesTheSnackbarAndItsUndoIsForTheNewOne() = with(kit) {
        val viewModel = newViewModel()
        viewModel.act(BankIntent.BuyInToggled(1))
        viewModel.act(BankIntent.BuyInToggled(2))

        pressSnackbarUndo()
        assertTrue("the older action stays", viewModel.player(1).buyIn)
        assertFalse(viewModel.player(2).buyIn)
        // The older one is still in the history
        viewModel.send(BankIntent.Undo)
        assertFalse(viewModel.player(1).buyIn)
    }

    @Test
    fun theTopBarUndoGoesBackNewestFirstUpToTwenty() = with(kit) {
        tournamentPreferences.setPlayerCount(25)
        val viewModel = newViewModel()
        val start = viewModel.recorded()
        val states = mutableListOf(start)
        (1..25).forEach {
            viewModel.send(BankIntent.BuyInToggled(it))
            states += viewModel.recorded()
        }

        repeat(20) { step ->
            assertTrue(viewModel.uiState.value.canUndo)
            viewModel.send(BankIntent.Undo)
            assertEquals("after ${step + 1} undos", states[states.size - 2 - step], viewModel.recorded())
        }
        // Twenty is the limit: the first five buy-ins stay
        assertFalse(viewModel.uiState.value.canUndo)
        viewModel.send(BankIntent.Undo)
        assertEquals(states[5], viewModel.recorded())
    }

    @Test
    fun theUndoLabelIsTheNewestAction() = with(kit) {
        val viewModel = newViewModel()
        assertNull(viewModel.uiState.value.undoLabel)
        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.AddPurchase(1, Purchase.ADD_ON))
        assertEquals("Add-on for Player 1 · $10", viewModel.uiState.value.undoLabel)
        viewModel.send(BankIntent.Undo)
        assertEquals("Player 1 paid the buy-in · $50", viewModel.uiState.value.undoLabel)
    }

    @Test
    fun undoKeepsNamesTypedSinceTheAction() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.PlayerNameChanged(1, "Dana"))

        viewModel.send(BankIntent.Undo)

        assertFalse(viewModel.player(1).buyIn)
        assertEquals("Dana", viewModel.player(1).name)
    }

    @Test
    fun changesFromOutsideTheBankClearTheHistory() = with(kit) {
        val viewModel = newViewModel()
        viewModel.send(BankIntent.BuyInToggled(1))
        assertTrue(viewModel.uiState.value.canUndo)

        // The Tournament tab clears the rebuys after the amount went to zero, and it changes the count
        bankPreferences.savePlayerRebuys(2, 1)
        settle()
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.send(BankIntent.BuyInToggled(1))
        tournamentPreferences.setPlayerCount(6)
        settle()
        assertFalse(viewModel.uiState.value.canUndo)
    }

    /** Any sequence of actions, undone one by one, gives back the start, at every step. */
    @Test
    fun randomActionsUndoneOneByOneRetraceEveryStep() = with(kit) {
        val random = Random(30_2026)
        repeat(SESSIONS) { session ->
            clear()
            bankPreferences.resetAllBankData()
            val viewModel = newViewModel()
            val history = mutableListOf(viewModel.recorded())
            repeat(random.nextInt(1, MAX_UNDO + 1)) {
                val before = viewModel.recorded()
                randomAction(viewModel, random)
                if (viewModel.recorded() != before) history += viewModel.recorded()
            }
            while (history.size > 1) {
                history.removeLast()
                viewModel.send(BankIntent.Undo)
                assertEquals("session $session, ${history.size - 1} left", history.last(), viewModel.recorded())
            }
            assertFalse(viewModel.uiState.value.canUndo)
        }
    }

    private fun BankTestKit.randomAction(viewModel: BankViewModel, random: Random) {
        val id = random.nextInt(1, PLAYERS + 1)
        val stillIn = viewModel.uiState.value.players.filterNot { it.out }.map { it.id }
        when (random.nextInt(7)) {
            0 -> viewModel.send(BankIntent.BuyInToggled(id))
            1 -> viewModel.send(BankIntent.AddPurchase(id, Purchase.REBUY))
            2 -> viewModel.send(BankIntent.AddPurchase(id, Purchase.ADD_ON))
            3 -> viewModel.setRebuys(id, random.nextInt(0, 3))
            4 -> if (id in stillIn) viewModel.knockOut(
                id,
                (stillIn - id).randomOrNull(random)
            ) else viewModel.send(BankIntent.BringBack(id))
            5 -> viewModel.togglePaid(id)
            else -> viewModel.setAddOns(id, random.nextInt(0, 3))
        }
    }

    private companion object {
        const val PLAYERS = 5
        const val SESSIONS = 40
        const val MAX_UNDO = 20
    }
}
