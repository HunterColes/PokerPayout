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
 * Tip the dealer (S25, PP-112) inside the app's shell on every cell of the device matrix: text fits
 * and is never clipped at any scroll position, 48 dp targets that don't overlap, every one named for
 * TalkBack, and AA contrast. Goldens on [DeviceMatrix.goldens]: `S25_tip` (the top: why a tip helps,
 * the donation page) and `S25_tip_copied` (scrolled to the Ethereum code, its address just copied).
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
        golden("S25_tip")
        check("S25")
    }

    @Test
    fun anAddressCopied() {
        render(TipUiState(copied = TipCoin.ETH))
        scrollToTop("Ethereum (ETH)")
        golden("S25_tip_copied")
        check("S25, copied")
    }

    @Test
    fun noBrowser() {
        render(TipUiState(noBrowser = TipLink.DONATION_PAGE))
        check("S25, no browser for the page")
        shown.value = TipUiState(noBrowser = TipLink.IDEAS)
        screen.compose.waitForIdle()
        check("S25, no browser for an idea")
    }

    private val shown = mutableStateOf(TipUiState())

    private fun render(state: TipUiState) {
        shown.value = state
        screen.compose.setContent {
            InAppShell(NavTab.Tools) { TipContent(shown.value, onBack = {}, onCopy = {}, onOpen = {}) }
        }
        screen.compose.waitForIdle()
    }

    /** Scrolls the page so the text [text] is at its top (as far as the page goes). */
    private fun scrollToTop(text: String) {
        if (screen.compose.onAllNodes(scroller).fetchSemanticsNodes().isEmpty()) return
        val page = screen.compose.onAllNodes(scroller)[0]
        val pageTop = page.fetchSemanticsNode().boundsInRoot.top
        val target = screen.compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target - pageTop) }
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
