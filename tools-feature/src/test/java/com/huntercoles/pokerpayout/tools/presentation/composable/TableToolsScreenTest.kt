package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
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
 * The table tools inside the app's shell on every cell of the device matrix: text fits and is
 * never clipped at any scroll position of either pane, 48 dp targets that don't overlap. Goldens on
 * [DeviceMatrix.goldens]: `S20_outs_flop` (a flush draw facing a bet: both chances, the rules of
 * thumb and the verdict), `S21_side_pots` (an all-in, a bigger all-in, a fold and a bet nobody
 * matched: a main pot, a side pot and chips back) and `S22_deal` (three left from the Bank, ICM and
 * chip chop side by side, $50 saved for the winner). The other states get the layout checks only.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TableToolsScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun outsFlop() = check("S20_outs_flop") { OutsContent(TableToolFixtures.outsFlop, onIntent = {}, onBack = {}) }

    @Test
    fun outsTurn() = check(name = null) { OutsContent(TableToolFixtures.outsTurn, onIntent = {}, onBack = {}) }

    @Test
    fun outsCombo() = check(name = null) { OutsContent(TableToolFixtures.outsCombo, onIntent = {}, onBack = {}) }

    @Test
    fun sidePots() = check("S21_side_pots") { SidePotsContent(TableToolFixtures.sidePots, onIntent = {}, onBack = {}) }

    @Test
    fun sidePotsEmpty() = check(name = null) { SidePotsContent(TableToolFixtures.sidePotsEmpty, onIntent = {}, onBack = {}) }

    @Test
    fun sidePotsAllFolded() = check(name = null) {
        SidePotsContent(TableToolFixtures.sidePotsAllFolded, onIntent = {}, onBack = {})
    }

    @Test
    fun sidePotsTen() = check(name = null) { SidePotsContent(TableToolFixtures.sidePotsTen, onIntent = {}, onBack = {}) }

    @Test
    fun deal() = check("S22_deal") { DealContent(TableToolFixtures.deal, onIntent = {}, onBack = {}) }

    @Test
    fun dealNoChips() = check(name = null) { DealContent(TableToolFixtures.dealNoChips, onIntent = {}, onBack = {}) }

    @Test
    fun dealTyped() = check(name = null) { DealContent(TableToolFixtures.dealTyped, onIntent = {}, onBack = {}) }

    private fun check(name: String?, content: @Composable () -> Unit) {
        screen.compose.setContent { InAppShell(NavTab.Tools) { content() } }
        val where = "${name ?: "table tool (layout only)"} on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    companion object {
        const val GROUP = "screens"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
