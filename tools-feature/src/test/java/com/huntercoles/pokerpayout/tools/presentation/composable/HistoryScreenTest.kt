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
import com.huntercoles.pokerpayout.tools.presentation.HistoryUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History (S16, PP-037) inside the app's shell on every cell of the device matrix: text fits and is
 * never clipped at any scroll position, 48 dp targets that don't overlap. Goldens on
 * [DeviceMatrix.goldens]: `S16_history_list` (all time, two players level at the top),
 * `S16_history_night` (one night in full) and `S16_history_empty` (nothing saved yet). A year picked
 * gets the layout checks too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun nights() = check("S16_history_list", HistoryFixtures.list)

    @Test
    fun oneNight() = check("S16_history_night", HistoryFixtures.night)

    @Test
    fun nothingSaved() = check("S16_history_empty", HistoryFixtures.empty)

    @Test
    fun aYear() = check(name = null, HistoryFixtures.year)

    private fun check(name: String?, state: HistoryUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                HistoryContent(state, onIntent = {}, onBack = {}, onShare = {}, onExport = {})
            }
        }
        val where = "${name ?: "S16 (layout only)"} on ${config.id}"
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
