package com.huntercoles.pokerpayout.tournament.presentation.payouts

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.testing.withCurrency
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import com.huntercoles.pokerpayout.core.utils.NO_BREAK_SPACE
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
 * PP-114: the Payouts tab in the host's currency, through its real ViewModel (the same mid-game night
 * as [PayoutsScreenTest]: a $450 pool, 3 places paid). Every amount on the tab, the rounding choices
 * and the share text follow the pick, and a pick made while the tab shows redraws it. The money itself
 * doesn't change: the same cents in every currency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h915dp-port-xhdpi")
class PayoutsCurrencyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var game: PayoutsGame

    @Before
    fun setUp() {
        game = PayoutsGame().midGame()
    }

    @After
    fun tearDown() {
        game.clear()
        MoneyFormat.current = AppCurrency.DEFAULT
    }

    private fun show(): PayoutsViewModel {
        val viewModel = game.viewModel()
        compose.setContent { PokerTheme(reducedMotion = true) { PayoutsScreen(viewModel) } }
        compose.waitForIdle()
        return viewModel
    }

    /** "450 €" as the screen writes it: the symbol held to the number by a no-break space. */
    private fun euros(amount: String) = "$amount$NO_BREAK_SPACE€"

    @Test
    fun inEurosEveryAmountOnTheTabIsInEuros() {
        withCurrency(AppCurrency.EURO) {
            show()
            compose.onNodeWithText(euros("450")).assertExists()
            val sources = "9 buy-ins ${euros("360")} · 1 rebuy ${euros("40")} · 5 add-ons ${euros("50")}"
            compose.onNodeWithText(sources).assertExists()
            compose.onNodeWithText("${euros("450")} prize pool · 3 places paid").assertExists()
            compose.onNodeWithText("Adds up to ${euros("450")}").performScrollTo().assertExists()
            // The rounding choices (5 € is also what lower places are rounded to, so it shows more than once)
            listOf("1", "5", "10").forEach { compose.onAllNodesWithText(euros(it)).onFirst().assertExists() }
            compose.onAllNodesWithText("$", substring = true).assertCountEqualsZero()
        }
    }

    @Test
    fun inEurosTheRoundingChoicesAreEurosAndSaveTheSameUnits() {
        withCurrency(AppCurrency.EURO) {
            show()
            compose.onNodeWithText(euros("1")).performClick()
            compose.waitForIdle()
            assertEquals(PayoutRounding.ONE_DOLLAR, game.tournament.getPayoutRounding())
            compose.onNodeWithText(euros("129")).assertExists()
        }
    }

    @Test
    fun inYenAmountsAreWholeYen() {
        withCurrency(AppCurrency.YEN) {
            show()
            compose.onNodeWithText("¥450 prize pool · 3 places paid").assertExists()
            compose.onNodeWithText("Adds up to ¥450").performScrollTo().assertExists()
            listOf("¥1", "¥5", "¥10").forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }
        }
    }

    @Test
    fun theShareTextIsInTheHostsCurrency() {
        withCurrency(AppCurrency.POUND) {
            show()
            compose.onNodeWithContentDescription("Share the payouts").performClick()
            compose.waitForIdle()
            val chooser = shadowOf(compose.activity).nextStartedActivity
            @Suppress("DEPRECATION")
            val text = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getStringExtra(Intent.EXTRA_TEXT)!!
            assertTrue(text, text.startsWith("Poker night payouts\nPrize pool £450"))
            assertTrue(text, '$' !in text)
        }
    }

    @Test
    fun aPickWhileTheTabShowsRedrawsIt() {
        show()
        compose.onNodeWithText("$450 prize pool · 3 places paid").assertExists()
        compose.runOnIdle { MoneyFormat.current = AppCurrency.RUPEE }
        compose.waitForIdle()
        compose.onNodeWithText("₹450 prize pool · 3 places paid").assertExists()
        compose.onNodeWithText("$450 prize pool · 3 places paid").assertDoesNotExist()
    }

    private fun SemanticsNodeInteractionCollection.assertCountEqualsZero() {
        val nodes = fetchSemanticsNodes()
        assertTrue("still a \"$\" on the tab: ${nodes.map { it.config }}", nodes.isEmpty())
    }
}
