package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.design.components.MoneyMeterPreview
import com.huntercoles.pokerpayout.core.design.components.PayoutStructureSheetPreview
import com.huntercoles.pokerpayout.core.design.components.PlaceBadgePreview
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The money components (M4): [MoneyMeterPreview], [PlaceBadgePreview] and the payout structure
 * sheet, on every cell of the device matrix (text fits, nothing clipped, 48 dp targets), with a
 * gallery golden on the [DeviceMatrix.goldens] cells, as `ComponentGoldenTest` does for M0's.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MoneyComponentsTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test fun moneyMeter() = check("MoneyMeter") { MoneyMeterPreview() }

    @Test fun placeBadge() = check("PlaceBadge") { PlaceBadgePreview() }

    /** Taller than a phone: the sheet scrolls, and its golden is the screen as it opens. */
    @Test fun payoutStructureSheet() = check("PayoutStructureSheet", wholeScreen = true) { PayoutStructureSheetPreview() }

    private fun check(name: String, wholeScreen: Boolean = false, gallery: @Composable () -> Unit) {
        screen.compose.setContent { Box(Modifier.testTag(GALLERY)) { gallery() } }
        val where = "$name on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
        if (config in DeviceMatrix.goldens) {
            val node = if (wholeScreen) screen.compose.onRoot() else screen.compose.onNodeWithTag(GALLERY)
            node.captureGolden("components", name, config)
        }
    }

    companion object {
        private const val GALLERY = "gallery"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
