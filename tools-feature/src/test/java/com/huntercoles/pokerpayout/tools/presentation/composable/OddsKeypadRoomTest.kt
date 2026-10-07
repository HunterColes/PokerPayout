package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.screenBounds
import com.huntercoles.pokerpayout.core.testing.unclippedBoundsInRoot
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Room for the hand while the keypad is open (the device matrix's finding on small-f2.0: the keypad
 * took the whole screen, and neither the seat being filled nor "Add player" could be reached). With
 * the keypad open, inside the app's shell (the tab bar on phones, the rail from 600 dp), on every
 * cell of the matrix:
 * - the slot the keypad is filling is wholly in view, above the keypad, without scrolling by hand;
 * - "Add player" scrolls wholly into view, above the keypad.
 *
 * The small cells run a second time in the window the device matrix's small profile leaves the app
 * ([WindowSize.MatrixSmallProfile]): its screen is 320 x 569 dp with 24 dp status and navigation
 * bars drawn over the app, so 521 dp, where Robolectric's small cell (320 x 640 dp, its bars outside
 * the window) leaves 584. That is where the matrix found it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OddsKeypadRoomTest(private val config: ScreenConfig, private val windowSize: WindowSize) {
    @get:Rule
    val screen = ScreenTestRule(config)

    /** The window the odds screen gets: the cell's own, or what the matrix's small profile leaves it. */
    enum class WindowSize { Cell, MatrixSmallProfile }

    @Test
    fun `an empty table shows the first slot and reaches Add player`() =
        check(OddsFixtures.empty, slot = "Player 1, card 1, empty. Next")

    @Test
    fun `typing the second hand shows its last slot and reaches Add player`() =
        check(OddsFixtures.typing, slot = "Player 2, card 2, empty. Next")

    private fun check(state: OddsCalculatorUiState, slot: String) {
        val frame = if (windowSize == WindowSize.MatrixSmallProfile) {
            Modifier.fillMaxWidth().height(MATRIX_SMALL_WINDOW)
        } else {
            Modifier.fillMaxSize()
        }
        screen.compose.setContent {
            Box(frame) { InAppShell(NavTab.Tools) { OddsCalculatorContent(state, onIntent = {}) } }
        }
        screen.compose.waitForIdle()
        val where = "on ${config.id}" + if (windowSize == WindowSize.MatrixSmallProfile) " (the matrix's small profile)" else ""
        screen.compose.onNodeWithText("Done").assertExists() // the keypad is open
        assertWhollyInView(screen.compose.onNodeWithContentDescription(slot), "The slot being filled ($slot) $where")
        val addPlayer = screen.compose.onNodeWithText("Add player")
        addPlayer.performScrollTo()
        screen.compose.waitForIdle()
        assertWhollyInView(addPlayer, "Add player, scrolled to, $where")
    }

    /**
     * [node] lies wholly inside the window and inside every scrolling container around it: on screen,
     * and (the odds page being the container above the keypad) not under the keypad.
     */
    private fun assertWhollyInView(node: SemanticsNodeInteraction, what: String) {
        val info = node.fetchSemanticsNode()
        val bounds = info.unclippedBoundsInRoot()
        val window = if (windowSize == WindowSize.MatrixSmallProfile) {
            Rect(0f, 0f, screenBounds().width, with(info.layoutInfo.density) { MATRIX_SMALL_WINDOW.toPx() })
        } else {
            screenBounds()
        }
        val view = generateSequence(info.parent) { it.parent }
            .filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            .fold(window) { visible, scroller -> visible.intersect(scroller.boundsInRoot) }
        assertTrue("$what is laid out at $bounds, but only $view can be seen", view.holds(bounds))
    }

    private fun Rect.holds(other: Rect): Boolean =
        other.left >= left - SLACK_PX && other.top >= top - SLACK_PX &&
            other.right <= right + SLACK_PX && other.bottom <= bottom + SLACK_PX

    companion object {
        /** The matrix's small profile: 569 dp tall, less the 24 dp status bar and 24 dp navigation bar over the app. */
        private val MATRIX_SMALL_WINDOW = 521.dp

        /** Rounding slack, in pixels. */
        private const val SLACK_PX = 1f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun configs(): List<Array<Any>> =
            DeviceMatrix.all.map { arrayOf<Any>(it, WindowSize.Cell) } +
                DeviceMatrix.all.filter { it.device == Device.SmallPhone }.map { arrayOf<Any>(it, WindowSize.MatrixSmallProfile) }
    }
}
