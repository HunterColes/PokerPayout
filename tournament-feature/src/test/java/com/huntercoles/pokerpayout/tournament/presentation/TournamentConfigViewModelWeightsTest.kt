package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.tournament.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Payout weights on the Tournament tab, through the real [CalculatePayoutsUseCase]. */
@kotlinx.coroutines.ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class TournamentConfigViewModelWeightsTest {

    private lateinit var viewModel: TournamentConfigViewModel
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        viewModel = TournamentConfigViewModel(
            CalculatePayoutsUseCase(),
            tournamentPreferences,
            timerPreferences,
            BankPreferences(context)
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun assertAmounts(expected: List<Double>, actual: List<Double>) {
        assertEquals(expected.size, actual.size, "count of $actual")
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-9, "in $actual") }
    }

    @Test
    fun `default weights pay one place for the default five players`() {
        assertEquals(5, state.tournamentConfig.numPlayers)
        assertEquals(listOf(35), state.tournamentConfig.payoutWeights)
        // 5 x 20 buy-in, all to 1st
        assertEquals(listOf(100.0), state.payouts.map { it.payout })
    }

    @Test
    fun `changing player count refreshes default weights and payouts`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(18))

        val expected = listOf(35, 20, 15, 10, 8, 6)
        assertEquals(18, tournamentPreferences.getPlayerCount())
        assertEquals(expected, tournamentPreferences.getPayoutWeights())
        assertEquals(expected, state.tournamentConfig.payoutWeights)
        assertEquals(expected, state.payouts.map { it.weight })
        assertEquals(18 * 20.0, state.payouts.sumOf { it.payout }, 1e-9)
    }

    @Test
    fun `custom weights within the player count are all paid`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(6))
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(listOf(40, 30, 20, 10)))

        assertEquals(listOf(40, 30, 20, 10), tournamentPreferences.getPayoutWeights())
        // Prize pool 6 x 20 = 120
        assertAmounts(listOf(48.0, 36.0, 24.0, 12.0), state.payouts.map { it.payout })
        assertAmounts(listOf(40.0, 30.0, 20.0, 10.0), state.payouts.map { it.percentage })
    }

    @Ignore("PP-016: custom weights can pay more places than there are players. Enable when payouts are capped.")
    @Test
    fun `never pays more places than there are players`() {
        // 5 players (default), 6 custom places
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(listOf(35, 20, 15, 10, 8, 6)))

        assertEquals(5, state.payouts.size)
        assertEquals(100.0, state.payouts.sumOf { it.payout }, 1e-9)
    }

    @Test
    fun `reset restores default weights and the timer`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(listOf(50, 30, 20)))
        timerPreferences.setGameDurationMinutes(240)
        assertFalse(timerPreferences.isInDefaultState())

        viewModel.acceptIntent(TournamentConfigIntent.ConfirmReset)

        assertTrue(tournamentPreferences.isInDefaultState())
        assertTrue(timerPreferences.isInDefaultState())
        assertEquals(180, timerPreferences.getGameDurationMinutes())
        assertEquals(listOf(35), state.tournamentConfig.payoutWeights)
        assertEquals(listOf(100.0), state.payouts.map { it.payout })
    }
}
