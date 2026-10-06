package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the odds screens on [DeviceMatrix.goldens]: S8 (entering cards), S9 (results and
 * insight) and S10 (run it out), named after the mockup IDs:
 * `tools-feature/src/test/screenshots/screens/S9_odds_flop_exact/S9_odds_flop_exact_phone-360x780_font1.0.png`.
 * Each captures the window as the player first sees it; `S9_odds_flop_insight` scrolls to the
 * bottom to show the insight panel. Landscape configs show run it out's two-pane layout.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OddsScreenGoldenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test fun s8Empty() = odds("S8_odds_empty", OddsFixtures.empty)

    @Test fun s8Typing() = odds("S8_odds_typing", OddsFixtures.typing)

    @Test fun s8Estimate() = odds("S8_odds_estimate", OddsFixtures.estimate)

    @Test fun s9FlopExact() = odds("S9_odds_flop_exact", OddsFixtures.flopExact)

    @Test
    fun s9FlopInsight() {
        render { OddsCalculatorContent(OddsFixtures.flopExact, onIntent = {}) }
        scrollToEnd()
        screen.compose.onRoot().captureGolden(GROUP, "S9_odds_flop_insight", config)
    }

    @Test fun s9PreflopRandom() = odds("S9_odds_preflop_random", OddsFixtures.preflopRandom)

    @Test fun s9FourPlayersFold() = odds("S9_odds_four_players_fold", OddsFixtures.fourPlayersFold)

    @Test fun s10Turn() = runOut("S10_runout_turn", OddsFixtures.runOutTurn)

    @Test fun s10RiverP1() = runOut("S10_runout_river_p1", OddsFixtures.runOutRiverP1)

    @Test fun s10RiverP2() = runOut("S10_runout_river_p2", OddsFixtures.runOutRiverP2)

    private fun odds(name: String, state: OddsCalculatorUiState) {
        render { OddsCalculatorContent(state, onIntent = {}) }
        screen.compose.onRoot().captureGolden(GROUP, name, config)
    }

    private fun runOut(name: String, state: RunOutState) {
        render { RunItOutContent(state, fourColour = false, onIntent = {}) }
        screen.compose.onRoot().captureGolden(GROUP, name, config)
    }

    private fun render(content: @Composable () -> Unit) {
        screen.compose.setContent { PokerTheme(reducedMotion = true, content = content) }
        screen.compose.waitForIdle()
    }

    /** Scrolls the screen's scrolling column all the way down. */
    private fun scrollToEnd() {
        screen.compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, FAR) }
        screen.compose.waitForIdle()
    }

    companion object {
        const val GROUP = "screens"
        private const val FAR = 100_000f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.goldens)
    }
}
