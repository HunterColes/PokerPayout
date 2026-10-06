package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.activity.ComponentActivity
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Payouts tab through its real ViewModel and saved settings (S6): presets, rounding, the places
 * stepper, the structure sheet, the lock while the clock runs, and Share as plain text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h915dp-port-xhdpi")
class PayoutsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var game: PayoutsGame

    @Before
    fun setUp() {
        game = PayoutsGame().midGame()
    }

    @After
    fun tearDown() = game.clear()

    private fun show(): PayoutsViewModel {
        val viewModel = game.viewModel()
        compose.setContent { PokerTheme(reducedMotion = true) { PayoutsScreen(viewModel) } }
        compose.waitForIdle()
        return viewModel
    }

    @Test
    fun aPresetIsAppliedAndSaved() {
        val viewModel = show()
        compose.onNodeWithText("Top-heavy").performClick()
        compose.waitForIdle()
        assertEquals(PayoutPreset.TOP_HEAVY, game.tournament.getPayoutPreset())
        assertEquals(PayoutPreset.TOP_HEAVY, viewModel.uiState.value.preset)
        compose.onNodeWithText("Top-heavy").assertIsSelected()
        // The preset shows it, and now 1st pays it
        compose.onAllNodesWithText("$270").assertCountEquals(2)
    }

    @Test
    fun roundingIsAppliedAndSaved() {
        show()
        compose.onNodeWithText("$1").performClick()
        compose.waitForIdle()
        assertEquals(PayoutRounding.ONE_DOLLAR, game.tournament.getPayoutRounding())
        compose.onNodeWithText("$129").assertExists()
    }

    @Test
    fun thePoolEveryPaidPlaceAndTheCheckShow() {
        show()
        compose.onNodeWithText("PRIZE POOL").assertExists()
        compose.onNodeWithText("$450").assertExists()
        compose.onNodeWithText("9 buy-ins $360 · 1 rebuy $40 · 5 add-ons $50").assertExists()
        listOf("1st", "2nd", "3rd").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Adds up to $450").performScrollTo().assertExists()
        compose.onNodeWithText("$450 prize pool · 3 places paid").assertExists()
    }

    @Test
    fun theStepperPaysAnotherPlace() {
        show()
        compose.onNodeWithContentDescription("Increase Places paid").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(4, game.tournament.getPayoutWeights().size)
        compose.onNodeWithText("4th").assertExists()
    }

    @Test
    fun theStructureSheetOpensFromThePencil() {
        val viewModel = show()
        compose.onNodeWithContentDescription("Edit payout structure").performClick()
        compose.waitForIdle()
        assertTrue(viewModel.uiState.value.showStructureSheet)
    }

    @Test
    fun onceTheClockRunsTheStructureIsLockedAndTheScreenSaysWhy() {
        game.tournament.setTournamentLocked(true)
        show()
        compose.onNodeWithText("Locked while the clock runs. Pause it to change the payouts.").assertExists()
        compose.onNodeWithText("Flat").assertIsNotEnabled()
        compose.onNodeWithText("$10").assertIsNotEnabled()
    }

    @Test
    fun shareSendsThePayoutsAsPlainText() {
        show()
        compose.onNodeWithContentDescription("Share the payouts").performClick()
        compose.waitForIdle()
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("Poker night payouts\nPrize pool $450"))
    }
}
