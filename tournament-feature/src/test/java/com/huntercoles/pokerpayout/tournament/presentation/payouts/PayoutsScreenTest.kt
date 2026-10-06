package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Payouts tab does what the Payouts panel did in the Tournament tab, through the real
 * ViewModel and saved settings: presets, "Pay N", the structure editor, and the lock once the clock
 * has started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h915dp-port-xhdpi")
class PayoutsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var tournamentPreferences: TournamentPreferences
    private lateinit var timerPreferences: TimerPreferences
    private lateinit var bankPreferences: BankPreferences

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        listOf("tournament_prefs", "timer_prefs", "bank_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        tournamentPreferences = TournamentPreferences(context).apply {
            setPlayerCount(9)
            setBuyIn(40.0)
        }
        timerPreferences = TimerPreferences(context)
        bankPreferences = BankPreferences(context)
    }

    @After
    fun tearDown() = store.clear()

    @Test
    fun aPresetIsAppliedAndSaved() {
        val viewModel = show()
        compose.onNodeWithText("Top-heavy").performClick()
        compose.waitForIdle()
        assertEquals(PayoutPreset.TOP_HEAVY, viewModel.uiState.value.payoutPreset)
        assertEquals(PayoutPreset.TOP_HEAVY, tournamentPreferences.getPayoutPreset())
        compose.onNodeWithText("60%").assertExists()
    }

    @Test
    fun thePrizePoolAndEveryPaidPlaceShow() {
        show()
        compose.onNodeWithText("Prize pool").assertExists()
        compose.onNodeWithText("$360.00").assertExists()
        listOf("1st", "2nd", "3rd").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("3 of 9 paid", substring = true).assertExists()
    }

    @Test
    fun theStructureEditorOpensFromThePencil() {
        val viewModel = show()
        compose.onNodeWithContentDescription("Edit payout structure").performClick()
        compose.waitForIdle()
        assertTrue(viewModel.uiState.value.showWeightsEditor)
    }

    @Test
    fun onceTheClockHasStartedThePresetsAreLockedAndTheTopBarSaysWhy() {
        tournamentPreferences.setTournamentLocked(true)
        show()
        compose.onNodeWithText("Locked: the clock has started").assertExists()
        compose.onNodeWithText("Flat").assertIsNotEnabled()
    }

    private fun show(): TournamentConfigViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TournamentConfigViewModel(
                CalculatePayoutsUseCase(),
                tournamentPreferences,
                timerPreferences,
                bankPreferences
            ) as T
        }
        val viewModel = ViewModelProvider(store, factory)[TournamentConfigViewModel::class.java]
        compose.setContent { PokerTheme(reducedMotion = true) { PayoutsScreen(viewModel) } }
        compose.waitForIdle()
        return viewModel
    }
}
