package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Payout weights on the Tournament tab, through the real [CalculatePayoutsUseCase]. Amounts in cents. */
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
    private val amounts get() = state.payoutTable.places.map { it.amountCents }

    @Test
    fun `default weights pay two places for the default five players`() {
        assertEquals(5, state.playerCount)
        // PP-086: 5 players pay 2 places (it was 1, winner takes all)
        assertEquals(listOf(35, 20), state.config.payoutWeights)
        // 5 x 20 buy-in, 35:20 -> 2nd 36.36 -> $36, 1st $64
        assertEquals(listOf(6_400L, 3_600L), amounts)
    }

    @Test
    fun `changing player count refreshes default weights and payouts`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(18))

        val expected = listOf(35, 20, 15, 10, 8, 6)
        assertEquals(18, tournamentPreferences.getPlayerCount())
        assertEquals(expected, tournamentPreferences.getPayoutWeights())
        assertEquals(expected, state.config.payoutWeights)
        assertEquals(expected, state.payoutTable.places.map { it.weight })
        assertEquals(18 * 2_000L, state.payoutTable.totalCents)
    }

    @Test
    fun `custom weights within the player count are all paid`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(6))
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(listOf(40, 30, 20, 10)))

        assertEquals(listOf(40, 30, 20, 10), tournamentPreferences.getPayoutWeights())
        // Prize pool 6 x 20 = 120
        assertEquals(listOf(4_800L, 3_600L, 2_400L, 1_200L), amounts)
        assertEquals(listOf(40.0, 30.0, 20.0, 10.0), state.payoutTable.places.map { it.sharePercent })
        assertNull(state.payoutPreset)
    }

    @Test
    fun `never pays more places than there are players`() {
        // 5 players (default), 6 custom places (PP-016)
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(listOf(35, 20, 15, 10, 8, 6)))

        assertEquals(5, state.payoutTable.places.size)
        assertEquals(10_000L, state.payoutTable.totalCents)
    }

    @Test
    fun `presets follow the player count until the user picks the places`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(9))
        viewModel.acceptIntent(TournamentConfigIntent.ApplyPayoutPreset(PayoutPreset.FLAT))
        assertEquals(listOf(45, 32, 23), tournamentPreferences.getPayoutWeights())

        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(12))
        assertEquals(PayoutPreset.FLAT.weightsFor(4), tournamentPreferences.getPayoutWeights())

        viewModel.acceptIntent(TournamentConfigIntent.SetPaidPlaces(2))
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(24))
        assertEquals(PayoutPreset.FLAT.weightsFor(2), tournamentPreferences.getPayoutWeights())
    }

    @Test
    fun `the payout editor saves weights, preset and rounding together`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePlayerCount(10))
        viewModel.acceptIntent(TournamentConfigIntent.ShowWeightsEditor)
        assertTrue(state.showWeightsEditor)

        viewModel.acceptIntent(
            TournamentConfigIntent.UpdatePayoutSettings(
                PayoutSettings(
                    weights = listOf(60, 30, 10),
                    preset = PayoutPreset.TOP_HEAVY,
                    rounding = PayoutRounding.TEN_DOLLARS
                )
            )
        )

        assertFalse(state.showWeightsEditor)
        assertEquals(PayoutPreset.TOP_HEAVY, tournamentPreferences.getPayoutPreset())
        assertEquals(PayoutRounding.TEN_DOLLARS, tournamentPreferences.getPayoutRounding())
        // $200 at $10: 2nd 60, 3rd 20, 1st 120
        assertEquals(listOf(12_000L, 6_000L, 2_000L), amounts)
    }

    @Test
    fun `reset restores default weights, rounding and the timer`() {
        viewModel.acceptIntent(TournamentConfigIntent.UpdateWeights(listOf(50, 30, 20)))
        viewModel.acceptIntent(TournamentConfigIntent.UpdatePayoutRounding(PayoutRounding.FIVE_DOLLARS))
        timerPreferences.setGameDurationMinutes(240)
        assertFalse(timerPreferences.isInDefaultState())

        viewModel.acceptIntent(TournamentConfigIntent.ConfirmReset)

        assertTrue(tournamentPreferences.isInDefaultState())
        assertTrue(timerPreferences.isInDefaultState())
        assertEquals(180, timerPreferences.getGameDurationMinutes())
        assertEquals(listOf(35, 20), state.config.payoutWeights)
        assertEquals(PayoutRounding.ONE_DOLLAR, state.config.payoutRounding)
        assertEquals(listOf(6_400L, 3_600L), amounts)
    }
}
