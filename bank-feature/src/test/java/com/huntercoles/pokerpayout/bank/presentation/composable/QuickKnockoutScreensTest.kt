package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankScenes
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.DANA
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.THEO
import com.huntercoles.pokerpayout.bank.presentation.BankTestKit
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
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
 * The knockout from the full-screen clock (PP-135) on every cell of the device matrix, over the
 * table view's black: sideways a side sheet over the blinds, upright (a tablet that won't turn) a
 * bottom sheet. Text fits and isn't clipped at any scroll position, every target is 48 dp and none
 * overlap. The states come from the real ViewModel ([BankScenes]). Goldens of both questions where
 * the table view is drawn: the phone and the tablet on their sides, and the tablet upright.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class QuickKnockoutScreensTest(private val config: ScreenConfig) {
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

    private fun show(state: BankUiState) {
        screen.compose.setContent {
            PokerTheme(reducedMotion = true) {
                Box(Modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
                    QuickKnockoutOverlay(state = state, onIntent = {}, onClose = {})
                }
            }
        }
        screen.compose.waitForIdle()
    }

    private fun check(what: String) {
        val where = "$what on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
    }

    /** Where the table view is drawn: sideways, and a tablet upright. */
    private fun golden(name: String) {
        val drawn = config.device.isLandscape || config.device == Device.Tablet
        if (config in DeviceMatrix.goldens && drawn) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    @Test
    fun whoIsOut() {
        show(BankScenes.midGame(kit).uiState.value)
        golden("S3_knockout_who")
        check("Who's out?")
    }

    /** Progressive bounties: the longest choices, a bounty under each name still in. */
    @Test
    fun whoKnockedThemOut() {
        val viewModel = BankScenes.progressive(kit)
        viewModel.acceptIntent(BankIntent.OpenKnockout(THEO))
        show(viewModel.uiState.value)
        golden("S3_knockout_by")
        check("Who knocked Theo out?")
    }

    @Test
    fun theMysteryEnvelope() {
        val viewModel = BankScenes.mystery(kit)
        viewModel.acceptIntent(BankIntent.OpenKnockout(THEO))
        viewModel.acceptIntent(BankIntent.KnockOut(THEO, DANA))
        show(viewModel.uiState.value)
        check("Dana's envelope")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
