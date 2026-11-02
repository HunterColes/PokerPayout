package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.testing.withCurrency
import com.huntercoles.pokerpayout.core.utils.AppCurrency
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
import org.robolectric.annotation.Config

/** #65: when the rounding step leaves only 1st paid, the Payouts tab flags it ([PayoutsUiState.stepPaysFirstAll]). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutsStepNoteTest {

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
    fun aPoolOfOneStepWithTwoPlacesPaysFirstAllAndIsFlagged() = withCurrency(AppCurrency.YEN) {
        val state = game.smallTable(players = 2, buyInCents = 25_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            .viewModel().state()
        assertEquals(50_000L, state.pool.prizePoolCents)
        assertEquals(listOf(50_000L), state.rows.map { it.amountCents })
        assertTrue(state.stepPaysFirstAll)
        assertFalse(state.onSmallestStep)
    }

    @Test
    fun twoPlacesUnderTwoStepsAreFlagged() = withCurrency(AppCurrency.YEN) {
        val state = game.smallTable(players = 2, buyInCents = 45_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            .viewModel().state()
        assertEquals(1, state.places)
        assertTrue(state.stepPaysFirstAll)
    }

    @Test
    fun aPoolOfExactlyTwoStepsPaysBothAndIsNotFlagged() = withCurrency(AppCurrency.YEN) {
        val state = game.smallTable(players = 2, buyInCents = 50_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            .viewModel().state()
        assertEquals(2, state.places)
        assertEquals(100_000L, state.rows.sumOf { it.amountCents })
        assertFalse(state.stepPaysFirstAll)
    }

    @Test
    fun aPoolUnderOneStepIsStillFlagged() = withCurrency(AppCurrency.YEN) {
        val state = game.yenSmallPool().viewModel().state()
        assertEquals(30_000L, state.pool.prizePoolCents)
        assertTrue(state.stepPaysFirstAll)
    }

    @Test
    fun aOneDollarPoolAtTheOneDollarStepIsFlaggedAsTheSmallestStep() {
        val state = game.smallTable(players = 2, buyInCents = 50L, rounding = PayoutRounding.ONE_DOLLAR)
            .viewModel().state()
        assertEquals(100L, state.pool.prizePoolCents)
        assertTrue(state.stepPaysFirstAll)
        assertTrue(state.onSmallestStep)
    }

    @Test
    fun switchingBackToASmallerStepHidesTheNote() = withCurrency(AppCurrency.YEN) {
        val viewModel = game.smallTable(players = 2, buyInCents = 25_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            .viewModel()
        assertTrue(viewModel.state().stepPaysFirstAll)
        viewModel.acceptIntent(PayoutsIntent.SelectRounding(PayoutRounding.ONE_DOLLAR))
        val state = viewModel.state()
        assertEquals(2, state.places)
        assertFalse(state.stepPaysFirstAll)
    }

    @Test
    fun onePlacePickedOnPurposeIsNotFlagged() {
        val state = game.smallTable(players = 3, buyInCents = 4_000L, rounding = PayoutRounding.FIVE_DOLLARS, places = 1)
            .viewModel().state()
        assertEquals(1, state.places)
        assertTrue(state.maxPlaces > 1)
        assertFalse(state.stepPaysFirstAll)
    }

    @Test
    fun noNoteWithOneOrNoPlayers() = withCurrency(AppCurrency.YEN) {
        val one = game.smallTable(players = 1, buyInCents = 25_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            .viewModel().state()
        assertFalse(one.stepPaysFirstAll)
        game.clear()
        val none = PayoutsGame().smallTable(players = 0, buyInCents = 25_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            .viewModel().state()
        assertFalse(none.stepPaysFirstAll)
    }
}
