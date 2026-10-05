package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.tournament.domain.usecase.CalculatePayoutsUseCase
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
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TournamentConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settle() = testDispatcher.scheduler.advanceUntilIdle()

    private fun createViewModel() = TournamentConfigViewModel(
        calculatePayoutsUseCase = CalculatePayoutsUseCase(),
        tournamentPreferences = tournamentPreferences,
        timerPreferences = timerPreferences,
        bankPreferences = bankPreferences
    ).also { settle() }

    @Test
    fun purchaseTotalsInfluenceSummaryAndPayouts() {
        tournamentPreferences.setPlayerCount(4)
        tournamentPreferences.setBuyIn(100.0)
        tournamentPreferences.setFoodPerPlayer(0.0)
        tournamentPreferences.setBountyPerPlayer(0.0)
        tournamentPreferences.setRebuyAmount(100.0)
        tournamentPreferences.setAddOnAmount(50.0)
        val viewModel = createViewModel()

        // Recorded on the Bank tab
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 2)
        bankPreferences.savePlayerRebuys(playerId = 2, rebuys = 1)
        bankPreferences.savePlayerAddons(playerId = 1, addons = 1)
        bankPreferences.savePlayerAddons(playerId = 3, addons = 2)
        settle()

        val state = viewModel.uiState.value
        assertEquals(3, state.rebuyPurchases)
        assertEquals(3, state.addOnPurchases)
        // 4 x 100 buy-ins + 3 x 100 rebuys + 3 x 50 add-ons, all to the single default place
        assertEquals(listOf(850.0), state.payouts.map { it.payout })
    }

    @Ignore(
        "PP-014: clearing the Rebuy/Add-on field emits 0, which wipes every recorded purchase. " +
            "Enable this when PP-014 lands; it is the spec for the fix."
    )
    @Test
    fun purchasesSurviveTheAmountBeingClearedAndRetyped() {
        tournamentPreferences.setPlayerCount(3)
        tournamentPreferences.setBuyIn(50.0)
        tournamentPreferences.setRebuyAmount(25.0)
        tournamentPreferences.setAddOnAmount(10.0)
        val viewModel = createViewModel()
        bankPreferences.savePlayerRebuys(playerId = 1, rebuys = 1)
        bankPreferences.savePlayerAddons(playerId = 2, addons = 2)
        settle()

        // The user clears each field and types the amount again
        viewModel.acceptIntent(TournamentConfigIntent.UpdateRebuyAmount(0.0))
        viewModel.acceptIntent(TournamentConfigIntent.UpdateAddOnAmount(0.0))
        viewModel.acceptIntent(TournamentConfigIntent.UpdateRebuyAmount(25.0))
        viewModel.acceptIntent(TournamentConfigIntent.UpdateAddOnAmount(10.0))
        settle()

        val state = viewModel.uiState.value
        assertEquals(1, state.rebuyPurchases)
        assertEquals(2, state.addOnPurchases)
        assertEquals(1, bankPreferences.getPlayerRebuys(1))
        assertEquals(2, bankPreferences.getPlayerAddons(2))
    }
}
