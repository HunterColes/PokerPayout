package com.huntercoles.pokerpayout.tournament.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The bounty type next to the bounty on the Tournament tab (PP-035): saved under a new key, fixed
 * from the first knockout, the mystery envelopes shown before the start, and a game saved before
 * bounty modes loading as Standard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TournamentBountyModeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context)
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
        tournamentPreferences.setPlayerCount(9)
        tournamentPreferences.setBountyPerPlayer(5.0)
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

    private fun TournamentConfigViewModel.send(vararg intents: TournamentConfigIntent) {
        intents.forEach { acceptIntent(it) }
        settle()
    }

    private val TournamentConfigViewModel.state get() = uiState.value

    @Test
    fun theBountyTypeIsSavedAndPartOfTheMoney() {
        val viewModel = createViewModel()
        assertEquals(BountyMode.STANDARD, viewModel.state.bountyMode)

        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.PROGRESSIVE))

        assertEquals(BountyMode.PROGRESSIVE, viewModel.state.bountyMode)
        assertEquals(BountyMode.PROGRESSIVE, tournamentPreferences.getMoneySettings().bountyMode)
        assertEquals(BountyMode.PROGRESSIVE, TournamentPreferences(context).getBountyMode())
        assertEquals("progressive", raw().getString("bounty_mode", null))
    }

    @Test
    fun theBountyTypeIsFixedFromTheFirstKnockout() {
        val viewModel = createViewModel()
        assertFalse(viewModel.state.bountyTypeLocked)

        // The Bank records a knockout
        bankPreferences.savePlayerOutStatus(9, true)
        bankPreferences.saveEliminationOrder(listOf(9))
        settle()
        assertTrue(viewModel.state.knockoutsRecorded)
        assertTrue(viewModel.state.bountyTypeLocked)

        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.MYSTERY))
        assertEquals(BountyMode.STANDARD, tournamentPreferences.getBountyMode())

        // Brought back in the Bank: nobody is out, so the type can change again
        bankPreferences.saveEliminationOrder(emptyList())
        settle()
        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.MYSTERY))
        assertEquals(BountyMode.MYSTERY, tournamentPreferences.getBountyMode())
    }

    @Test
    fun aMysteryBountyIsFixedOnceEnvelopesAreDrawnAndTheOthersAreNot() {
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        bankPreferences.saveEliminationOrder(listOf(9))
        val viewModel = createViewModel()
        assertTrue(viewModel.state.bountyAmountLocked)
        viewModel.send(TournamentConfigIntent.UpdateBountyPerPlayer(1_000))
        assertEquals(500L, tournamentPreferences.getMoneySettings().bountyCents)

        // A progressive bounty is worked out from the knockouts, so it can still change
        bankPreferences.saveEliminationOrder(emptyList())
        settle()
        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.PROGRESSIVE))
        bankPreferences.saveEliminationOrder(listOf(9))
        settle()
        assertFalse(viewModel.state.bountyAmountLocked)
        viewModel.send(TournamentConfigIntent.UpdateBountyPerPlayer(1_000))
        assertEquals(1_000L, tournamentPreferences.getMoneySettings().bountyCents)
    }

    @Test
    fun theMysteryEnvelopesAreShownBeforeTheStart() {
        val viewModel = createViewModel()
        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.MYSTERY))
        assertEquals(listOf(1_500L, 600L, 600L, 300L, 300L, 300L, 300L, 300L, 300L), viewModel.state.envelopes)
        // They follow the players and the bounty
        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(10))
        assertEquals(10, viewModel.state.envelopes.size)
        assertEquals(5_000L, viewModel.state.envelopes.sum())
    }

    /** The Bank knocks [victim] out, credits [eliminator], and draws the [cents] envelope for it. */
    private fun drawEnvelope(victim: Int, eliminator: Int, cents: Long) {
        bankPreferences.savePlayerOutStatus(victim, true)
        bankPreferences.savePlayerEliminatedBy(victim, eliminator)
        bankPreferences.savePlayerBountyDraw(victim, cents)
        bankPreferences.saveEliminationOrder(bankPreferences.getEliminationOrder() + victim)
        settle()
    }

    /**
     * Fewer players deal fewer envelopes: 9 at $5 make $45, 3 make $15, so the $15 envelope already
     * drawn would leave the champion $0. Once one is drawn the count can't go lower; a late entry
     * still can come in.
     */
    @Test
    fun theCountCantGoLowerOnceAMysteryEnvelopeIsDrawn() {
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        val viewModel = createViewModel()
        assertFalse(viewModel.state.playerCountCantGoLower)

        drawEnvelope(victim = 9, eliminator = 1, cents = 1_500)
        assertTrue(viewModel.state.envelopesDrawn)
        assertTrue(viewModel.state.playerCountCantGoLower)

        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(3))
        assertEquals(9, tournamentPreferences.getPlayerCount())
        assertEquals(9, viewModel.state.playerCount)
        assertEquals(1_500L, bankPreferences.getPlayerBountyDraw(9))
        assertEquals(listOf(9), bankPreferences.getEliminationOrder())

        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(10))
        assertEquals(10, tournamentPreferences.getPlayerCount())
        assertTrue("still locked at the new count", viewModel.state.playerCountCantGoLower)
        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(9))
        assertEquals(10, tournamentPreferences.getPlayerCount())
    }

    @Test
    fun theCountCanGoLowerAgainOnceTheDrawIsUndone() {
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        val viewModel = createViewModel()
        drawEnvelope(victim = 9, eliminator = 1, cents = 1_500)
        assertTrue(viewModel.state.playerCountCantGoLower)

        // The Bank's Undo (or "back in") takes the knockout back, envelope and all
        bankPreferences.savePlayerBountyDraw(9, null)
        bankPreferences.savePlayerEliminatedBy(9, null)
        bankPreferences.savePlayerOutStatus(9, false)
        bankPreferences.saveEliminationOrder(emptyList())
        settle()
        assertFalse(viewModel.state.playerCountCantGoLower)
        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(8))
        assertEquals(8, tournamentPreferences.getPlayerCount())

        // So does the Bank's reset
        drawEnvelope(victim = 8, eliminator = 1, cents = 1_500)
        assertTrue(viewModel.state.playerCountCantGoLower)
        bankPreferences.resetAllBankData()
        settle()
        assertFalse(viewModel.state.playerCountCantGoLower)
        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(7))
        assertEquals(7, tournamentPreferences.getPlayerCount())
    }

    @Test
    fun aNewTournamentStillResetsTheCountAfterADraw() {
        tournamentPreferences.setPlayerCount(20)
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        val viewModel = createViewModel()
        drawEnvelope(victim = 20, eliminator = 1, cents = 1_500)
        assertTrue(viewModel.state.playerCountCantGoLower)

        viewModel.send(TournamentConfigIntent.ShowResetDialog, TournamentConfigIntent.ConfirmReset)

        assertEquals(TournamentConfigUiState().playerCount, tournamentPreferences.getPlayerCount())
        assertFalse(viewModel.state.playerCountCantGoLower)
    }

    @Test
    fun onlyAMysteryDrawLocksTheCount() {
        // A knockout credited to nobody draws no envelope
        tournamentPreferences.setBountyMode(BountyMode.MYSTERY)
        bankPreferences.savePlayerOutStatus(9, true)
        bankPreferences.saveEliminationOrder(listOf(9))
        val viewModel = createViewModel()
        assertFalse(viewModel.state.playerCountCantGoLower)
        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(8))
        assertEquals(8, tournamentPreferences.getPlayerCount())

        // Standard bounties have no envelopes to protect
        bankPreferences.resetAllBankData()
        settle()
        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.STANDARD))
        drawEnvelope(victim = 8, eliminator = 1, cents = 500)
        assertFalse(viewModel.state.playerCountCantGoLower)
        viewModel.send(TournamentConfigIntent.UpdatePlayerCount(7))
        assertEquals(7, tournamentPreferences.getPlayerCount())
    }

    @Test
    fun aResetPutsTheBountyTypeBackToStandard() {
        val viewModel = createViewModel()
        viewModel.send(TournamentConfigIntent.UpdateBountyMode(BountyMode.MYSTERY))
        viewModel.send(TournamentConfigIntent.ShowResetDialog, TournamentConfigIntent.ConfirmReset)
        assertEquals(BountyMode.STANDARD, tournamentPreferences.getBountyMode())
        assertFalse(raw().contains("bounty_mode"))
        assertEquals(MoneySettings.DEFAULT, tournamentPreferences.getMoneySettings())
    }

    /**
     * Migration: a v1.3.7 save (and a v1.1.x one, still in Float dollars) has a bounty and no bounty
     * mode. It loads as Standard, and reading it writes nothing new.
     */
    @Test
    fun aGameSavedBeforeBountyModesLoadsAsStandard() {
        raw().edit().clear()
            .putInt("player_count", 9)
            .putLong("buy_in_cents", 4_000)
            .putLong("bounty_per_player_cents", 500)
            .commit()
        val saved = TournamentPreferences(context)
        assertEquals(BountyMode.STANDARD, saved.getBountyMode())
        assertEquals(BountyMode.STANDARD, saved.getMoneySettings().bountyMode)
        assertEquals(500L, saved.getMoneySettings().bountyCents)
        assertFalse(raw().contains("bounty_mode"))

        raw().edit().clear().putFloat("bounty_per_player", 5f).commit()
        val legacy = TournamentPreferences(context)
        assertEquals(500L, legacy.getMoneySettings().bountyCents)
        assertEquals(BountyMode.STANDARD, legacy.getMoneySettings().bountyMode)
        assertFalse(raw().contains("bounty_mode"))
    }

    @Test
    fun anUnknownStoredModeIsStandard() {
        raw().edit().putString("bounty_mode", "something-newer").commit()
        assertEquals(BountyMode.STANDARD, TournamentPreferences(context).getBountyMode())
    }

    private fun raw() = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
}
