package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPlayerNamesProvider
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.tools.table.BankTonight
import com.huntercoles.pokerpayout.tools.table.DealProblem
import com.huntercoles.pokerpayout.tools.table.Tonight
import com.huntercoles.pokerpayout.tools.table.TonightSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The deal maker's ViewModel (S22): it starts from tonight (the Bank's players still in, the
 * Payouts tab's prizes, read through the real preferences and settlement), prizes can be typed
 * instead, a sum can be saved for the winner, the deal follows every change, and Start over reads
 * tonight again with Undo.
 */
class DealViewModelTest {

    private val main = StandardTestDispatcher()
    private val memory = TableToolsMemory()
    private val snackbars = SnackbarController()
    private val messages = mockk<TableToolMessages> {
        every { startedOver } returns "Deal started over from tonight"
        every { undo } returns "Undo"
    }
    private var tonight = Tonight(playersLeft = listOf("Dana", "Sam", "Theo"), prizes = listOf(6_000, 3_600, 2_400, 0, 0, 0))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(main)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: TonightSource = TonightSource { tonight }) = DealViewModel(source, memory, snackbars, messages)

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    private val DealViewModel.state get() = uiState.value

    private fun DealViewModel.stacks(vararg chips: Long) =
        chips.forEachIndexed { index, stack -> acceptIntent(DealIntent.SetChips(index, stack)) }

    @Test
    fun `it starts from the Bank's players still in and tonight's payouts`() {
        val state = viewModel().state
        assertEquals(listOf("Dana", "Sam", "Theo"), state.players.map { it.name })
        assertTrue(state.fromBank)
        assertEquals(PrizeSource.Payouts, state.source)
        assertEquals(listOf(6_000L, 3_600L, 2_400L), state.prizes)
        assertEquals(DealProblem.MissingChips, state.problem)
        assertNull(state.deal)
    }

    @Test
    fun `with the night over or not started it starts from three to name, and no pool means typing`() {
        tonight = Tonight(playersLeft = listOf("Dana"), prizes = listOf(0, 0, 0))
        val state = viewModel().state
        assertEquals(List(3) { DealPlayer() }, state.players)
        assertFalse(state.fromBank)
        assertEquals(PrizeSource.Typed, state.source)
        assertFalse(state.hasPayouts)
    }

    @Test
    fun `the stacks make the deal, adding up to the prizes left`() {
        val vm = viewModel()
        vm.stacks(5_000, 3_000, 2_000)
        val deal = checkNotNull(vm.state.deal)
        assertEquals(12_000L, deal.splitCents)
        assertEquals(12_000L, deal.icm.sum())
        assertEquals(12_000L, deal.chipChop.sum())
        // $24 each, then $48 at 50 / 30 / 20.
        assertEquals(listOf(4_800L, 3_840L, 3_360L), deal.chipChop)
    }

    @Test
    fun `a player more takes the next place's prize, a player less drops the last`() {
        val vm = viewModel()
        vm.acceptIntent(DealIntent.AddPlayer)
        assertEquals(listOf(6_000L, 3_600L, 2_400L, 0L), vm.state.prizes)
        assertFalse(vm.state.fromBank, "no longer just the Bank's")
        vm.acceptIntent(DealIntent.RemovePlayer(3))
        vm.acceptIntent(DealIntent.RemovePlayer(2))
        assertEquals(listOf(6_000L, 3_600L), vm.state.prizes)
        vm.acceptIntent(DealIntent.RemovePlayer(1))
        assertEquals(2, vm.state.players.size, "never fewer than two")
    }

    @Test
    fun `typing the prizes starts from tonight's, and the deal follows them`() {
        val vm = viewModel()
        vm.stacks(5_000, 3_000, 2_000)
        vm.acceptIntent(DealIntent.SetSource(PrizeSource.Typed))
        assertEquals(listOf(6_000L, 3_600L, 2_400L), vm.state.typed)
        vm.acceptIntent(DealIntent.SetPrize(place = 1, cents = 5_000))
        vm.acceptIntent(DealIntent.SetPrize(place = 2, cents = 3_000))
        vm.acceptIntent(DealIntent.SetPrize(place = 3, cents = 2_000))
        assertEquals(listOf(3_839L, 3_275L, 2_886L), vm.state.deal?.icm)
        vm.acceptIntent(DealIntent.SetSource(PrizeSource.Payouts))
        assertEquals(12_000L, vm.state.deal?.splitCents)
        vm.acceptIntent(DealIntent.SetSource(PrizeSource.Typed))
        assertEquals(listOf(5_000L, 3_000L, 2_000L), vm.state.prizes, "what was typed is kept")
    }

    @Test
    fun `prizes that go up or add up to nothing stop the deal`() {
        val vm = viewModel()
        vm.stacks(5_000, 3_000, 2_000)
        vm.acceptIntent(DealIntent.SetSource(PrizeSource.Typed))
        vm.acceptIntent(DealIntent.SetPrize(place = 3, cents = 4_000))
        assertEquals(DealProblem.PrizesGoUp(place = 3), vm.state.problem)
        listOf(1, 2, 3).forEach { vm.acceptIntent(DealIntent.SetPrize(place = it, cents = null)) }
        assertEquals(DealProblem.NoPrizes, vm.state.problem)
        assertNull(vm.state.deal)
    }

    @Test
    fun `saving for the winner shares the rest, up to what 1st pays over 2nd`() {
        val vm = viewModel()
        vm.stacks(5_000, 3_000, 2_000)
        assertEquals(2_400L, vm.state.maxForWinnerCents)
        vm.acceptIntent(DealIntent.SetForWinner(1_000))
        val deal = checkNotNull(vm.state.deal)
        assertEquals(11_000L, deal.splitCents)
        assertEquals(1_000L, deal.forWinnerCents)
        vm.acceptIntent(DealIntent.SetForWinner(2_401))
        assertEquals(DealProblem.TooMuchForWinner(maxCents = 2_400), vm.state.problem)
        vm.acceptIntent(DealIntent.SetForWinner(0))
        assertNull(vm.state.forWinnerCents)
        assertEquals(12_000L, vm.state.deal?.splitCents)
    }

    @Test
    fun `the deal is there when the screen comes back, with tonight's payouts read again`() {
        val vm = viewModel()
        vm.stacks(5_000, 3_000, 2_000)
        vm.acceptIntent(DealIntent.SetName(0, "Dana B"))
        tonight = tonight.copy(prizes = listOf(7_000, 3_000, 2_000))
        val back = viewModel().state
        assertEquals(vm.state.players, back.players)
        assertEquals(listOf(7_000L, 3_000L, 2_000L), back.prizes)
    }

    @Test
    fun `Start over reads tonight again, and Undo brings the deal back`() = runVmTest {
        val vm = viewModel()
        vm.stacks(5_000, 3_000, 2_000)
        vm.acceptIntent(DealIntent.AddPlayer)
        val before = vm.state
        vm.acceptIntent(DealIntent.StartOver)
        testScheduler.runCurrent()
        assertEquals(listOf("Dana", "Sam", "Theo"), vm.state.players.map { it.name })
        assertTrue(vm.state.players.all { it.chips == null })
        val snackbar = checkNotNull(snackbars.hostState.currentSnackbarData) { "no Undo offered" }
        assertEquals("Deal started over from tonight", snackbar.visuals.message)
        snackbar.performAction()
        testScheduler.advanceUntilIdle()
        assertEquals(before, vm.state)
    }

    @Test
    fun `tonight comes from the Bank and the Payouts tab, settled the same way`() {
        val stores = mutableMapOf<String, SharedPreferences>()
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } answers { stores.getOrPut(firstArg()) { InMemoryPreferences() } }
        }
        val tournament = TournamentPreferences(context)
        val bank = BankPreferences(context)
        tournament.setPlayerCount(6)
        tournament.setBuyInCents(2_000)
        tournament.setFoodCents(0)
        tournament.setPayoutWeights(listOf(50, 30, 20))
        val names = listOf("Dana", "Marcus", "Sam", "Theo", "Jo", "Rita")
        names.forEachIndexed { index, name -> bank.savePlayerName(index + 1, name) }
        bank.saveEliminationOrder(listOf(6, 2, 5))
        val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())
        val source = BankTonight(tournament, bank, settle, BankPlayerNamesProvider(tournament, bank))

        // Six at $20: $120, paid 50 / 30 / 20. Rita, Marcus and Jo are out.
        val expected = Tonight(playersLeft = listOf("Dana", "Sam", "Theo"), prizes = listOf(6_000, 3_600, 2_400, 0, 0, 0))
        assertEquals(expected, source.tonight())
        bank.saveEliminationOrder(listOf(6, 2, 5, 4, 3))
        assertEquals(emptyList<String>(), source.tonight().playersLeft, "Dana won: nobody is left to deal")
    }
}
