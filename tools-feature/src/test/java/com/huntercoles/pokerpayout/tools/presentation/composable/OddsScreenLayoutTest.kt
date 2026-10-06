package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout checks for every odds state on every cell of the matrix (8 screens x 3 font scales): no
 * text clipped, cut off, ellipsized (bar the top bar's subtitle) or broken mid-word; nothing off
 * screen; every touch target at least 48 x 48 dp, laid out at full size, and none overlapping.
 * Each scrolling column is checked a screen at a time from top to bottom.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OddsScreenLayoutTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test fun empty() = odds("S8 empty", OddsFixtures.empty)

    @Test fun typing() = odds("S8 typing", OddsFixtures.typing)

    @Test fun estimate() = odds("S8 estimate", OddsFixtures.estimate)

    @Test fun flopExact() = odds("S9 flop exact", OddsFixtures.flopExact)

    @Test fun preflopRandom() = odds("S9 preflop random", OddsFixtures.preflopRandom)

    @Test fun fourPlayersFold() = odds("S9 four players", OddsFixtures.fourPlayersFold)

    @Test fun runOutTurn() = runOut("S10 turn", OddsFixtures.runOutTurn)

    @Test fun runOutRiver() = runOut("S10 river", OddsFixtures.runOutRiverP1)

    @Test fun runOutTwice() = runOut("S10 run twice", OddsFixtures.runOutTwice)

    private fun odds(what: String, state: OddsCalculatorUiState) = check(what) { OddsCalculatorContent(state, onIntent = {}) }

    private fun runOut(what: String, state: RunOutState) =
        check(what) { RunItOutContent(state, fourColour = false, onIntent = {}) }

    private fun check(what: String, content: @Composable () -> Unit) {
        screen.compose.setContent { PokerTheme(reducedMotion = true, content = content) }
        screen.compose.waitForIdle()
        val where = "$what on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
        forEachScrollPosition { scrolled -> LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $scrolled") }
    }

    /** Scrolls each scrolling column a screen at a time to its end, running [check] at every stop. */
    private fun forEachScrollPosition(check: (String) -> Unit) {
        val scrollers = screen.compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).fetchSemanticsNodes()
        val page = screen.compose.activity.window.decorView.height / 2f
        scrollers.forEachIndexed { index, node ->
            val matcher = SemanticsMatcher("scroller ${node.id}") { it.id == node.id }
            fun canScroll(): Boolean {
                val range = screen.compose.onNode(matcher).fetchSemanticsNode().config
                    .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                return range != null && range.value() < range.maxValue()
            }
            var stops = 0
            while (stops < MAX_STOPS && canScroll()) {
                screen.compose.onNode(matcher).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, page) }
                screen.compose.waitForIdle()
                stops++
                check("column $index scrolled ${stops}x")
            }
        }
    }

    companion object {
        private const val MAX_STOPS = 20

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
