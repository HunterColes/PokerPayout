package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.tournament.presentation.composable.WinnerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-111: the champion's screen is the Payouts tab's picture of the night ([PayoutsGame]: Dana wins
 * a $450 pool, Standard to $5), so its names and amounts are the Bank's to the cent, and "Save this
 * night" on it is the Payouts tab's own save: offered once everyone is paid, then saved, once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WinnerModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var game: PayoutsGame

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        game = PayoutsGame()
    }

    @After
    fun tearDown() {
        game.clear()
        Dispatchers.resetMain()
    }

    private fun PayoutsViewModel.winner(): WinnerModel? {
        dispatcher.scheduler.advanceUntilIdle()
        return WinnerModel.from(uiState.value)
    }

    @Test
    fun noChampionNoScreen() {
        assertNull(game.midGame().viewModel().winner())
    }

    @Test
    fun theChampionAndEveryPaidPlaceAsThePayoutsTabHasThem() {
        val winner = game.finished().viewModel().winner()!!
        assertEquals("Dana", winner.championName)
        assertEquals(22_500L, winner.prizeCents)
        assertEquals(listOf("Dana", "Marcus", "Priya"), winner.rows.map { it.holderName })
        assertEquals(listOf(22_500L, 13_000L, 9_500L), winner.rows.map { it.amountCents })
        assertEquals("the table adds up to the pool", 45_000L, winner.rows.sumOf { it.amountCents })
        assertEquals("her own bounty and the unclaimed one", 1_000L, winner.bountyCents)
        assertEquals("Dana, Marcus and Priya are still to be paid", NightSave.NotOver, winner.night)
    }

    @Test
    fun onceEveryoneIsPaidItSavesTheNightOnce() {
        val viewModel = game.settled().viewModel()
        assertEquals(NightSave.Offered, viewModel.winner()!!.night)
        viewModel.acceptIntent(PayoutsIntent.SaveNight)
        assertEquals(NightSave.Saved, viewModel.winner()!!.night)
        viewModel.acceptIntent(PayoutsIntent.SaveNight)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, game.nights.nights.value.size)
    }
}
