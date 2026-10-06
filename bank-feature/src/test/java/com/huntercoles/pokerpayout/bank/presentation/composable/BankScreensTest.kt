package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankScenes
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankTestKit
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.bank.presentation.Purchase
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.Device
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
 * The Bank (S5 v2, S5b, S5c, Z2, Z5) inside the app's shell on every cell of the device matrix: text
 * fits and isn't clipped at any scroll position, and every target is 48 dp without overlapping.
 * Goldens on [DeviceMatrix.goldens], named after the mockups (Z2, Z5 and the 200% one on their
 * [DeviceMatrix.pinned] cells); the states come from the real ViewModel ([BankScenes]). Sheets are
 * drawn as they look open, over the screen and its scrim (a modal window doesn't capture under
 * Robolectric).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BankScreensTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = BankTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    /** False once a sheet's golden is taken: a modal sheet's screen can't be reached, so only the sheet is checked. */
    private val screenBehind = mutableStateOf(true)

    private fun show(state: BankUiState, sheet: (@Composable () -> Unit)? = null) {
        screen.compose.setContent {
            InAppShell(NavTab.Bank) {
                Box(Modifier.fillMaxSize()) {
                    if (screenBehind.value) {
                        // As the app shows it: the Tournament / Cash game switch on top (M7)
                        BankContent(
                            state = state.copy(sheet = null),
                            onIntent = {},
                            modeSwitch = { BankModeSwitch(BankMode.TOURNAMENT, onSwitch = {}) },
                        )
                    }
                    if (sheet != null) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM)))
                        Box(Modifier.align(Alignment.BottomCenter)) { PokerSheetContent { sheet() } }
                    }
                }
            }
        }
        screen.compose.waitForIdle()
    }

    private fun check(what: String, scroll: Boolean = true) {
        val where = "$what on ${config.id}"
        if (what.contains("sheet")) {
            screenBehind.value = false
            screen.compose.waitForIdle()
        }
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        if (scroll) {
            screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
        } else {
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
        }
    }

    private fun golden(name: String) {
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    /** A golden drawn for particular cells, recorded on its [DeviceMatrix.pinned] cells only. */
    private fun pinnedGolden(name: String) {
        if (DeviceMatrix.isPinned(name, config)) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    private fun BankViewModel.state() = uiState.value

    @Test
    fun beforeBuyIns() {
        show(BankScenes.beforeBuyIns(kit).state())
        golden("S5_bank_before_buyins")
        check("Bank before buy-ins")
    }

    @Test
    fun midGame() {
        show(BankScenes.midGame(kit).state())
        golden("S5_bank_midgame")
        pinnedGolden("Z5_bank_tablet")
        if (config.device == Device.SmallPhone) {
            // Z2: the rows, with the closed Rebuy and Add-on columns folded under the names
            screen.compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
            screen.compose.waitForIdle()
            pinnedGolden("Z2_bank_small")
        }
        if (config.fontScale == 2.0f) {
            screen.compose.onNode(hasScrollToIndexAction()).performScrollToIndex(9)
            screen.compose.waitForIdle()
            pinnedGolden("S5_bank_font2x")
        }
        check("Bank mid-game")
    }

    @Test
    fun rebuysOpen() {
        show(BankScenes.rebuysOpen(kit).state())
        golden("S5_bank_rebuys_open")
        check("Bank with rebuys open")
    }

    @Test
    fun noRebuys() {
        show(BankScenes.noRebuys(kit).state())
        golden("S5_bank_no_rebuys")
        check("Bank without rebuys")
    }

    @Test
    fun champion() {
        show(BankScenes.champion(kit).state())
        golden("S5_bank_champion")
        check("Bank with a champion")
    }

    @Test
    fun thirtyPlayers() {
        show(BankScenes.thirtyPlayers(kit).state())
        // Scrolled into the list: the header stays on top
        screen.compose.onNode(hasScrollToIndexAction()).performScrollToIndex(13)
        screen.compose.waitForIdle()
        golden("S5_bank_30players")
        check("Bank with 30 players")
    }

    @Test
    fun knockoutSheet() = with(kit) {
        val viewModel = BankScenes.midGame(kit)
        viewModel.send(BankIntent.OpenKnockout(BankScenes.THEO))
        val sheet = viewModel.state().sheet as BankSheet.Knockout
        show(viewModel.state()) { KnockoutSheetContent(
            sheet,
            onKnockOut = {},
            onDismiss = {},
            initialChoice = BankScenes.MARCUS
        ) }
        golden("S5b_knockout_sheet")
        check("Knockout sheet")
    }

    @Test
    fun countSheet() = with(kit) {
        val viewModel = BankScenes.rebuysOpen(kit)
        viewModel.send(BankIntent.OpenCount(BankScenes.MARCUS, Purchase.REBUY))
        val sheet = viewModel.state().sheet as BankSheet.Count
        show(viewModel.state()) { CountSheetContent(sheet, onSet = {}, onDismiss = {}) }
        golden("S5b_count_sheet")
        check("Count sheet")
    }

    @Test
    fun payOutSheetForTheChampion() = with(kit) {
        val viewModel = BankScenes.champion(kit)
        viewModel.send(BankIntent.OpenPayOut(BankScenes.DANA))
        val sheet = viewModel.state().sheet as BankSheet.PayOut
        show(viewModel.state()) { PayOutSheetContent(sheet, onSetPaid = {}, onDismiss = {}) }
        golden("S5c_payout_champion")
        check("Pay-out sheet, champion")
    }

    @Test
    fun payOutSheetForSecond() = with(kit) {
        val viewModel = BankScenes.champion(kit)
        viewModel.send(BankIntent.SetPaid(BankScenes.MARCUS, false), BankIntent.OpenPayOut(BankScenes.MARCUS))
        val sheet = viewModel.state().sheet as BankSheet.PayOut
        show(viewModel.state()) { PayOutSheetContent(sheet, onSetPaid = {}, onDismiss = {}) }
        golden("S5c_payout_second")
        check("Pay-out sheet, 2nd")
    }

    @Test
    fun poolBreakdownSheet() {
        val state = BankScenes.midGame(kit).state()
        show(state) { PoolBreakdownSheetContent(state, onPayoutStructure = {}, onDismiss = {}) }
        golden("S5c_pool_breakdown")
        check("Pool breakdown sheet")
    }

    // Progressive and mystery bounties (PP-035) ------------------------------------------------------

    /** Progressive bounties: every player still in shows the bounty on their head under their name. */
    @Test
    fun progressiveBounties() {
        show(BankScenes.progressive(kit).state())
        golden("S5_bank_pko")
        check("Bank with progressive bounties")
    }

    /** Theo's knockout with Marcus picked: what Marcus takes now and what his bounty grows to. */
    @Test
    fun knockoutSheetProgressive() = with(kit) {
        val viewModel = BankScenes.progressive(kit)
        viewModel.send(BankIntent.OpenKnockout(BankScenes.THEO))
        val sheet = viewModel.state().sheet as BankSheet.Knockout
        show(viewModel.state()) {
            KnockoutSheetContent(sheet, onKnockOut = {}, onDismiss = {}, initialChoice = BankScenes.MARCUS)
        }
        golden("S5b_knockout_sheet_pko")
        check("Knockout sheet, progressive")
    }

    /** The progressive night played out: Dana's knockouts, her grown bounty and the one nobody claimed. */
    @Test
    fun payOutSheetProgressiveChampion() = with(kit) {
        val viewModel = BankScenes.progressive(kit)
        viewModel.knockOut(BankScenes.SAM, BankScenes.DANA)
        viewModel.knockOut(BankScenes.JO, BankScenes.THEO)
        viewModel.knockOut(BankScenes.THEO, BankScenes.DANA)
        viewModel.knockOut(BankScenes.PRIYA, null)
        viewModel.knockOut(BankScenes.MARCUS, BankScenes.DANA)
        viewModel.send(BankIntent.OpenPayOut(BankScenes.DANA))
        val sheet = viewModel.state().sheet as BankSheet.PayOut
        show(viewModel.state()) { PayOutSheetContent(sheet, onSetPaid = {}, onDismiss = {}) }
        golden("S5c_payout_champion_pko")
        check("Pay-out sheet, progressive champion")
    }

    /** Mystery bounties: the envelope Priya drew for knocking Theo out, opened. */
    @Test
    fun envelopeReveal() = with(kit) {
        val viewModel = BankScenes.mystery(kit)
        viewModel.send(BankIntent.KnockOut(BankScenes.THEO, BankScenes.PRIYA))
        val sheet = viewModel.state().sheet as BankSheet.Envelope
        show(viewModel.state()) { EnvelopeSheetContent(sheet, onDismiss = {}) }
        golden("S5d_envelope_reveal")
        check("Envelope sheet")
    }

    /** Mystery bounties: the pool breakdown says what is still in the envelopes (layout only). */
    @Test
    fun poolBreakdownSheetMystery() {
        val state = BankScenes.mystery(kit).state()
        show(state) { PoolBreakdownSheetContent(state, onPayoutStructure = {}, onDismiss = {}) }
        check("Pool breakdown sheet, mystery")
    }

    companion object {
        private const val SCRIM = 0.62f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(
            // PP_ONLY=phone-360x780_font1.0 renders one cell while iterating on a layout
            DeviceMatrix.all.filter { config -> System.getenv("PP_ONLY")?.let { config.id in it.split(",") } ?: true }
        )
    }
}
