package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceUiState
import com.huntercoles.pokerpayout.tools.presentation.EquityQuizUiState
import com.huntercoles.pokerpayout.tools.presentation.ShotClockUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shot clock (S17), dealer's choice (S18) and the equity quiz (S19) inside the app's shell on
 * every cell of the device matrix: text fits and is never clipped at any scroll position, and 48 dp
 * targets that don't overlap. Goldens on [DeviceMatrix.goldens]: `S17_shot_clock_ready` (before the
 * first tap) and `S17_shot_clock_low` (eight seconds left, two players' cards played);
 * `S18_dealers_picked` (Badugi picked, with a house game) and `S18_dealers_rules` (every game's
 * rules, the sheet); `S19_quiz_ask` (a heads-up flop to guess) and `S19_quiz_range_wrong` (three
 * hands, the range answered one off). The other states get the layout checks.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FunToolsScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    // Shot clock (S17)

    @Test
    fun shotClockReady() = shotClock("S17_shot_clock_ready", ShotClockFixtures.ready)

    @Test
    fun shotClockLow() = shotClock("S17_shot_clock_low", ShotClockFixtures.low)

    @Test
    fun shotClockTimeUp() = shotClock(name = null, ShotClockFixtures.timeUp)

    @Test
    fun shotClockNoTimeBank() = shotClock(name = null, ShotClockFixtures.noTimeBank)

    // Dealer's choice (S18)

    @Test
    fun dealersPicked() = dealers("S18_dealers_picked", DealersFixtures.picked)

    @Test
    fun dealersFirstSpin() = dealers(name = null, DealersFixtures.fresh)

    @Test
    fun dealersHouseGamePicked() = dealers(name = null, DealersFixtures.housePicked)

    @Test
    fun dealersTooFew() = dealers(name = null, DealersFixtures.tooFew)

    @Test
    fun dealersHouseFull() = dealers(name = null, DealersFixtures.houseFull)

    @Test
    fun dealersRules() = check("S18_dealers_rules") {
        PokerSheetContent(title = stringResource(R.string.dealers_all_rules)) { AllGameRules() }
    }

    // Equity quiz (S19)

    @Test
    fun quizAsk() = quiz("S19_quiz_ask", QuizFixtures.ask)

    @Test
    fun quizRangeWrong() = quiz("S19_quiz_range_wrong", QuizFixtures.rangeWrong)

    @Test
    fun quizWorking() = quiz(name = null, QuizFixtures.working)

    @Test
    fun quizRangeAsk() = quiz(name = null, QuizFixtures.rangeAsk)

    private fun shotClock(name: String?, state: ShotClockUiState) =
        check(name) { ShotClockContent(state, onIntent = {}, onBack = {}) }

    private fun dealers(name: String?, state: DealersChoiceUiState) =
        check(name) { DealersChoiceContent(state, onIntent = {}, onBack = {}) }

    private fun quiz(name: String?, state: EquityQuizUiState) =
        check(name) { EquityQuizContent(state, onIntent = {}, onBack = {}) }

    private fun check(name: String?, content: @Composable () -> Unit) {
        screen.compose.setContent { InAppShell(NavTab.Tools, content) }
        screen.compose.waitForIdle()
        val where = "${name ?: "fun tools (layout only)"} on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    companion object {
        const val GROUP = "screens"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
