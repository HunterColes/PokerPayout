package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
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

/** Payout weights as the Bank screen sees them. Real preferences; see [BankViewModelTest] for why. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankViewModelWeightsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit
    private val tournamentPreferences get() = kit.tournamentPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    private fun newViewModel() = kit.newViewModel()

    private fun BankViewModel.send(vararg intents: BankIntent) = with(kit) { send(*intents) }

    private fun BankViewModel.saveWeights(weights: List<Int>) = with(kit) { saveWeights(weights) }

    private val BankViewModel.amounts get() = uiState.value.payoutTable.places.map { it.amountCents }

    @Test
    fun `default weights pay about one place per three players`() {
        // Default table: 5 players -> 2 places (PP-086; it was 1, winner takes all)
        assertEquals(listOf(35, 20), newViewModel().uiState.value.payoutWeights)

        tournamentPreferences.setPlayerCount(9)
        assertEquals(listOf(35, 20, 15), newViewModel().uiState.value.payoutWeights)

        tournamentPreferences.setPlayerCount(30)
        // Capped at the 9 default weights
        assertEquals(listOf(35, 20, 15, 10, 8, 6, 3, 2, 1), newViewModel().uiState.value.payoutWeights)
    }

    @Test
    fun `updating weights changes payout calculations`() {
        tournamentPreferences.setBuyIn(20.0)
        val viewModel = newViewModel()

        viewModel.saveWeights(listOf(40, 30, 20, 10))

        val state = viewModel.uiState.value
        assertEquals(listOf(40, 30, 20, 10), state.payoutWeights)
        assertEquals(listOf(40, 30, 20, 10), tournamentPreferences.getPayoutWeights())
        // Prize pool 5 x 20 = 100
        assertEquals(listOf(4_000L, 3_000L, 2_000L, 1_000L), viewModel.amounts)
        assertEquals(listOf(40.0, 30.0, 20.0, 10.0), state.payoutTable.places.map { it.sharePercent })
        assertEquals(listOf("1st", "2nd", "3rd", "4th"), state.payoutTable.places.map { it.ordinal })
    }

    @Test
    fun `payout positions always add up to the prize pool`() {
        tournamentPreferences.setPlayerCount(10)
        tournamentPreferences.setBuyIn(33.0)
        val viewModel = newViewModel()

        viewModel.saveWeights(listOf(35, 20, 15, 10, 8, 6, 4, 2))

        val state = viewModel.uiState.value
        assertEquals(33_000L, state.prizePoolCents)
        assertEquals(state.prizePoolCents, state.payoutTable.totalCents)
    }

    @Test
    fun `weights persist across view model recreation`() {
        newViewModel().saveWeights(listOf(45, 25, 18, 12))

        assertEquals(listOf(45, 25, 18, 12), newViewModel().uiState.value.payoutWeights)
    }

    @Test
    fun `custom weights survive a player count change`() {
        newViewModel().saveWeights(listOf(40, 25, 20, 15))

        tournamentPreferences.setPlayerCount(12)

        assertEquals(listOf(40, 25, 20, 15), newViewModel().uiState.value.payoutWeights)
    }

    @Test
    fun `bank reset clears player state but leaves the tournament's payout weights alone`() {
        val viewModel = newViewModel()
        viewModel.saveWeights(listOf(50, 30, 20))
        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.PlayerNameChanged(2, "Ana"))

        viewModel.send(BankIntent.ShowResetConfirm, BankIntent.ConfirmReset)

        val state = viewModel.uiState.value
        assertFalse(state.players.first { it.id == 1 }.buyIn)
        assertEquals("Player 2", state.players.first { it.id == 2 }.name)
        assertEquals(0L, state.totalPaidInCents)
        // Weights belong to the Tournament tab, which has its own reset
        assertEquals(listOf(50, 30, 20), state.payoutWeights)
    }

    @Test
    fun `reset asks only when bank state differs from default`() {
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowResetConfirm)
        assertNull(viewModel.uiState.value.sheet)
        assertFalse(viewModel.uiState.value.canReset)

        // Changing weights alone is not Bank state
        viewModel.saveWeights(listOf(50, 30, 20))
        viewModel.send(BankIntent.ShowResetConfirm)
        assertNull(viewModel.uiState.value.sheet)

        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.ShowResetConfirm)
        assertEquals(BankSheet.ResetConfirm(playerCount = 5), viewModel.uiState.value.sheet)

        viewModel.send(BankIntent.ConfirmReset)
        assertNull(viewModel.uiState.value.sheet)
        assertFalse(viewModel.uiState.value.canReset)
        // A reset can't be undone: it asked first
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun `the payout structure sheet shows and hides`() {
        val viewModel = newViewModel()
        assertNull(viewModel.uiState.value.sheet)

        viewModel.send(BankIntent.ShowPayoutStructure)
        assertEquals(BankSheet.PayoutStructure, viewModel.uiState.value.sheet)

        viewModel.send(BankIntent.DismissSheet)
        assertNull(viewModel.uiState.value.sheet)

        // Saving also closes it
        viewModel.send(BankIntent.ShowPayoutStructure)
        viewModel.saveWeights(listOf(60, 40))
        assertNull(viewModel.uiState.value.sheet)
        assertEquals(listOf(60, 40), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `the payout structure is read-only while the clock runs`() {
        val viewModel = newViewModel()
        kit.timerPreferences.setTimerRunning(true)
        kit.settle()

        viewModel.saveWeights(listOf(60, 40))

        assertTrue(viewModel.uiState.value.isTimerRunning)
        assertEquals(listOf(35, 20), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `updating weights recalculates payouts immediately`() {
        tournamentPreferences.setBuyIn(20.0)
        val viewModel = newViewModel()
        (1..5).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }

        viewModel.saveWeights(listOf(50, 50))

        assertEquals(listOf(5_000L, 5_000L), viewModel.amounts)
    }

    @Test
    fun `custom weights never pay more places than there are players`() {
        // 5 players, 9 weights (PP-016)
        val viewModel = newViewModel()

        viewModel.saveWeights(TournamentConstants.DEFAULT_PAYOUT_WEIGHTS)

        assertEquals(5, viewModel.uiState.value.payoutTable.places.size)
        assertEquals(10_000L, viewModel.uiState.value.payoutTable.totalCents)
    }

    @Test
    fun `the payout editor saves preset and rounding from the bank`() {
        tournamentPreferences.setPlayerCount(10)
        tournamentPreferences.setBuyIn(23.0)
        val viewModel = newViewModel()

        viewModel.send(
            BankIntent.ShowPayoutStructure,
            BankIntent.UpdatePayoutSettings(
                PayoutSettings(listOf(50, 24, 13, 8, 5), PayoutPreset.TOP_HEAVY, PayoutRounding.FIVE_DOLLARS)
            )
        )

        val state = viewModel.uiState.value
        assertNull(state.sheet)
        assertEquals(PayoutPreset.TOP_HEAVY, state.payoutSettings.preset)
        assertEquals(PayoutRounding.FIVE_DOLLARS, tournamentPreferences.getPayoutRounding())
        // $230 at $5: 2nd 55.20 -> 55, 3rd 29.90 -> 30, 4th 18.40 -> 20, 5th 11.50 -> 10, 1st 115
        assertEquals(listOf(11_500L, 5_500L, 3_000L, 2_000L, 1_000L), viewModel.amounts)
    }
}
