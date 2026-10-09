package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.bank.presentation.BankScenes.ALEX
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.BEN
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.DANA
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.MARCUS
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.PRIYA
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.RITA
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.SAM
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.THEO
import com.huntercoles.pokerpayout.core.domain.settle.MinimumPayments
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.testing.withCurrency
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.core.utils.NO_BREAK_SPACE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The settle-up in the Bank (1.4; it took over the cash game's): who pays whom once the night is
 * over, the Bank one of the parties; a tick per payment that Undo, a restart and a reset respect; the
 * last tick records everyone square.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankSettleUpTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = BankTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    private val BankViewModel.state get() = uiState.value

    private fun BankViewModel.transfers(): List<Transfer> = requireNotNull(state.settleUp) { "no settle-up" }.transfers

    /** Every party ends square: what each pays less what each receives is what they owed, to the cent. */
    private fun assertSquares(balances: Map<Int, Long>, transfers: List<Transfer>) {
        val moved = mutableMapOf<Int, Long>()
        transfers.forEach { transfer ->
            assertTrue("$transfer: an amount", transfer.amountCents > 0L)
            assertTrue("$transfer: only who owes pays", balances.getValue(transfer.fromId) < 0L)
            assertTrue("$transfer: only who is owed is paid", balances.getValue(transfer.toId) > 0L)
            moved.merge(transfer.fromId, -transfer.amountCents, Long::plus)
            moved.merge(transfer.toId, transfer.amountCents, Long::plus)
        }
        balances.forEach { (id, cents) -> assertEquals("party $id", cents, moved[id] ?: 0L) }
    }

    @Test
    fun theSettleUpWaitsForTheNightToEnd() = with(kit) {
        val viewModel = BankScenes.midGame(kit)
        assertNull(viewModel.state.settleUp)
        assertFalse(viewModel.state.offersSettleUp)
        // Nothing to open, and nothing to tick
        viewModel.send(BankIntent.ShowSettleUp)
        assertNull(viewModel.state.sheet)
    }

    @Test
    fun whenEveryonePaidInTheBankPaysTheWinnersAndSettleUpIsntOffered() = with(kit) {
        // Everyone bought in; Marcus is paid. What is left is the Paid column: the Bank pays the rest.
        val viewModel = BankScenes.champion(kit)
        val transfers = viewModel.transfers()
        assertTrue(transfers.isNotEmpty())
        assertTrue("only the Bank pays", transfers.all { it.fromId == SettleUp.BANK_ID })
        assertEquals(setOf(DANA, PRIYA, THEO), transfers.map { it.toId }.toSet())
        transfers.forEach { assertEquals(viewModel.row(it.toId).owedCents, it.amountCents) }
        assertFalse("the Paid column does this already", viewModel.state.offersSettleUp)
    }

    @Test
    fun withBuyInsStillOpenThePlayersPayInTheFewestPayments() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val state = viewModel.state
        assertTrue(state.offersSettleUp)
        val transfers = viewModel.transfers()
        // The four who didn't pay in owe their $50 entry; the Bank holds $340 and keeps the $45 food
        val owed = state.players.associate { player ->
            val night = state.settleUp!!.nights.first { it.playerId == player.id }
            player.id to night.wonCents - if (player.buyIn) 0L else ENTRY
        }
        val balances = owed + (SettleUp.BANK_ID to -owed.values.sum())
        assertEquals(-(BANK_HOLDS - FOOD), balances.getValue(SettleUp.BANK_ID))
        listOf(SAM, ALEX, RITA, BEN).forEach { assertEquals(-ENTRY, balances.getValue(it)) }
        assertSquares(balances, transfers)
        assertEquals(MinimumPayments.exact(balances).size, transfers.size)
        assertTrue(transfers.size <= MinimumPayments.greedy(balances).size)
    }

    @Test
    fun ticksAreUndoneAndTheLastOneRecordsEveryoneSquare() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val transfers = viewModel.transfers()
        val first = transfers.first()
        viewModel.send(BankIntent.SetSettlePaid(first, true))
        assertEquals(setOf(first), viewModel.state.settlePaid)
        assertEquals("the plan doesn't move under a tick", transfers, viewModel.transfers())
        viewModel.send(BankIntent.Undo)
        assertEquals(emptySet<Transfer>(), viewModel.state.settlePaid)

        // Tick them all: the last one marks every buy-in and every payout paid
        val before = viewModel.recorded()
        transfers.forEach { viewModel.send(BankIntent.SetSettlePaid(it, true)) }
        val square = viewModel.state
        assertTrue(square.players.all { it.buyIn })
        assertTrue(square.rows.all { it.owedCents == 0L })
        assertEquals(emptyList<Transfer>(), square.settleUp!!.transfers)
        assertEquals(emptySet<Transfer>(), square.settlePaid)
        assertFalse(square.offersSettleUp)
        assertEquals("the Bank keeps the food money", FOOD, square.totalPaidInCents - square.totalPaidOutCents)
        assertEquals(square.payableCents, square.totalPaidOutCents)

        // One Undo takes back the square and the last tick, not the ticks before it
        viewModel.send(BankIntent.Undo)
        assertEquals(before, viewModel.recorded())
        assertEquals(transfers.dropLast(1).toSet(), viewModel.state.settlePaid)
        assertEquals(transfers, viewModel.transfers())
    }

    @Test
    fun theSquareActionSaysSoOnTheSnackbar() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val transfers = viewModel.transfers()
        transfers.dropLast(1).forEach { viewModel.send(BankIntent.SetSettlePaid(it, true)) }
        viewModel.act(BankIntent.SetSettlePaid(transfers.last(), true))
        assertEquals("Everyone is square: buy-ins and payouts marked paid", snackbarMessage())
        pressSnackbarUndo()
        assertEquals(transfers.dropLast(1).toSet(), viewModel.state.settlePaid)
    }

    @Test
    fun aTickSaysWhoPaidWhomWithTheBankByName() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val fromBank = viewModel.transfers().first { it.fromId == SettleUp.BANK_ID }
        viewModel.act(BankIntent.SetSettlePaid(fromBank, true))
        val name = viewModel.player(fromBank.toId).name
        assertEquals("The bank paid $name ${formatMoney(fromBank.amountCents)}", snackbarMessage())
        settle()
        val fromPlayer = viewModel.transfers().first { it.fromId != SettleUp.BANK_ID }
        viewModel.act(BankIntent.SetSettlePaid(fromPlayer, true))
        settle()
        viewModel.act(BankIntent.SetSettlePaid(fromPlayer, false))
        val payer = viewModel.player(fromPlayer.fromId).name
        val payee = viewModel.player(fromPlayer.toId).name
        assertEquals("$payer hasn't paid $payee yet", snackbarMessage())
    }

    @Test
    fun ticksSurviveARestartAndGoWhenTheirPaymentChanges() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val transfers = viewModel.transfers()
        val ticked = transfers.filter { it.fromId != SettleUp.BANK_ID }.take(2).toSet()
        ticked.forEach { viewModel.send(BankIntent.SetSettlePaid(it, true)) }

        clear()
        val restarted = newViewModel()
        assertEquals(ticked, restarted.state.settlePaid)
        assertEquals(transfers, restarted.transfers())

        // Paying Dana from the Bank's Paid column changes the settle-up: ticks for payments it no
        // longer lists go, and Undo brings them back
        restarted.send(BankIntent.SetPaid(DANA, true))
        val after = restarted.transfers()
        assertTrue(restarted.state.settlePaid.all { it in after })
        assertEquals(ticked.filter { it in after }.toSet(), restarted.state.settlePaid)
        assertEquals(restarted.state.settlePaid, bankPreferences.getSettlePaid())
        restarted.send(BankIntent.Undo)
        assertEquals(ticked, restarted.state.settlePaid)
        assertEquals(ticked, bankPreferences.getSettlePaid())
    }

    @Test
    fun bringingThePlayerBackEndsTheSettleUpAndUndoRestoresIt() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val tick = viewModel.transfers().first()
        viewModel.send(BankIntent.SetSettlePaid(tick, true), BankIntent.ShowSettleUp)
        assertEquals(BankSheet.SettleUp, viewModel.state.sheet)
        viewModel.send(BankIntent.BringBack(MARCUS))
        assertNull("no champion, no settle-up", viewModel.state.settleUp)
        assertEquals(emptySet<Transfer>(), viewModel.state.settlePaid)
        assertNull("its sheet closes", viewModel.state.sheet)
        viewModel.send(BankIntent.Undo)
        assertNotNull(viewModel.state.settleUp)
        assertEquals(setOf(tick), viewModel.state.settlePaid)
    }

    @Test
    fun aPaymentTheSettleUpDoesntListCantBeTicked() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val before = viewModel.state
        viewModel.send(BankIntent.SetSettlePaid(Transfer(SAM, DANA, 1L), true))
        viewModel.send(BankIntent.SetSettlePaid(viewModel.transfers().first(), false))
        assertEquals(before.settlePaid, viewModel.state.settlePaid)
        assertEquals(before.undoLabel, viewModel.state.undoLabel)
    }

    @Test
    fun theResetClearsTheTicks() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        viewModel.send(BankIntent.SetSettlePaid(viewModel.transfers().first(), true))
        viewModel.send(BankIntent.ShowResetConfirm, BankIntent.ConfirmReset)
        assertEquals(emptySet<Transfer>(), viewModel.state.settlePaid)
        assertEquals(emptySet<Transfer>(), bankPreferences.getSettlePaid())
    }

    @Test
    fun theSheetOpensOnlyOnceTheNightIsOver() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        viewModel.send(BankIntent.ShowSettleUp)
        assertEquals(BankSheet.SettleUp, viewModel.state.sheet)
    }

    @Test
    fun theShareTextListsEveryNightAndEveryPayment() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val transfers = viewModel.transfers()
        viewModel.send(BankIntent.SetSettlePaid(transfers.first(), true))
        val text = requireNotNull(SettleUpShareText.build(context.resources, viewModel.state))
        val lines = text.lines()
        assertEquals("Poker night: settle up", lines.first())
        assertTrue(text, lines.any { it.startsWith("Sam: in \$50, won \$0, down \$50") })
        assertTrue(text, lines.any { it.startsWith("Dana: in \$60, won ") && it.contains(", up ") })
        assertTrue(text, lines.contains("Food \$45, kept by the bank."))
        assertTrue(text, lines.contains("Settle up, ${transfers.size} payments:"))
        assertEquals(text, transfers.size, lines.count { it.contains(" pays ") })
        // The Bank owes the most, so its payments come first; the first one is ticked
        val first = transfers.first()
        assertEquals(SettleUp.BANK_ID, first.fromId)
        val payee = viewModel.player(first.toId).name
        assertTrue(text, lines.contains("The bank pays $payee ${formatMoney(first.amountCents)} (paid)"))
        assertNull("nothing to share before the night is over", SettleUpShareText.build(context.resources, BankUiState()))
    }

    /** PP-114: the same night shared in kronor; every amount in the host's currency, the money the same. */
    @Test
    fun theShareTextIsInTheHostsCurrency() = with(kit) {
        val viewModel = BankScenes.settleUp(kit)
        val first = viewModel.transfers().first()
        val text = withCurrency(AppCurrency.KRONA) { requireNotNull(SettleUpShareText.build(context.resources, viewModel.state)) }
        val lines = text.lines()
        val kr = "$NO_BREAK_SPACE" + "kr"
        assertTrue(text, lines.any { it.startsWith("Sam: in 50$kr, won 0$kr, down 50$kr") })
        assertTrue(text, lines.contains("Food 45$kr, kept by the bank."))
        val amount = withCurrency(AppCurrency.KRONA) { formatMoney(first.amountCents) }
        assertTrue(text, lines.contains("The bank pays ${viewModel.player(first.toId).name} $amount"))
        assertFalse(text, '$' in text)
    }

    @Test
    fun aNightWithNobodyPaidInSettlesBetweenThePlayersAndTheBankGetsTheFood() = with(kit) {
        configure(players = 4, buyIn = 20.0, food = 5.0)
        val viewModel = newViewModel()
        (2..4).forEach { viewModel.knockOut(it, 1) }
        val transfers = viewModel.transfers()
        val received = transfers.filter { it.toId == SettleUp.BANK_ID }.sumOf { it.amountCents }
        val paid = transfers.filter { it.fromId == SettleUp.BANK_ID }.sumOf { it.amountCents }
        assertEquals("the Bank ends with the food money", 4 * FIVE, received - paid)
        assertTrue(viewModel.state.offersSettleUp)
    }

    /** 1.3.14 left on its Cash game tab, a cash game saved: the Bank opens as the tournament's, as before. */
    @Test
    fun anInstallLeftOnTheCashGameOpensTheTournamentsBank() = with(kit) {
        configure(players = 5, buyIn = 20.0)
        context.getSharedPreferences("bank_prefs", android.content.Context.MODE_PRIVATE).edit()
            .putString("bank_mode", "cash")
            .putString("cash_players", "1,2")
            .putString("cash_name_1", "Dana")
            .putString("cash_buy_ins_1", "4000")
            .putLong("cash_out_1", 9_000L)
            .putString("cash_paid", "2>1:4000")
            .commit()
        val viewModel = newViewModel()
        val state = viewModel.state
        assertEquals((1..5).map { "Player $it" }, state.players.map { it.name })
        assertEquals(0L, state.totalPaidInCents)
        assertFalse(state.canReset)
        assertNull(state.settleUp)
        assertEquals(emptySet<Transfer>(), state.settlePaid)
        viewModel.send(BankIntent.BuyInToggled(1))
        assertTrue(viewModel.state.players.first().buyIn)
    }

    private companion object {
        const val ENTRY = 5_000L
        const val FOOD = 4_500L
        const val FIVE = 500L

        /** Five entries, Marcus's rebuy and five add-ons. */
        const val BANK_HOLDS = 5 * ENTRY + 4_000L + 5 * 1_000L
    }
}
