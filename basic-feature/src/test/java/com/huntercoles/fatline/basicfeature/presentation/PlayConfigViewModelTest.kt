package com.huntercoles.fatline.basicfeature.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.fatline.basicfeature.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.fatline.core.preferences.BankPreferences
import com.huntercoles.fatline.core.preferences.TimerPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        tournamentPreferences.resetAllTournamentData()
        timerPreferences.resetAllTimerData()
        bankPreferences.resetAllBankData()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PlayConfigViewModel {
        val viewModel = PlayConfigViewModel(
            calculatePayoutsUseCase = CalculatePayoutsUseCase(),
            tournamentPreferences = tournamentPreferences,
            timerPreferences = timerPreferences,
            bankPreferences = bankPreferences
        )
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    @Test
    fun purchaseTotalsInfluenceSummaryAndPayouts() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(0.0)
        tournamentPreferences.setRebuyAmount(100.0)
        tournamentPreferences.setAddOnAmount(50.0)

        val viewModel = createViewModel()

        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        bankPreferences.savePlayerRebuys(playerId = 2, rebuys = 1)
        bankPreferences.savePlayerAddons(playerId = 1, addons = 1)
        bankPreferences.savePlayerAddons(playerId = 3, addons = 2)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.rebuyPurchases)
        assertEquals(3, state.addOnPurchases)

        val totalPayout = state.payouts.sumOf { it.payout }
        assertEquals(850.0, totalPayout, 0.001)
    }

    @Test
    fun clearingRebuyOrAddonAmountClearsPurchases() = runTest(testDispatcher) {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setBuyIn(50.0)
        tournamentPreferences.setRebuyAmount(25.0)
        tournamentPreferences.setAddOnAmount(10.0)

        val viewModel = createViewModel()

        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 1)
        bankPreferences.savePlayerAddons(playerId = 2, addons = 2)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptIntent(PlayConfigIntent.UpdateRebuyAmount(0.0))
        viewModel.acceptIntent(PlayConfigIntent.UpdateAddOnAmount(0.0))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.rebuyPurchases)
        assertEquals(0, state.addOnPurchases)
        assertEquals(0, bankPreferences.getPlayerRebuys(1))
        assertEquals(0, bankPreferences.getPlayerAddons(2))
    }
}