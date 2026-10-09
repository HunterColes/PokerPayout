package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.tools.presentation.TipUiState
import com.huntercoles.pokerpayout.tools.tip.TipCoin
import com.huntercoles.pokerpayout.tools.tip.TipLink
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tip the dealer (S28, PP-112) inside the app's shell on every cell of the device matrix: text fits
 * and is never clipped at any scroll position, 48 dp targets that don't overlap, every one named for
 * TalkBack, and AA contrast. Goldens on [DeviceMatrix.goldens]: `S28_tip` (the top: why a tip helps,
 * the donation page) and `S28_tip_copied` (scrolled to the Ethereum code, its address just copied).
 * A phone with no browser gets the layout checks too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TipScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun theTop() {
        render(TipUiState())
        golden("S28_tip")
        check("S28")
    }

    @Test
    fun anAddressCopied() {
        render(TipUiState(copied = TipCoin.ETH))
        scrollToTop("Ethereum (ETH)")
        golden("S28_tip_copied")
        check("S28, copied")
    }

    @Test
    fun noBrowser() {
        render(TipUiState(noBrowser = TipLink.DONATION_PAGE))
        check("S28, no browser for the page")
        shown.value = TipUiState(noBrowser = TipLink.IDEAS)
        screen.compose.waitForIdle()
        check("S28, no browser for an idea")
    }

    private val shown = mutableStateOf(TipUiState())

    private fun render(state: TipUiState) {
        shown.value = state
        screen.compose.setContent {
            InAppShell(NavTab.Tools) { TipContent(shown.value, onBack = {}, onCopy = {}, onOpen = {}) }
        }
        screen.compose.waitForIdle()
    }

    /** Scrolls the page the text [text] is on (not the rail, which scrolls too) so the text is at its top. */
    private fun scrollToTop(text: String) {
        val target = screen.compose.onNodeWithText(text).fetchSemanticsNode()
        val page = generateSequence(target.parent) { it.parent }.firstOrNull { scroller.matches(it) } ?: return
        // Laid-out positions: bounds are clipped to what shows, and a text far below shows nothing
        val distance = target.positionInRoot.y - page.positionInRoot.y
        screen.compose.onNode(SemanticsMatcher("the page") { it.id == page.id })
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        screen.compose.waitForIdle()
    }

    private fun golden(name: String) {
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, name, config)
    }

    private fun check(what: String) {
        val where = "$what on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    companion object {
        const val GROUP = "screens"

        private val scroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
