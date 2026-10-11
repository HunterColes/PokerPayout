package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.tournament.presentation.composable.WinnerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The house rake on the Payouts tab (PP-145), on the mockups' game ([PayoutsGame]: $450 in for prizes,
 * Standard, rounded to $5). At 0% the tab is exactly what it was; at 5% the house keeps $22.50, the
 * places share $427.50 (217.50 / 120 / 90), and the two add up to the $450 to the cent. The bounties
 * are the same whatever the rake, and a night saved to History keeps its rake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutsRakeTest {

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

    private fun PayoutsViewModel.state(): PayoutsUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return uiState.value
    }

    @Test
    fun `at 0 percent the tab is exactly as it was`() {
        val state = game.midGame().viewModel().state()
        assertEquals(HouseRake.NONE, state.pool.rake)
        assertEquals(0L, state.pool.rakeCents)
        assertEquals(0L, state.table.rakeCents)
        assertEquals(45_000L, state.pool.prizePoolCents)
        assertEquals(listOf(22_500L, 13_000L, 9_500L), state.rows.map { it.amountCents })
        assertEquals("9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50", PayoutsShareText.poolSources(game.context, state))
        assertEquals(null, PayoutsShareText.rakeLine(game.context, state.pool))
    }

    @Test
    fun `a 5 percent rake comes off the pool, and the places plus the rake are what came in`() {
        game.tournament.setRake(HouseRake.of(500))
        val state = game.midGame().viewModel().state()
        assertEquals(45_000L, state.pool.prizeInCents)
        assertEquals(2_250L, state.pool.rakeCents)
        assertEquals(42_750L, state.pool.prizePoolCents)
        assertEquals(listOf(21_750L, 12_000L, 9_000L), state.rows.map { it.amountCents })
        assertEquals(2_250L, state.table.rakeCents)
        assertEquals(45_000L, state.table.totalCents + state.table.rakeCents)
        assertTrue(state.addsUp)
        // Under the pool: where it came from, then the rake on a line of its own
        assertEquals("9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50", PayoutsShareText.poolSources(game.context, state))
        assertEquals("5% house rake −$22.50", PayoutsShareText.rakeLine(game.context, state.pool))
        // Each preset's "what 1st gets" is paid from the pool after the rake too
        val calculate = CalculatePayoutsUseCase()
        PayoutPreset.entries.forEach { preset ->
            val first = calculate(state.pool, preset.weightsFor(3), 9, state.settings.rounding).amountFor(1)
            assertEquals("$preset", first, state.firstPlaceByPreset[preset])
        }
        assertEquals(state.rows.first().amountCents, state.firstPlaceByPreset[PayoutPreset.STANDARD])
    }

    @Test
    fun `the bounties are the same whatever the rake`() {
        val plain = game.finished().viewModel().state().bounties
        game.clear()
        game.tournament.setRake(HouseRake.of(2_500))
        val raked = game.viewModel().state()
        assertEquals(plain, raked.bounties)
        assertEquals(4_500L, raked.pool.bountyPoolCents)
    }

    /** The champion's screen (PP-111) is the tab's own picture of the night: the rake comes with it. */
    @Test
    fun `the champion's screen carries the rake and what came in`() {
        game.tournament.setRake(HouseRake.of(500))
        val winner = requireNotNull(WinnerModel.from(game.finished().viewModel().state()))
        assertEquals(21_750L, winner.prizeCents)
        assertEquals(2_250L, winner.rakeCents)
        assertEquals(45_000L, winner.takenInCents)
        assertEquals(winner.takenInCents, winner.rows.sumOf { it.amountCents } + winner.rakeCents)
    }

    @Test
    fun `the share text gives the pool after the rake, and the rake`() {
        game.tournament.setRake(HouseRake.of(500))
        val text = PayoutsShareText.build(game.context, game.midGame().viewModel().state())
        val pool = "Prize pool $427.50 (9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50 · 5% house rake −$22.50)"
        assertTrue(text, text.lines().contains(pool))
        assertTrue(text, text.lines().contains("1st: $217.50"))
    }

    @Test
    fun `a night saved with a rake keeps it in History`() {
        game.tournament.setRake(HouseRake.of(500))
        val viewModel = game.settled().viewModel()
        assertEquals(NightSave.Offered, viewModel.state().night)
        viewModel.acceptIntent(PayoutsIntent.SaveNight)
        assertEquals(NightSave.Saved, viewModel.state().night)
        val night = game.nights.nights.value.single()
        assertEquals(42_750L, night.prizePoolCents)
        assertEquals(2_250L, night.rakeCents)
        assertEquals(night.prizePoolCents, night.players.sumOf { it.prizeCents })
    }
}
