package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
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
 * `S14_seats_font2x`: the dealt tables (the busiest seat rows: name, pills and card) at the largest
 * font on a tall phone, a cell [DeviceMatrix.goldens] leaves out.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeatDrawExtraGoldenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun golden() {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                SeatDrawContent(SeatDrawFixtures.buttonDealt, onIntent = {}, onBack = {}, onShare = {})
            }
        }
        screen.compose.onRoot().captureGolden(SeatDrawScreenTest.GROUP, "S14_seats_font2x", config)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(listOf(ScreenConfig(Device.TallPhone, 2.0f)))
    }
}
