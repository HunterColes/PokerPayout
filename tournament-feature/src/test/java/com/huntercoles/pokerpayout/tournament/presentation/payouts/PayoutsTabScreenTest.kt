package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
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
 * The Payouts tab (S6) inside the app's shell on every cell of the device matrix, on the mockups'
 * game ([PayoutsGame]): text fits and isn't clipped at any scroll position, targets are 48 dp
 * without overlap. Goldens on [DeviceMatrix.goldens]: Standard / $5 / 3 places, Top-heavy, custom
 * weights, and the finished night with names; `S6_payouts_font2x` on its [DeviceMatrix.pinned] cells.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PayoutsTabScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val dispatcher = StandardTestDispatcher()
    private lateinit var game: PayoutsGame

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        game = PayoutsGame()
    }

    @After
    fun tearDown() {
        game.clear()
        Dispatchers.resetMain()
    }

    private fun show(intent: PayoutsIntent? = null) {
        val viewModel = game.viewModel()
        intent?.let(viewModel::acceptIntent)
        dispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        screen.compose.setContent { InAppShell(NavTab.Payouts) { PayoutsContent(state = state, onIntent = {}, onShare = {}) } }
        screen.compose.waitForIdle()
    }

    private fun check(what: String) {
        val where = "$what on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
    }

    private fun golden(name: String) {
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    /** A golden drawn for particular cells, recorded on its [DeviceMatrix.pinned] cells only. */
    private fun pinnedGolden(name: String) {
        if (DeviceMatrix.isPinned(name, config)) screen.compose.onRoot().captureGolden("screens", name, config)
    }

    @Test
    fun standard() {
        game.midGame()
        show()
        golden("S6_payouts_standard")
        pinnedGolden("S6_payouts_font2x")
        check("Payouts, Standard")
    }

    @Test
    fun topHeavy() {
        game.midGame()
        show(PayoutsIntent.SelectPreset(PayoutPreset.TOP_HEAVY))
        golden("S6_payouts_topheavy")
        check("Payouts, Top-heavy")
    }

    @Test
    fun customWeights() {
        game.midGame()
        show(PayoutsIntent.SaveStructure(PayoutSettings(
            listOf(50, 25, 15, 10),
            preset = null,
            rounding = PayoutRounding.ONE_DOLLAR
        )))
        golden("S6_payouts_custom")
        check("Payouts, custom weights")
    }

    @Test
    fun finished() {
        game.finished()
        show()
        golden("S6_payouts_finished")
        check("Payouts, finished")
    }

    @Test
    fun lockedWhileTheClockRuns() {
        game.midGame()
        game.tournament.setTournamentLocked(true)
        show()
        check("Payouts, locked")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(
            // PP_ONLY=phone-360x780_font1.0 renders one cell while iterating on a layout
            DeviceMatrix.all.filter { config -> System.getenv("PP_ONLY")?.let { config.id in it.split(",") } ?: true }
        )
    }
}
