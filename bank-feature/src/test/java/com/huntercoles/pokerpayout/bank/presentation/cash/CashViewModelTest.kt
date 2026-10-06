package com.huntercoles.pokerpayout.bank.presentation.cash

import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.Purchase
import com.huntercoles.pokerpayout.bank.presentation.cash.CashTestKit.Companion.CENTS
import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.domain.cash.CashGame
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.domain.cash.ChipCheck
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The cash game's ViewModel (S13, PP-029): the ledger and settle-up, Undo for every change,
 * surviving process death, the mode switch keeping both games, the tournament's resets leaving the
 * cash game alone (and the other way round), and the share text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CashViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: CashTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = CashTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    // The night ------------------------------------------------------------------------------------

    @Test
    fun theMockupsNightSettlesInFivePayments() = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel, counted = false)
        assertEquals(26_000L, viewModel.state.ledger.cashInCents)
        assertEquals(ChipCheck.Counting(6), viewModel.state.chipCheck)
        assertEquals(CashSettlement.Counting(6), viewModel.state.settlement)
        assertEquals(listOf(0, 0, 1, 0, 1, 2), viewModel.state.players.map { it.topUps })

        s13Counts(viewModel)
        assertEquals(ChipCheck.Balanced, viewModel.state.chipCheck)
        assertEquals(
            listOf("Theo pays Dana 47", "Marcus pays Dana 25", "Marcus pays Priya 20", "Sam pays Jo 12", "Sam pays Priya 8"),
            viewModel.payments(),
        )
        val nets = viewModel.state.players.map { viewModel.state.netFor(it)!! / CENTS }
        assertEquals(listOf(72L, 28L, 12L, -20L, -45L, -47L), nets)
    }

    private fun CashTestKit.s13Counts(viewModel: CashViewModel) = with(viewModel) {
        mapOf("Dana" to 112, "Priya" to 48, "Jo" to 52, "Sam" to 0, "Marcus" to 15, "Theo" to 33)
            .forEach { (name, dollars) -> cashOut(name, dollars) }
    }

    @Test
    fun anUnbalancedCountWaitsUntilRecountedOrSplit() = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel)
        viewModel.cashOut("Dana", 117)
        assertEquals(ChipCheck.Off(500L), viewModel.state.chipCheck)
        assertEquals(CashSettlement.Unbalanced(500L, canSplit = true), viewModel.state.settlement)
        assertEquals(emptyList<String>(), viewModel.payments())

        // The explicit choice: split the $5
        viewModel.act(CashIntent.SplitDifference)
        val split = viewModel.state.settlement as CashSettlement.Settled
        assertEquals(500L, split.splitCents)
        assertEquals("Split the $5 across the stacks", snackbarMessage())
        assertTrue(viewModel.state.transfers.isNotEmpty())

        // A recount that changes the difference asks again
        viewModel.cashOut("Dana", 115)
        assertEquals(CashSettlement.Unbalanced(300L, canSplit = true), viewModel.state.settlement)
        assertNull(viewModel.state.game.splitCents)

        // Recounted to balance: settles with no split
        viewModel.cashOut("Dana", 112)
        assertEquals(0L, (viewModel.state.settlement as CashSettlement.Settled).splitCents)

        // Taking a split back
        viewModel.cashOut("Dana", 117)
        viewModel.send(CashIntent.SplitDifference, CashIntent.Recount)
        assertEquals(CashSettlement.Unbalanced(500L, canSplit = true), viewModel.state.settlement)
    }

    @Test
    fun ticksStayWithTheirPaymentAndGoWhenItChanges() = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel)
        val theo = viewModel.state.transfers[0]
        val sam = viewModel.state.transfers[4]
        viewModel.send(CashIntent.SetPaid(theo, true))
        viewModel.act(CashIntent.SetPaid(sam, true))
        assertEquals(setOf(theo, sam), viewModel.state.game.paid)
        assertEquals("Sam paid Priya $8", snackbarMessage())
        // Renaming changes no payment
        viewModel.send(CashIntent.Rename(viewModel.id("Sam"), "Samantha"))
        assertEquals(setOf(theo, sam), viewModel.state.game.paid)
        // Sam's count changes: his payments change, Theo's doesn't. In between, while the count is
        // off, the ticks wait.
        viewModel.cashOut("Samantha", 2)
        assertEquals(setOf(theo, sam), viewModel.state.game.paid)
        assertTrue(viewModel.state.transfers.isEmpty())
        viewModel.cashOut("Dana", 110)
        assertEquals(setOf(theo), viewModel.state.game.paid)
        viewModel.send(CashIntent.SetPaid(theo, false))
        assertFalse(viewModel.state.isPaid(theo))
        assertFalse(viewModel.state.allPaid)
        viewModel.state.transfers.forEach { viewModel.send(CashIntent.SetPaid(it, true)) }
        assertTrue(viewModel.state.allPaid)
    }

    @Test
    fun guardsKeepTheLedgerSane() = with(kit) {
        val viewModel = newCashViewModel()
        viewModel.send(CashIntent.AddPlayer("Dana", 0L))
        assertTrue(viewModel.state.players.isEmpty())
        viewModel.send(CashIntent.AddPlayer("  ", 2_000L))
        assertEquals("Player 1", viewModel.state.players.single().name)
        val id = viewModel.state.players.single().id
        // The only buy-in can't be removed (remove the player instead); blank names don't stick
        viewModel.send(CashIntent.RemoveBuyIn(id, 0), CashIntent.Rename(id, " "), CashIntent.TopUp(id, -5L))
        assertEquals(listOf(2_000L), viewModel.state.players.single().buyInsCents)
        assertEquals("Player 1", viewModel.state.players.single().name)
        // Splitting needs a difference
        viewModel.send(CashIntent.SetCashOut(id, 2_000L), CashIntent.SplitDifference)
        assertNull(viewModel.state.game.splitCents)
        // The same count again isn't a change
        val label = viewModel.state.undoLabel
        viewModel.send(CashIntent.SetCashOut(id, 2_000L))
        assertEquals(label, viewModel.state.undoLabel)
        // Everything counted at $0 against money in: nothing to split over
        viewModel.send(CashIntent.SetCashOut(id, 0L), CashIntent.SplitDifference)
        assertEquals(CashSettlement.Unbalanced(-2_000L, canSplit = false), viewModel.state.settlement)
    }

    @Test
    fun sheetsOpenAndCloseWithTheirActions() = with(kit) {
        val viewModel = newCashViewModel()
        viewModel.send(CashIntent.ShowAddPlayer)
        assertEquals(CashSheet.AddPlayer, viewModel.state.sheet)
        viewModel.send(CashIntent.AddPlayer("Dana", 4_000L))
        assertNull(viewModel.state.sheet)
        viewModel.sitDown("Priya", 20)
        assertEquals(4_000L, viewModel.state.players.first().buyInsCents.single())
        assertEquals(2_000L, viewModel.state.suggestedBuyInCents)
        val dana = viewModel.id("Dana")
        viewModel.send(CashIntent.OpenPlayer(dana), CashIntent.TopUp(dana, 2_000L))
        assertEquals(CashSheet.Player(dana), viewModel.state.sheet)
        viewModel.send(CashIntent.RemovePlayer(dana))
        assertNull(viewModel.state.sheet)
        viewModel.send(CashIntent.OpenPlayer(dana))
        assertNull(viewModel.state.sheet)
        // Undo brings Dana back, sheet closed
        viewModel.send(CashIntent.Undo)
        assertEquals(listOf("Dana", "Priya"), viewModel.state.players.map { it.name })
    }

    // Undo ---------------------------------------------------------------------------------------------

    /** Applies [action], checks it changed the game, then undoes it: the game and what is saved come back. */
    private fun CashTestKit.assertUndoes(viewModel: CashViewModel, label: String, action: () -> Unit) {
        val before = viewModel.state.game
        val settlementBefore = viewModel.state.settlement
        action()
        assertNotEquals("$label changed nothing", before, viewModel.state.game)
        viewModel.send(CashIntent.Undo)
        assertEquals("$label: Undo restores the game", before, viewModel.state.game)
        assertEquals("$label: Undo restores the settle-up", settlementBefore, viewModel.state.settlement)
        assertEquals("$label: Undo is saved", before, BankPreferences(bank.context).getCashGame())
    }

    @Test
    fun undoTakesBackEveryKindOfChange() = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel)
        val dana = viewModel.id("Dana")
        val theo = viewModel.id("Theo")
        assertUndoes(viewModel, "add") { viewModel.sitDown("Ben", 20) }
        assertUndoes(viewModel, "top-up") { viewModel.send(CashIntent.TopUp(dana, 2_000L)) }
        assertUndoes(viewModel, "remove a buy-in") { viewModel.send(CashIntent.RemoveBuyIn(theo, 1)) }
        assertUndoes(viewModel, "cash-out") { viewModel.cashOut("Dana", 100) }
        assertUndoes(viewModel, "count cleared") { viewModel.cashOut("Dana", null) }
        assertUndoes(viewModel, "remove a player") { viewModel.send(CashIntent.RemovePlayer(theo)) }
        assertUndoes(viewModel, "paid") { viewModel.send(CashIntent.SetPaid(viewModel.state.transfers[0], true)) }
        viewModel.send(CashIntent.SetPaid(viewModel.state.transfers[0], true))
        assertUndoes(viewModel, "not paid") { viewModel.send(CashIntent.SetPaid(viewModel.state.transfers[0], false)) }
        viewModel.cashOut("Dana", 117)
        assertUndoes(viewModel, "split") { viewModel.send(CashIntent.SplitDifference) }
        viewModel.send(CashIntent.SplitDifference)
        assertUndoes(viewModel, "recount") { viewModel.send(CashIntent.Recount) }
        assertUndoes(viewModel, "clear the game") { viewModel.send(CashIntent.ClearGame) }
    }

    @Test
    fun theSnackbarSaysWhatHappenedAndItsUndoTakesItBack() = with(kit) {
        val viewModel = newCashViewModel()
        viewModel.sitDown("Dana", 40)
        viewModel.act(CashIntent.TopUp(viewModel.id("Dana"), 2_000L))
        assertEquals("Dana topped up · $20", snackbarMessage())
        assertEquals("Dana topped up · $20", viewModel.state.undoLabel)
        pressSnackbarUndo()
        assertEquals(listOf(4_000L), viewModel.state.players.single().buyInsCents)
        assertEquals("Dana bought in · $40", viewModel.state.undoLabel)

        viewModel.act(CashIntent.SetCashOut(viewModel.id("Dana"), 4_550L))
        assertEquals("Dana cashed out $45.50", snackbarMessage())
        viewModel.act(CashIntent.ClearGame)
        assertEquals("Cleared the cash game (1 player)", snackbarMessage())
        pressSnackbarUndo()
        assertEquals(4_550L, viewModel.state.players.single().cashOutCents)
    }

    @Test
    fun undoKeepsNamesTypedSinceAndStopsAtTwenty() = with(kit) {
        val viewModel = newCashViewModel()
        viewModel.sitDown("Dana", 10)
        val dana = viewModel.id("Dana")
        repeat(25) { viewModel.send(CashIntent.TopUp(dana, 100L)) }
        viewModel.send(CashIntent.Rename(dana, "Dee"))
        repeat(30) { viewModel.send(CashIntent.Undo) }
        val player = viewModel.state.players.single()
        assertEquals("Dee", player.name)
        // 26 changes, 20 undone: the buy-in and 5 top-ups stay
        assertEquals(6, player.buyInsCents.size)
        assertFalse(viewModel.state.canUndo)
    }

    @Test
    fun aSeededRandomSessionUndoesStepByStep() = with(kit) {
        val viewModel = newCashViewModel()
        val random = Random(SEED)
        val history = mutableListOf<CashGame>()
        repeat(STEPS) {
            val before = viewModel.state.game
            randomChange(viewModel, random)
            if (viewModel.state.game != before) history += before
        }
        history.takeLast(20).reversed().forEach { expected ->
            viewModel.send(CashIntent.Undo)
            assertEquals(expected, viewModel.state.game)
        }
    }

    private fun CashTestKit.randomChange(viewModel: CashViewModel, random: Random) {
        val players = viewModel.state.players
        val someone = players.randomOrNull(random)?.id
        val intent = when (random.nextInt(8)) {
            0, 1 -> CashIntent.AddPlayer("P${random.nextInt(100)}", random.nextLong(1L, 10_000L))
            2 -> someone?.let { CashIntent.TopUp(it, random.nextLong(1L, 5_000L)) }
            3 -> someone?.let { CashIntent.RemoveBuyIn(it, 0) }
            4, 5 -> someone?.let { CashIntent.SetCashOut(it, random.nextLong(0L, 20_000L).takeIf { random.nextInt(5) > 0 }) }
            6 -> viewModel.state.transfers.randomOrNull(random)?.let { CashIntent.SetPaid(it, !viewModel.state.isPaid(it)) }
            else -> if (random.nextBoolean()) CashIntent.SplitDifference else someone?.let { CashIntent.RemovePlayer(it) }
        }
        intent?.let { viewModel.send(it) }
    }

    // Process death ------------------------------------------------------------------------------------

    @Test
    fun theWholeGameSurvivesProcessDeath() = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel)
        viewModel.send(CashIntent.SwitchMode(BankMode.CASH))
        viewModel.cashOut("Dana", 117)
        viewModel.send(CashIntent.SplitDifference)
        viewModel.send(CashIntent.SetPaid(viewModel.state.transfers[1], true))
        viewModel.send(CashIntent.Rename(viewModel.id("Jo"), "Jo Ann"))
        val before = viewModel.state

        clear()
        val after = newCashViewModel(fresh = true).state
        assertEquals(BankMode.CASH, after.mode)
        assertEquals(before.game, after.game)
        assertEquals(before.settlement, after.settlement)
        assertTrue(after.isPaid(before.transfers[1]))
        // Undo history doesn't outlive the process
        assertFalse(after.canUndo)
    }

    // Two games, kept apart ----------------------------------------------------------------------------

    @Test
    fun switchingModeKeepsBothGames() = with(kit) {
        bank.configure(players = 4, buyIn = 20.0, rebuy = 10.0)
        val tournament = bank.newViewModel()
        with(bank) {
            tournament.send(BankIntent.BuyInToggled(1), BankIntent.BuyInToggled(2), BankIntent.AddPurchase(2, Purchase.REBUY))
            tournament.knockOut(3, 1)
        }
        val cash = newCashViewModel()
        s13(cash)
        val tournamentBefore = tournament.uiState.value
        val cashBefore = cash.state.game
        assertEquals(BankMode.TOURNAMENT, cash.state.mode)

        cash.send(CashIntent.SwitchMode(BankMode.CASH))
        assertEquals(BankMode.CASH, cash.state.mode)
        cash.send(CashIntent.SwitchMode(BankMode.TOURNAMENT), CashIntent.SwitchMode(BankMode.CASH))
        assertEquals(cashBefore, cash.state.game)
        assertEquals(tournamentBefore, tournament.uiState.value)

        // And after process death, in the mode last shown
        clear()
        assertEquals(BankMode.CASH, newCashViewModel(fresh = true).state.mode)
        assertEquals(cashBefore, newCashViewModel(fresh = true).state.game)
        with(bank) {
            val again = newViewModel().uiState.value
            assertEquals(tournamentBefore.players, again.players)
            assertEquals(tournamentBefore.eliminationOrder, again.eliminationOrder)
        }
    }

    @Test
    fun tournamentResetsLeaveTheCashGameAlone() = with(kit) {
        bank.configure(players = 5, buyIn = 20.0, rebuy = 10.0, addOn = 5.0)
        val tournament = bank.newViewModel()
        with(bank) {
            tournament.send(BankIntent.BuyInToggled(1), BankIntent.AddPurchase(1, Purchase.REBUY))
            tournament.send(BankIntent.PlayerNameChanged(1, "Alice"))
        }
        val cash = newCashViewModel()
        s13(cash)
        cash.send(CashIntent.SwitchMode(BankMode.CASH), CashIntent.SetPaid(cash.state.transfers[0], true))
        val cashBefore = cash.state.game

        // The Bank's "Reset bank…"
        with(bank) { tournament.send(BankIntent.ShowResetConfirm, BankIntent.ConfirmReset) }
        assertEquals("Player 1", tournament.uiState.value.players.first().name)
        // What the Tournament tab's reset and player-count changes call
        bank.bankPreferences.clearAllRebuys()
        bank.bankPreferences.clearAllAddons()
        bank.bankPreferences.removePlayersAbove(0)
        TournamentPreferences(bank.context).resetAllTournamentData()
        TimerPreferences(bank.context).resetAllTimerData()

        assertEquals(cashBefore, cash.state.game)
        clear()
        val after = newCashViewModel(fresh = true).state
        assertEquals(cashBefore, after.game)
        assertEquals(BankMode.CASH, after.mode)
    }

    @Test
    fun clearingTheCashGameLeavesTheTournamentAlone() = with(kit) {
        bank.configure(players = 4, buyIn = 20.0)
        val tournament = bank.newViewModel()
        with(bank) {
            tournament.send(BankIntent.PlayerNameChanged(1, "Alice"), BankIntent.BuyInToggled(1))
            tournament.knockOut(2, 1)
        }
        val recorded = tournament.uiState.value.players to tournament.uiState.value.eliminationOrder
        val undoLabel = tournament.uiState.value.undoLabel
        val revision = bank.bankPreferences.revision.value
        val cash = newCashViewModel()
        s13(cash)
        cash.send(CashIntent.ClearGame)
        assertTrue(cash.state.game.isEmpty)
        // Cash writes never touch the tournament's records, nor tell its screens to reload
        assertEquals(revision, bank.bankPreferences.revision.value)
        assertEquals(recorded, tournament.uiState.value.players to tournament.uiState.value.eliminationOrder)
        assertEquals(undoLabel, tournament.uiState.value.undoLabel)
        clear()
        with(bank) {
            val again = newViewModel().uiState.value
            assertEquals(recorded, again.players to again.eliminationOrder)
        }
    }

    // Share as text --------------------------------------------------------------------------------------

    private val resources get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun theShareTextIsTheSettleUpForTheGroupChat() = with(kit) {
        val viewModel = newCashViewModel()
        s13(viewModel, counted = false)
        assertNull("nothing to share before the count", CashShareText.build(resources, viewModel.state))
        s13Counts(viewModel)
        viewModel.send(CashIntent.SetPaid(viewModel.state.transfers[0], true))
        assertEquals(
            """
            Poker night: cash game
            Cash in $260 · counted out $260 · balanced

            Dana: in $40, out $112, up $72
            Priya: in $20, out $48, up $28
            Jo: in $40, out $52, up $12
            Sam: in $20, out $0, down $20
            Marcus: in $60, out $15, down $45
            Theo: in $80, out $33, down $47

            Settle up, 5 payments:
            Theo pays Dana $47 (paid)
            Marcus pays Dana $25
            Marcus pays Priya $20
            Sam pays Jo $12
            Sam pays Priya $8
            """.trimIndent(),
            CashShareText.build(resources, viewModel.state),
        )
    }

    @Test
    fun theShareTextSaysWhenTheDifferenceWasSplitAndWhenEveryoneIsEven() = with(kit) {
        val viewModel = newCashViewModel()
        viewModel.sitDown("Dana", 50)
        viewModel.sitDown("Sam", 50)
        viewModel.cashOut("Dana", 60)
        viewModel.cashOut("Sam", 45)
        assertNull("unbalanced: nothing to share yet", CashShareText.build(resources, viewModel.state))
        viewModel.send(CashIntent.SplitDifference)
        assertEquals(
            """
            Poker night: cash game
            Cash in $100 · counted out $105 · the $5 difference split across the stacks

            Dana: in $50, out $60 ($57.14 after the split), up $7.14
            Sam: in $50, out $45 ($42.86 after the split), down $7.14

            Settle up, 1 payment:
            Sam pays Dana $7.14
            """.trimIndent(),
            CashShareText.build(resources, viewModel.state),
        )
        viewModel.cashOut("Dana", 50)
        viewModel.cashOut("Sam", 50)
        assertEquals(
            "Dana: in $50, out $50, even\nSam: in $50, out $50, even\n\nEveryone is even: no payments.",
            CashShareText.build(resources, viewModel.state)!!.substringAfter("balanced\n\n"),
        )
    }

    private companion object {
        const val SEED = 29L
        const val STEPS = 120
    }
}
