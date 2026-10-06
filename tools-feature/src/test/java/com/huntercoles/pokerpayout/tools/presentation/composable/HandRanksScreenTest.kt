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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Hand ranks (S12) inside the app's shell (Tools selected, a back arrow) on every cell of the
 * device matrix: text fits and is never clipped at any scroll position, 48 dp targets that don't
 * overlap, and goldens `S12_ranks_default` and `S12_ranks_4colour` on [DeviceMatrix.goldens].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HandRanksScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun ranksDefault() = check("S12_ranks_default", fourColour = false)

    @Test
    fun ranksFourColour() = check("S12_ranks_4colour", fourColour = true)

    private fun check(name: String, fourColour: Boolean) {
        screen.compose.setContent { InAppShell(NavTab.Tools) { HandRanksContent(fourColour = fourColour, onBack = {}) } }
        val where = "$name on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
