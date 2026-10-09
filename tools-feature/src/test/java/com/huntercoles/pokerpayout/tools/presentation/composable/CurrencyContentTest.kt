package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.NO_BREAK_SPACE
import com.huntercoles.pokerpayout.tools.presentation.CurrencyIntent
import com.huntercoles.pokerpayout.tools.presentation.CurrencyUiState
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Currency (S25, PP-114): each row is one radio TalkBack reads with its sample, and a tap picks it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class CurrencyContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<CurrencyIntent>()
    private var backs = 0

    private fun show(state: CurrencyUiState) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                CurrencyContent(state = state, onIntent = { intents += it }, onBack = { backs++ })
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    @Test
    fun everyCurrencyShowsWithASampleWrittenItsWay() {
        show(CurrencyUiState())
        listOf(
            "Dollar" to "$123,456.50",
            "Euro" to "123.456,50$NO_BREAK_SPACE€",
            "Euro, sign first" to "€123,456.50",
            "Rupee" to "₹1,23,456.50",
            "Yen" to "¥123,457",
            "No symbol" to "123,456.50",
        ).forEach { (name, sample) ->
            row(name).performScrollTo()
            compose.onNodeWithText(sample).assertExists()
        }
        compose.onNodeWithText("Whole amounts, no cents").performScrollTo()
        compose.onNodeWithText("Plain numbers, for points or chips").performScrollTo()
    }

    @Test
    fun theSavedPickIsSelectedAndATapPicksAnother() {
        show(CurrencyUiState(picked = AppCurrency.DOLLAR))
        row("Dollar").assertIsSelected()
        row("Euro").assertIsNotSelected()
        row("Euro").performClick()
        row("Krona or krone").performScrollTo().performClick()
        assertEquals(listOf(CurrencyIntent.Pick(AppCurrency.EURO), CurrencyIntent.Pick(AppCurrency.KRONA)), intents)
    }

    @Test
    fun backGoesBack() {
        show(CurrencyUiState())
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun theToolsRowSaysWhatTheCurrencyIs() {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ToolsHomeContent(
                    state = ToolsHomeUiState(currency = AppCurrency.EURO),
                    onIntent = {},
                    onOpenTool = {},
                    versionName = "",
                )
            }
        }
        compose.onNodeWithText("Euro · 1.234,50$NO_BREAK_SPACE€").performScrollTo()
    }
}
