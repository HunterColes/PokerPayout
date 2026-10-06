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
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Seat draw (S14) inside the app's shell on every cell of the device matrix: text fits and is
 * never clipped at any scroll position, 48 dp targets that don't overlap. Goldens on
 * [DeviceMatrix.goldens]: `S14_seats_one_table` (the mockups' nine), `S14_seats_two_tables`
 * (fourteen at nine a table: 7 and 7) and `S14_button_draw` (both tables dealt, table 1 tied on
 * rank), plus `S14_seats_empty`, the screen before a draw. The name fields open and an out-of-date
 * draw with the players unfolded get the layout checks too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeatDrawScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun oneTable() = check("S14_seats_one_table", SeatDrawFixtures.oneTable)

    @Test
    fun twoTables() = check("S14_seats_two_tables", SeatDrawFixtures.twoTables)

    @Test
    fun buttonDraw() = check("S14_button_draw", SeatDrawFixtures.buttonDealt)

    @Test
    fun nothingDrawn() = check("S14_seats_empty", SeatDrawFixtures.empty)

    @Test
    fun namesOpen() = check(name = null, SeatDrawFixtures.editing)

    @Test
    fun staleDrawPlayersOpen() = check(name = null, SeatDrawFixtures.stale, playersOpen = true)

    private fun check(name: String?, state: SeatDrawUiState, playersOpen: Boolean = false) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                SeatDrawContent(state, onIntent = {}, onBack = {}, onShare = {}, playersOpen = playersOpen)
            }
        }
        val where = "${name ?: "S14 (layout only)"} on ${config.id}"
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
