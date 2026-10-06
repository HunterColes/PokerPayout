package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsGame.Companion.DANA
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsGame.Companion.MARCUS
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
import org.robolectric.annotation.Config

/** The Payouts tab's ViewModel (S6) on the mockups' game, over real preferences. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutsViewModelTest {

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

    private fun PayoutsViewModel.send(intent: PayoutsIntent) {
        acceptIntent(intent)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun thePoolAndWhereItCameFrom() {
        val state = game.midGame().viewModel().state()
        assertEquals(45_000L, state.pool.prizePoolCents)
        assertEquals(36_000L, state.pool.buyInCents)
        assertEquals(1, state.rebuyCount)
        assertEquals(5, state.addOnCount)
        assertEquals("9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50", PayoutsShareText.poolSources(game.context, state))
    }

    @Test
    fun theTableAddsUpAndTheSharesAreOfTheRoundedAmounts() {
        val state = game.midGame().viewModel().state()
        assertEquals(listOf(22_500L, 13_000L, 9_500L), state.rows.map { it.amountCents })
        assertTrue(state.addsUp)
        // 130 of 450 is 28.9%, not the 20/70 = 28.6% of the weights
        assertEquals(listOf(50.0, 28.89, 21.11), state.rows.map { Math.round(it.sharePercent * 100) / 100.0 })
        assertEquals(listOf(null, null, null), state.rows.map { it.holderName })
    }

    @Test
    fun eachPresetShowsWhatFirstWouldGet() {
        val state = game.midGame().viewModel().state()
        assertEquals(
            mapOf(PayoutPreset.TOP_HEAVY to 27_000L, PayoutPreset.STANDARD to 22_500L, PayoutPreset.FLAT to 20_000L),
            state.firstPlaceByPreset
        )
    }

    @Test
    fun aPresetIsAppliedAtTheSamePlacesAndSaved() {
        val viewModel = game.midGame().viewModel()
        viewModel.send(PayoutsIntent.SelectPreset(PayoutPreset.TOP_HEAVY))
        val state = viewModel.state()
        assertEquals(PayoutPreset.TOP_HEAVY, state.preset)
        assertEquals(listOf(60, 30, 10), game.tournament.getPayoutWeights())
        assertEquals(listOf(27_000L, 13_500L, 4_500L), state.rows.map { it.amountCents })
    }

    @Test
    fun roundingIsSaved() {
        val viewModel = game.midGame().viewModel()
        viewModel.send(PayoutsIntent.SelectRounding(PayoutRounding.ONE_DOLLAR))
        assertEquals(PayoutRounding.ONE_DOLLAR, game.tournament.getPayoutRounding())
        // 2nd 128.57 -> $129, 3rd 96.43 -> $96, 1st $225
        assertEquals(listOf(22_500L, 12_900L, 9_600L), viewModel.state().rows.map { it.amountCents })
    }

    @Test
    fun thePlacesStepperKeepsThePresetAndNeverPaysMorePlacesThanPlayers() {
        val viewModel = game.midGame().viewModel()
        viewModel.send(PayoutsIntent.SetPlaces(4))
        assertEquals(listOf(35, 20, 15, 10), game.tournament.getPayoutWeights())
        assertEquals(PayoutPreset.STANDARD, viewModel.state().preset)

        viewModel.send(PayoutsIntent.SetPlaces(30))
        assertEquals(9, viewModel.state().places)
    }

    @Test
    fun customWeightsGrowWithThePlacesAndStayValid() {
        val viewModel = game.midGame().viewModel()
        viewModel.send(PayoutsIntent.SaveStructure(PayoutSettings(
            listOf(5, 3, 1),
            preset = null,
            rounding = PayoutRounding.FIVE_DOLLARS
        )))
        assertNull(viewModel.state().preset)

        viewModel.send(PayoutsIntent.SetPlaces(5))
        val weights = game.tournament.getPayoutWeights()
        assertEquals(5, weights.size)
        assertTrue("strictly falling: $weights", weights.zipWithNext().all { (a, b) -> a > b })
        assertTrue(viewModel.state().addsUp)
    }

    @Test
    fun thePayAboutAThirdShortcutUsesPP086() {
        game.tournament.setPlayerCount(5)
        val state = game.viewModel().state()
        assertEquals(2, state.recommendedPlaces)
    }

    @Test
    fun theStructureIsLockedWhileTheClockRuns() {
        game.midGame()
        game.tournament.setTournamentLocked(true)
        val viewModel = game.viewModel()
        assertTrue(viewModel.state().isLocked)

        viewModel.send(PayoutsIntent.SelectPreset(PayoutPreset.FLAT))
        viewModel.send(PayoutsIntent.SelectRounding(PayoutRounding.TEN_DOLLARS))
        viewModel.send(PayoutsIntent.SetPlaces(5))

        assertEquals(listOf(35, 20, 15), game.tournament.getPayoutWeights())
        assertEquals(PayoutRounding.FIVE_DOLLARS, game.tournament.getPayoutRounding())
        assertFalse(viewModel.state().canPlaceMore)
    }

    @Test
    fun theStructureSheetOpensAndSaves() {
        val viewModel = game.midGame().viewModel()
        viewModel.send(PayoutsIntent.ShowStructure)
        assertTrue(viewModel.state().showStructureSheet)
        viewModel.send(PayoutsIntent.SaveStructure(PayoutSettings(listOf(50, 30, 20), null, PayoutRounding.FIVE_DOLLARS)))
        assertFalse(viewModel.state().showStructureSheet)
        assertEquals(listOf(50, 30, 20), game.tournament.getPayoutWeights())
    }

    @Test
    fun theBubbleCountsDownToTheMoney() {
        val bubble = game.midGame().viewModel().state().bubble
        assertEquals((9 downTo 1).toList(), bubble.seats.map { it.place })
        assertEquals(
            listOf(SeatState.Out, SeatState.Out) + List(4) { SeatState.Bubble } + List(3) { SeatState.Money },
            bubble.seats.map { it.state }
        )
        assertEquals(4, bubble.moreOutToMoney)
        assertEquals(7, bubble.nextOutPlace)
    }

    @Test
    fun bountiesClaimedSoFar() {
        val bounties = game.midGame().viewModel().state().bounties
        assertEquals(500L, bounties.perHeadCents)
        assertEquals(
            listOf(BountyClaim("Dana", listOf("Ben"), 500L), BountyClaim("Marcus", listOf("Rita"), 500L)),
            bounties.claims
        )
        assertEquals(3_500L, bounties.stillOutCents)
        assertEquals(4_500L, bounties.foodCents)
    }

    @Test
    fun whenItIsOverTheRowsHaveNamesAndTheChampionHasTheRestOfTheBounties() {
        val state = game.finished().viewModel().state()
        assertEquals(listOf("Dana", "Marcus", "Priya"), state.rows.map { it.holderName })
        assertNull(state.bubble.nextOutPlace)
        assertEquals(
            listOf(SeatState.Cashed, SeatState.Cashed, SeatState.Cashed),
            state.bubble.seats.takeLast(3).map { it.state }
        )
        with(state.bounties) {
            assertEquals("Dana", championName)
            // Her own bounty plus Priya's, which nobody claimed
            assertEquals(1_000L, championCents)
            assertEquals(0L, stillOutCents)
            assertEquals(4_500L, claims.sumOf { it.cents } + championCents)
        }
    }

    @Test
    fun rebuysCountAtThePriceTheyWereBoughtAt() {
        game.midGame()
        game.tournament.setRebuyAmount(100.0)
        val state = game.viewModel().state()
        // Marcus's rebuy cost $40; the new amount doesn't change it (PP-085)
        assertEquals(4_000L, state.pool.rebuyCents)
        assertEquals(45_000L, state.pool.prizePoolCents)
    }

    @Test
    fun theShareTextHasThePoolThePlacesTheStructureAndTheBounties() {
        val text = PayoutsShareText.build(game.context, game.midGame().viewModel().state())
        assertEquals(
            """
            Poker night payouts
            Prize pool $450 (9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50)
            1st: $225
            2nd: $130
            3rd: $95
            Standard, rounded to $5
            Bounties, $5 a head: Dana $5 (Ben) · Marcus $5 (Rita)
            Food: $45, kept out of the prize pool.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun theShareTextNamesTheWinnersOnceTheyAreKnown() {
        val text = PayoutsShareText.build(game.context, game.finished().viewModel().state())
        assertTrue(text, text.contains("1st: $225 · Dana"))
        assertTrue(text, text.contains("2nd: $130 · Marcus"))
        assertTrue(text, text.contains("3rd: $95 · Priya"))
        assertTrue(text, text.contains("Dana $10 (own bounty and unclaimed)"))
    }

    @Test
    fun theTableFollowsTheBankAsPlayersGoOut() {
        val viewModel = game.midGame().viewModel()
        assertNull(viewModel.state().rows.last().holderName)
        game.knockOut(DANA, by = MARCUS)
        val state = viewModel.state()
        assertEquals(SeatState.Out, state.bubble.seats.first { it.place == 7 }.state)
        assertEquals(3, state.bubble.moreOutToMoney)
        assertEquals(
            listOf(BountyClaim("Marcus", listOf("Rita", "Dana"), 1_000L)),
            state.bounties.claims.filter { it.name == "Marcus" }
        )
    }
}
