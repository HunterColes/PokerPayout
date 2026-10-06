package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.testing.Device
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
 * Two named goldens on screens [DeviceMatrix.goldens] leaves out: `S8_odds_font2x`, the keypad
 * at the largest font on a tall phone, and `S10_runout_land`, run it out on the shortest landscape
 * screen (a rotated small phone).
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
            screen.compose.onRoot().captureGolden(OddsScreenGoldenTest.GROUP, "S10_runout_land", config)
        } else {
            screen.compose.setContent {
                PokerTheme(reducedMotion = true) { OddsCalculatorContent(OddsFixtures.typing, onIntent = {}) }
            }
            screen.compose.onRoot().captureGolden(OddsScreenGoldenTest.GROUP, "S8_odds_font2x", config)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(
            listOf(ScreenConfig(Device.TallPhone, 2.0f), ScreenConfig(Device.SmallPhoneLandscape, 1.0f)),
        )
    }
}
