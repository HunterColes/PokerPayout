package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsGame.Companion.DANA
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

/**
 * The "Tip the dealer?" card on the Payouts tab (PP-112), through the real ViewModel, History and
 * saved state: it comes with the third night saved and not before, at most twice ever and three
 * nights apart, never on the app's first run, never while the clock runs, gone for good with one tap
 * on "Don't ask again" or "Leave a tip", and the same after process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutsTipTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var game: PayoutsGame
    private var nightNo = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        game = PayoutsGame().settled()
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

    /**
     * Saves [count] nights to History from the Payouts tab, each a night of its own (Dana's name
     * changes, as if another night was played and settled), the last one left on the tab.
     */
    private fun PayoutsViewModel.saveNights(count: Int): PayoutsViewModel = apply {
        repeat(count) {
            nightNo++
            game.bank.savePlayerName(DANA, "Dana $nightNo")
            state()
            send(PayoutsIntent.SaveNight)
            assertEquals(NightSave.Saved, state().night)
        }
    }

    private val PayoutsViewModel.card: Boolean get() = state().tipCard

    @Test
    fun theCardComesWithTheThirdNightSavedAndNotBefore() {
        val viewModel = game.laterRun().viewModel()
        assertFalse(viewModel.card)
        viewModel.saveNights(2)
        assertFalse("after two nights", viewModel.card)
        viewModel.saveNights(1)
        assertTrue("after the third", viewModel.card)
        assertEquals(3, game.nights.nights.value.size)
    }

    @Test
    fun neverOnTheFirstRun() {
        val viewModel = game.viewModel() // a new install's first run
        viewModel.saveNights(5)
        assertFalse(viewModel.card)
        // The next run's first save brings it: the nights saved on the first run counted
        game.restartProcess().saveNights(1).let { assertTrue(it.card) }
    }

    @Test
    fun neverWhileTheClockRuns() {
        val viewModel = game.laterRun().viewModel()
        game.timer.setTimerRunning(true)
        viewModel.saveNights(3)
        assertFalse("held back while the clock runs", viewModel.card)
        game.timer.setTimerRunning(false)
        assertFalse("a held-back card doesn't pop up later", viewModel.card)

        viewModel.saveNights(1)
        assertTrue("the next save with the clock stopped brings it", viewModel.card)
        game.timer.setTimerRunning(true) // the clock started again with the card up
        assertFalse(viewModel.card)
        game.timer.setTimerRunning(false)
        assertTrue(viewModel.card)
    }

    @Test
    fun dontAskAgainEndsItForGoodWithOneTap() {
        val viewModel = game.laterRun().viewModel().saveNights(3)
        assertTrue(viewModel.card)
        viewModel.send(PayoutsIntent.TipNever)
        assertFalse(viewModel.card)
        viewModel.saveNights(12)
        assertFalse(viewModel.card)
        assertFalse("after process death", game.restartProcess().saveNights(3).card)
    }

    @Test
    fun leaveATipEndsItForGoodToo() {
        val viewModel = game.laterRun().viewModel().saveNights(3)
        viewModel.send(PayoutsIntent.LeaveTip)
        assertFalse(viewModel.card)
        viewModel.saveNights(9)
        assertFalse(viewModel.card)
        assertTrue(game.tip.ask.value.stopped)
    }

    @Test
    fun atMostTwoCardsEverThreeNightsApart() {
        val viewModel = game.laterRun().viewModel().saveNights(3)
        assertTrue(viewModel.card)
        viewModel.send(PayoutsIntent.TipNotNow)
        assertFalse("Not now puts it away", viewModel.card)
        viewModel.saveNights(2)
        assertFalse("nights four and five: too soon", viewModel.card)
        viewModel.saveNights(1)
        assertTrue("night six: the second and last card", viewModel.card)
        viewModel.send(PayoutsIntent.TipNotNow)
        repeat(12) {
            viewModel.saveNights(1)
            assertFalse("night ${7 + it}: never a third", viewModel.card)
        }
        assertEquals(2, game.tip.ask.value.asksShown)
    }

    @Test
    fun theCardSurvivesProcessDeathWithItsNight() {
        game.laterRun().viewModel().saveNights(3)
        val afterDeath = game.restartProcess()
        assertEquals(NightSave.Saved, afterDeath.state().night)
        assertTrue("still under the same night after process death", afterDeath.card)

        afterDeath.send(PayoutsIntent.TipNotNow)
        assertFalse(game.restartProcess().card)
    }

    @Test
    fun theCardGoesWithItsNight() {
        val viewModel = game.laterRun().viewModel().saveNights(3)
        assertTrue(viewModel.card)
        game.nights.delete(game.nights.nights.value.first { it.players.any { p -> p.name == "Dana 3" } }.id)
        assertFalse("deleted from History", viewModel.card)
        assertEquals(NightSave.Offered, viewModel.state().night)
    }

    @Test
    fun aNewNightOnTheBankTakesTheCardAway() {
        val viewModel = game.laterRun().viewModel().saveNights(3)
        game.bank.resetAllBankData()
        assertEquals(NightSave.NotOver, viewModel.state().night)
        assertFalse(viewModel.card)
    }
}
