package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import com.huntercoles.pokerpayout.tools.presentation.ColourEditor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chip set (S11) inside the app's shell on every cell of the device matrix: text fits and is
 * never clipped at any scroll position of either pane, 48 dp targets that don't overlap. Goldens on
 * [DeviceMatrix.goldens]: `S11_chipset_ok` (the home set covers 9 players), `S11_chipset_short`
 * (too few blacks), `S11_chipset_ok_end`, scrolled to the end of every pane (the color-up plan
 * and the stack settings), and `S11_chipset_settings`, the stack settings unfolded and keeping back
 * the Tournament's estimate. A first-use set and the colour sheet get the layout checks too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ChipSetScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun chipSetOk() = check("S11_chipset_ok") { ChipSetContent(ChipSetFixtures.ok, onIntent = {}, onBack = {}) }

    @Test
    fun chipSetShort() = check("S11_chipset_short") { ChipSetContent(ChipSetFixtures.short, onIntent = {}, onBack = {}) }

    @Test
    fun chipSetOkEnd() {
        render { ChipSetContent(ChipSetFixtures.ok, onIntent = {}, onBack = {}) }
        scrollEveryPaneToTheEnd()
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, "S11_chipset_ok_end", config)
    }

    @Test
    fun settingsOpenFirstUse() {
        val firstUse = ChipSetFixtures.state(
            ChipInventory.HOME_SET,
            reviewed = false,
            reserve = 3,
            stackOverride = 4_000,
            estimate = ChipSetFixtures.nightEstimate,
        )
        check(name = null) { ChipSetContent(firstUse, onIntent = {}, onBack = {}, settingsOpen = true) }
    }

    /**
     * PP-091 #3: the stack settings unfolded at the end of the page, keeping back the Tournament's
     * estimate (5 for rebuys, 9 for add-ons) with the line that says where it came from.
     */
    @Test
    fun chipSetSettings() {
        render { ChipSetContent(ChipSetFixtures.fromTournament, onIntent = {}, onBack = {}, settingsOpen = true) }
        scrollEveryPaneToTheEnd()
        val where = "S11_chipset_settings on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, "S11_chipset_settings", config)
    }

    @Test
    fun colourSheet() {
        val state: ChipSetUiState = ChipSetFixtures.ok
        check(name = null) {
            PokerSheetContent(title = colourSheetTitle(state, ColourEditor(ChipColour.Green))) {
                ColourEditorContent(state, ColourEditor(ChipColour.Green), onIntent = {})
            }
        }
    }

    private fun render(content: @Composable () -> Unit) {
        screen.compose.setContent { InAppShell(NavTab.Tools, content) }
        screen.compose.waitForIdle()
    }

    private fun check(name: String?, content: @Composable () -> Unit) {
        render(content)
        val where = "${name ?: "S11 (layout only)"} on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    /** Scrolls each scrolling pane all the way down (one on phones, two from 600 dp). */
    private fun scrollEveryPaneToTheEnd() {
        val panes = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)
        val count = screen.compose.onAllNodes(panes).fetchSemanticsNodes().size
        repeat(count) { i ->
            screen.compose.onAllNodes(panes)[i].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, FAR) }
            screen.compose.waitForIdle()
        }
    }

    companion object {
        private const val GROUP = "screens"
        private const val FAR = 100_000f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
