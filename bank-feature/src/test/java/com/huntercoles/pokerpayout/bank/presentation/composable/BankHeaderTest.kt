package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.bank.presentation.PlayerData
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.withCurrency
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Bank header's subtitle (#24): the money collected is never cut, on any cell of the device
 * matrix, with two-digit player counts, a four-figure amount and the yen. Where the line doesn't fit
 * beside Undo, Mute and More, it moves to its own full-width row under the title and wraps; the three
 * icons keep their 48 dp targets.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BankHeaderTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private fun players(count: Int, left: Int, bought: Int = count) = List(count) { i ->
        PlayerData(id = i + 1, name = "Player ${i + 1}", buyIn = i < bought, out = i >= left)
    }

    /** Shows [state], then fails if the subtitle [text] is ellipsized or cut off anywhere on this cell. */
    private fun assertSubtitleInFull(state: BankUiState, text: String) {
        screen.compose.setContent { InAppShell(NavTab.Bank) { BankContent(state = state, onIntent = {}) } }
        screen.compose.waitForIdle()
        val where = "\"$text\" on ${config.id}"
        val node = screen.compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode(where)
        node.textLayouts().forEach { layout ->
            val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            assertFalse("$where is ellipsized", ellipsized)
            assertFalse("$where is cut off after ${layout.lineCount} line(s)", layout.multiParagraph.didExceedMaxLines)
        }
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
    }

    @Test
    fun smallPayment() = assertSubtitleInFull(
        BankUiState(players = players(count = 5, left = 5, bought = 1), totalPaidInCents = 2_500L),
        "5 of 5 left · \$25 collected",
    )

    @Test
    fun bigAmountTwoDigitCount() = assertSubtitleInFull(
        BankUiState(players = players(count = 12, left = 5), totalPaidInCents = 123_450L),
        "5 of 12 left · \$1,234.50 collected",
    )

    @Test
    fun yen() = withCurrency(AppCurrency.YEN) {
        assertSubtitleInFull(
            BankUiState(players = players(count = 12, left = 10), totalPaidInCents = 12_345_600L),
            "10 of 12 left · ¥123,456 collected",
        )
    }

    @Test
    fun beforeBuyIns() = assertSubtitleInFull(
        BankUiState(players = players(count = 12, left = 12, bought = 0)),
        "12 players · \$0 to collect",
    )

    @Test
    fun finished() = assertSubtitleInFull(
        BankUiState(
            players = players(count = 12, left = 1).map { if (it.id == 1) it.copy(name = "Alexandria") else it },
            totalPaidInCents = 123_450L,
            championId = 1,
        ),
        "Finished · Alexandria wins · all paid",
    )

    /** The node's text laid out again with its own style (as LayoutAssertions does: Compose 1.7 hands out 14 px). */
    private fun SemanticsNode.textLayouts(): List<TextLayoutResult> {
        val reported = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(reported)
        val density = layoutInfo.density
        return reported.map { result ->
            val input = result.layoutInput
            TextMeasurer(input.fontFamilyResolver, density, input.layoutDirection, cacheSize = 0).measure(
                text = input.text,
                style = input.style,
                overflow = input.overflow,
                softWrap = input.softWrap,
                maxLines = input.maxLines,
                constraints = input.constraints,
                layoutDirection = input.layoutDirection,
                density = density,
                fontFamilyResolver = input.fontFamilyResolver,
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
