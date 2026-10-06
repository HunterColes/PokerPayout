package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.components.ContentMaxWidth
import com.huntercoles.pokerpayout.core.design.components.NavLayout
import com.huntercoles.pokerpayout.core.design.components.PokerAppShell
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.POKER_NAV_BAR_TAG
import com.huntercoles.pokerpayout.core.design.components.POKER_NAV_RAIL_TAG
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.components.navLayoutFor
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import com.huntercoles.pokerpayout.core.design.components.widthClassOf
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.screenBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The shell on every cell of the device matrix (8 screens x 3 font sizes): a bottom bar on phones
 * held upright and a rail from 600 dp (PP-087), the screen capped at 720 dp and centred, and the
 * shell's own text and touch targets passing the layout checks.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppShellTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun railFromSixHundredDpWideBarBelowThat() {
        show(selected = 3)
        val windowDp = windowWidthDp()
        val rail = navLayoutFor(windowDp.dp) == NavLayout.Rail
        // The matrix's phones held upright are under 600 dp and everything else is over it.
        assertEquals("rail on ${config.id} (window $windowDp dp)", config.device.widthDp >= 600, rail)

        val tabs = screen.compose.onAllNodes(isTab).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals("four tabs on ${config.id}", 4, tabs.size)
        if (rail) {
            screen.compose.onNodeWithTag(POKER_NAV_RAIL_TAG).assertExists()
            screen.compose.onNodeWithTag(POKER_NAV_BAR_TAG).assertDoesNotExist()
            assertTrue("rail tabs share the left edge", tabs.all { abs(it.left - tabs[0].left) < 1f && it.left < 1f })
            assertTrue("rail tabs run top down", tabs.zipWithNext().all { (a, b) -> b.top >= a.bottom - 1f })
        } else {
            screen.compose.onNodeWithTag(POKER_NAV_BAR_TAG).assertExists()
            screen.compose.onNodeWithTag(POKER_NAV_RAIL_TAG).assertDoesNotExist()
            assertTrue("bar tabs share one row", tabs.all { abs(it.top - tabs[0].top) < 1f })
            assertTrue("bar tabs run left to right", tabs.zipWithNext().all { (a, b) -> b.left >= a.right - 1f })
            val bottom = screenBounds().bottom
            assertTrue("the bar sits on the bottom edge", tabs.all { abs(it.bottom - bottom) < 1f })
        }
        screen.compose.onNode(hasText("Tools") and isTab).assertIsSelected()
    }

    @Test
    fun screenIsCappedAtTheContentWidthAndCentred() {
        show(selected = 0)
        val density = screen.compose.density
        val content = screen.compose.onNodeWithTag(SCREEN).fetchSemanticsNode().boundsInRoot
        val maxPx = with(density) { ContentMaxWidth.toPx() }
        assertTrue("screen is ${content.width}px wide on ${config.id}, over $maxPx", content.width <= maxPx + 1f)

        val window = screenBounds()
        val nav = screen.compose.onAllNodes(isTab).fetchSemanticsNodes().first().boundsInRoot
        val paneLeft = if (navLayoutFor(windowWidthDp().dp) == NavLayout.Rail) nav.right else 0f
        val pane = window.right - paneLeft
        if (pane > maxPx) {
            val left = content.left - paneLeft
            val right = window.right - content.right
            assertTrue("screen is centred on ${config.id}: $left px left, $right px right", abs(left - right) <= 2f)
        } else {
            assertEquals("screen fills the pane on ${config.id}", pane, content.width, 2f)
        }
    }

    @Test
    fun shellFitsTheScreen() {
        show(selected = 1)
        val where = "shell on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
    }

    @Test
    fun widthClassFollowsTheWindow() {
        val expected = when {
            config.device.widthDp < 360 -> WidthClass.Small
            config.device.widthDp < 600 -> WidthClass.Phone
            config.device.widthDp < 840 -> WidthClass.Medium
            else -> WidthClass.Expanded
        }
        assertEquals(config.id, expected, widthClassOf(config.device.widthDp.dp))
    }

    private fun show(selected: Int) {
        screen.compose.setContent {
            PokerTheme(reducedMotion = true) {
                PokerAppShell(items = pokerNavItems(), selectedIndex = selected, onSelect = {}) { StandInScreen() }
            }
        }
        screen.compose.waitForIdle()
    }

    private fun windowWidthDp(): Int = with(screen.compose.density) { screenBounds().width.toDp().value.toInt() }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    companion object {
        private const val SCREEN = "screen"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}

@Composable
private fun StandInScreen() {
    Column(Modifier.fillMaxSize().testTag("screen")) {
        PokerTopBar(title = "Tools", subtitle = "Everything works offline")
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("A stand-in screen.", style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite)
            PokerButton(text = "Do the thing", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
