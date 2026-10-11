package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The house rake in the Tournament setup (PP-145): what the field sends is saved, comes off the pool
 * and the payout table the setup shows (and the clock's), and a new tournament takes it back to 0%.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TournamentRakeSetupTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var tournament: TournamentPreferences
    private lateinit var timer: TimerPreferences
    private lateinit var bank: BankPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs", "phone_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournament = TournamentPreferences(context).apply {
            setPlayerCount(10)
            setBuyInCents(4_000L)
            setFoodCents(500L)
            setBountyCents(1_000L)
        }
        timer = TimerPreferences(context)
        bank = BankPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = TournamentConfigViewModel(CalculatePayoutsUseCase(), tournament, timer, bank)
        .also { dispatcher.scheduler.advanceUntilIdle() }

    private fun TournamentConfigViewModel.send(vararg intents: TournamentConfigIntent) {
        intents.forEach(::acceptIntent)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `the rake typed in setup is saved and comes off the pool the setup pays from`() {
        val viewModel = viewModel()
        assertEquals(HouseRake.NONE, viewModel.uiState.value.money.rake)
        assertEquals(40_000L, viewModel.uiState.value.payoutTable.totalCents)

        viewModel.send(TournamentConfigIntent.UpdateRake(HouseRake.of(500)))
        assertEquals(HouseRake.of(500), tournament.getRake())
        val state = viewModel.uiState.value
        assertEquals(HouseRake.of(500), state.money.rake)
        assertEquals(40_000L, state.pool.prizeInCents)
        assertEquals(2_000L, state.pool.rakeCents)
        assertEquals(38_000L, state.pool.prizePoolCents)
        assertEquals(38_000L, state.payoutTable.totalCents)
        assertEquals(2_000L, state.payoutTable.rakeCents)
        // The bounties and the food are not raked
        assertEquals(10_000L, state.pool.bountyPoolCents)
        assertEquals(55_000L, state.pool.totalCents)

        viewModel.send(TournamentConfigIntent.UpdateRake(HouseRake.NONE))
        assertEquals(40_000L, viewModel.uiState.value.payoutTable.totalCents)
        assertEquals(0L, viewModel.uiState.value.payoutTable.rakeCents)
    }

    @Test
    fun `a new tournament takes the rake back to 0 percent`() {
        val viewModel = viewModel()
        viewModel.send(TournamentConfigIntent.UpdateRake(HouseRake.of(1_000)), TournamentConfigIntent.ConfirmReset)
        assertEquals(HouseRake.NONE, tournament.getRake())
        assertEquals(HouseRake.NONE, viewModel.uiState.value.money.rake)
        assertEquals(0L, viewModel.uiState.value.pool.rakeCents)
    }
}
