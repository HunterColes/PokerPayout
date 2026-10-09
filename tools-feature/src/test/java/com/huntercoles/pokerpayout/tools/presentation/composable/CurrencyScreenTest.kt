package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.tools.presentation.CurrencyUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Currency (S25, PP-114) inside the app's shell on every cell of the device matrix: every name and
 * sample fits and is never clipped at any scroll position (the longest samples, "CHF 123’456.50" and
 * "₹1,23,456.50", at 2x text on the smallest phone), 48 dp rows that don't overlap, a TalkBack name for
 * each, and contrast. Goldens on [DeviceMatrix.goldens]: `S25_currency` (the dollar, as an install from
 * before has it) and `S25_currency_euro` (the euro picked, as a new install in Germany has it).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CurrencyScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun dollar() = check("S25_currency", CurrencyUiState(picked = AppCurrency.DOLLAR))

    @Test
    fun euro() = check("S25_currency_euro", CurrencyUiState(picked = AppCurrency.EURO))

    @Test
    fun yen() = check(name = null, CurrencyUiState(picked = AppCurrency.YEN))

    private fun check(name: String?, state: CurrencyUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                CurrencyContent(state = state, onIntent = {}, onBack = {})
            }
        }
        val where = "${name ?: "S25 (layout only)"} on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
