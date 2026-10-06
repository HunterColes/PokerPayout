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
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tools tab (S7) inside the app's shell, on every cell of the device matrix: the layout checks
 * everywhere (text fits, nothing clipped at any scroll position, 48 dp targets that don't overlap),
 * and a golden on the [DeviceMatrix.goldens] cells. Hand ranks is there too, as the tool screen that
 * keeps Tools selected and shows the back arrow.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ToolsTabScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun toolsDefault() = check("S7_tools_default", ToolsHomeUiState(soundOn = true, volume = 0.7f))

    @Test
    fun toolsMuted() = check("S7_tools_muted", ToolsHomeUiState(soundOn = false, volume = 0.7f))

    @Test
    fun handRanksKeepsToolsSelected() {
        screen.compose.setContent { InAppShell(NavTab.Tools) { HandRanksScreen(onBack = {}) } }
        val where = "Hand ranks on ${config.id}"
        // The list itself is restyled in M6 (S12); here the shell, the top bar and the back arrow count.
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", "Shell_handranks", config)
    }

    private fun check(name: String, state: ToolsHomeUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                ToolsHomeContent(state = state, onIntent = {}, onOpenTool = {}, versionName = "1.3.0")
            }
        }
        val where = "$name on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
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
