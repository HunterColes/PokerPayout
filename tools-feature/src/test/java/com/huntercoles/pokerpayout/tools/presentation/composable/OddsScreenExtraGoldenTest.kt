package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Two named goldens on cells [DeviceMatrix.goldens] leaves out, listed in [DeviceMatrix.pinned]:
 * `S8_odds_font2x`, the keypad at the largest font on a tall phone, and `S10_runout_land`, run it
 * out on the shortest landscape screen (a rotated small phone).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OddsScreenExtraGoldenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun golden() {
        if (config.device.isLandscape) {
            screen.compose.setContent {
                PokerTheme(reducedMotion = true) { RunItOutContent(OddsFixtures.runOutTurn, fourColour = false, onIntent = {}) }
            }
            screen.compose.onRoot().captureGolden(OddsScreenGoldenTest.GROUP, RUN_OUT_LAND, config)
        } else {
            screen.compose.setContent {
                PokerTheme(reducedMotion = true) { OddsCalculatorContent(OddsFixtures.typing, onIntent = {}) }
            }
            screen.compose.onRoot().captureGolden(OddsScreenGoldenTest.GROUP, ODDS_FONT_2X, config)
        }
    }

    companion object {
        private const val ODDS_FONT_2X = "S8_odds_font2x"
        private const val RUN_OUT_LAND = "S10_runout_land"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(
            DeviceMatrix.pinned.getValue(ODDS_FONT_2X) + DeviceMatrix.pinned.getValue(RUN_OUT_LAND),
        )
    }
}
