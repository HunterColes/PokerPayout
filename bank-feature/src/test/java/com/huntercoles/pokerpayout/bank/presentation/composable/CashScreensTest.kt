package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.huntercoles.pokerpayout.bank.presentation.cash.CashScenes
import com.huntercoles.pokerpayout.bank.presentation.cash.CashTestKit
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.bank.presentation.cash.CashViewModel
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
import com.huntercoles.pokerpayout.core.design.components.fillShellWidth
import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cash game (S13) inside the app's shell on every cell of the device matrix: text fits and
 * isn't clipped at any scroll position, and every target is 48 dp without overlapping. Goldens on
 * [DeviceMatrix.goldens], named after the mockup; the states come from the real ViewModel
 * ([CashScenes]). A sheet is drawn as it looks open, over the screen and its scrim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CashScreensTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: CashTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = CashTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    /** False once a sheet's golden is taken: a modal sheet's screen can't be reached, so only the sheet is checked. */
    private val screenBehind = mutableStateOf(true)

    private fun show(state: CashUiState, sheet: (@Composable () -> Unit)? = null) {
        screen.compose.setContent {
            InAppShell(NavTab.Bank) {
                Box(Modifier.fillMaxSize()) {
                    if (screenBehind.value) {
                        CashLedgerContent(
                            state = state.copy(sheet = null),
                            onIntent = {},
                            onShare = {},
                            modeSwitch = { BankModeSwitch(BankMode.CASH, onSwitch = {}) },
                        )
                    }
                    if (sheet != null) {
                        // Over the whole window, as a modal sheet's scrim is, past the centred column on tablets
                        Box(Modifier.fillMaxSize().fillShellWidth().background(Color.Black.copy(alpha = SCRIM)))
                        Box(Modifier.align(Alignment.BottomCenter)) { PokerSheetContent { sheet() } }
                    }
                }
            }
        }
        screen.compose.waitForIdle()
    }

    private fun check(what: String, sheet: Boolean = false) {
        val where = "$what on ${config.id}"
        if (sheet) {
            screenBehind.value = false
            screen.compose.waitForIdle()
        }
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
    }

    private fun golden(name: String, only: (ScreenConfig) -> Boolean = { true }) {
        if (config in DeviceMatrix.goldens && only(config)) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    /**
     * Scrolls the column holding the text [text] so it sits near the top (the rail beside it, in
     * landscape, scrolls too; a tablet's panes may have nothing to scroll).
     */
    private fun scrollToTop(text: String) {
        // Unclipped: a node below the fold has empty clipped bounds
        val target = screen.compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().positionInRoot
        val scrollers = screen.compose.onAllNodes(verticalScroller)
        val index = scrollers.fetchSemanticsNodes().indexOfFirst { node ->
            target.x >= node.positionInRoot.x && target.x < node.positionInRoot.x + node.size.width
        }
        if (index < 0) return
        val top = scrollers[index].fetchSemanticsNode().positionInRoot.y
        scrollers[index].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target.y - top - MARGIN_PX) }
        screen.compose.waitForIdle()
    }

    private val CashViewModel.ui get() = uiState.value

    @Test
    fun balanced() {
        show(CashScenes.balanced(kit).ui)
        golden("S13_cash_balanced")
        if (config.fontScale == 2.0f) {
            // The ledger, two lines a player at this size
            scrollToTop("PLAYER".takeIf { onScreen(it) } ?: "Dana")
            golden("S13_cash_font2x")
        }
        check("Cash game, balanced")
    }

    private fun onScreen(text: String) =
        screen.compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun off() {
        show(CashScenes.off(kit).ui)
        golden("S13_cash_off")
        check("Cash game, off by $5")
    }

    @Test
    fun settling() {
        show(CashScenes.settling(kit).ui)
        scrollToTop("SETTLE UP · 5 PAYMENTS")
        golden("S13_cash_settle")
        check("Cash game, settling up")
    }

    @Test
    fun split() {
        show(CashScenes.split(kit).ui)
        check("Cash game, difference split")
    }

    @Test
    fun counting() {
        show(CashScenes.counting(kit).ui)
        check("Cash game, counting")
    }

    @Test
    fun empty() {
        show(CashScenes.empty(kit).ui)
        check("Cash game, nobody yet")
    }

    @Test
    fun playerSheet() = with(kit) {
        val viewModel = CashScenes.balanced(kit)
        val theo = viewModel.state.players.first { it.name == "Theo" }
        show(viewModel.ui) { CashPlayerSheetContent(theo, viewModel.ui, onIntent = {}, onDone = {}) }
        golden("S13_cash_player")
        check("Player sheet", sheet = true)
    }

    @Test
    fun addPlayerSheet() {
        val state = CashScenes.balanced(kit).ui
        show(state) { CashAddPlayerSheetContent(state, onIntent = {}, onDismiss = {}) }
        check("Add-player sheet", sheet = true)
    }

    companion object {
        private const val SCRIM = 0.62f
        private const val MARGIN_PX = 24f
        private val verticalScroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(
            // PP_ONLY=phone-360x780_font1.0 renders one cell while iterating on a layout
            DeviceMatrix.all.filter { config -> System.getenv("PP_ONLY")?.let { config.id in it.split(",") } ?: true }
        )
    }
}
