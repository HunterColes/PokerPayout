package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var bankPreferences: BankPreferences
    private lateinit var timerPreferences: TimerPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "bank_prefs", "timer_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        bankPreferences = BankPreferences(context)
        timerPreferences = TimerPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = BankViewModel(tournamentPreferences, bankPreferences, timerPreferences)
        .also { testDispatcher.scheduler.advanceUntilIdle() }

    private fun assertAmounts(expected: List<Double>, actual: List<Double>) {
        assertEquals("count of $actual", expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals("in $actual", e, a, 1e-9) }
    }

    private fun BankViewModel.send(vararg intents: BankIntent) {
        intents.forEach { acceptIntent(it) }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `default weights pay one place per three players`() {
        // Default table: 5 players -> 1 place
        assertEquals(listOf(35), newViewModel().uiState.value.payoutWeights)

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

        viewModel.send(BankIntent.UpdateWeights(listOf(40, 30, 20, 10)))

        val state = viewModel.uiState.value
        assertEquals(listOf(40, 30, 20, 10), state.payoutWeights)
        assertEquals(listOf(40, 30, 20, 10), tournamentPreferences.getPayoutWeights())
        // Prize pool 5 x 20 = 100
        assertAmounts(listOf(40.0, 30.0, 20.0, 10.0), state.payoutPositions.map { it.payout })
        assertEquals(listOf("40%", "30%", "20%", "10%"), state.payoutPositions.map { it.formattedPercentage })
        assertEquals(listOf("st", "nd", "rd", "th"), state.payoutPositions.map { it.positionSuffix })
    }

    @Test
    fun `payout positions always add up to the prize pool`() {
        tournamentPreferences.setPlayerCount(10)
        tournamentPreferences.setBuyIn(33.0)
        val viewModel = newViewModel()

        viewModel.send(BankIntent.UpdateWeights(listOf(35, 20, 15, 10, 8, 6, 4, 2)))

        val state = viewModel.uiState.value
        assertEquals(330.0, state.prizePool, 0.001)
        assertEquals(state.prizePool, state.payoutPositions.sumOf { it.payout }, 1e-9)
    }

    @Test
    fun `weights persist across view model recreation`() {
        newViewModel().send(BankIntent.UpdateWeights(listOf(45, 25, 18, 12)))

        assertEquals(listOf(45, 25, 18, 12), newViewModel().uiState.value.payoutWeights)
    }

    @Test
    fun `custom weights survive a player count change`() {
        newViewModel().send(BankIntent.UpdateWeights(listOf(40, 25, 20, 15)))

        tournamentPreferences.setPlayerCount(12)

        assertEquals(listOf(40, 25, 20, 15), newViewModel().uiState.value.payoutWeights)
    }

    @Test
    fun `bank reset clears player state but leaves the tournament's payout weights alone`() {
        val viewModel = newViewModel()
        viewModel.send(
            BankIntent.UpdateWeights(listOf(50, 30, 20)),
            BankIntent.BuyInToggled(1),
            BankIntent.PlayerNameChanged(2, "Ana")
        )

        viewModel.send(BankIntent.ConfirmReset)

        val state = viewModel.uiState.value
        assertFalse(state.players.first { it.id == 1 }.buyIn)
        assertEquals("Player 2", state.players.first { it.id == 2 }.name)
        assertEquals(0.0, state.totalPaidIn, 0.001)
        // Weights belong to the Tournament tab, which has its own reset
        assertEquals(listOf(50, 30, 20), state.payoutWeights)
    }

    @Test
    fun `reset dialog opens only when bank state differs from default`() {
        val viewModel = newViewModel()

        viewModel.send(BankIntent.ShowResetDialog)
        assertFalse(viewModel.uiState.value.showResetDialog)

        // Changing weights alone is not Bank state
        viewModel.send(BankIntent.UpdateWeights(listOf(50, 30, 20)), BankIntent.ShowResetDialog)
        assertFalse(viewModel.uiState.value.showResetDialog)

        viewModel.send(BankIntent.BuyInToggled(1), BankIntent.ShowResetDialog)
        assertTrue(viewModel.uiState.value.showResetDialog)

        viewModel.send(BankIntent.ConfirmReset)
        assertFalse(viewModel.uiState.value.showResetDialog)
        viewModel.send(BankIntent.ShowResetDialog)
        assertFalse(viewModel.uiState.value.showResetDialog)
    }

    @Test
    fun `weights dialog shows and hides`() {
        val viewModel = newViewModel()
        assertFalse(viewModel.uiState.value.showWeightsDialog)

        viewModel.send(BankIntent.ShowWeightsDialog)
        assertTrue(viewModel.uiState.value.showWeightsDialog)

        viewModel.send(BankIntent.HideWeightsDialog)
        assertFalse(viewModel.uiState.value.showWeightsDialog)

        // Saving weights also closes it
        viewModel.send(BankIntent.ShowWeightsDialog, BankIntent.UpdateWeights(listOf(60, 40)))
        assertFalse(viewModel.uiState.value.showWeightsDialog)
    }

    @Test
    fun `updating weights recalculates payouts immediately`() {
        tournamentPreferences.setBuyIn(20.0)
        val viewModel = newViewModel()
        (1..5).forEach { viewModel.send(BankIntent.BuyInToggled(it)) }

        viewModel.send(BankIntent.UpdateWeights(listOf(50, 50)))

        assertAmounts(listOf(50.0, 50.0), viewModel.uiState.value.payoutPositions.map { it.payout })
    }
}
