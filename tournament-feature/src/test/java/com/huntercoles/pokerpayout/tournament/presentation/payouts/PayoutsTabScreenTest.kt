package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.domain.model.HouseRake
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
import com.huntercoles.pokerpayout.core.testing.withCurrency
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
 * weights, the finished night with names, the settled night offering "Save this night" (PP-037),
 * and a 30-character winner of a $123,456.78 pool (#49);
 * `S6_payouts_font2x` on its [DeviceMatrix.pinned] cells.
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

    /**
     * PP-145: a 5% house rake. The pool is what the places share ($427.50), the rake has a line under
     * where it came from ("5% house rake −$22.50"), and under the rows "With the $22.50 house rake:
     * $450 taken in." The golden is scrolled to that line.
     */
    @Test
    fun raked() {
        game.tournament.setRake(HouseRake.of(500))
        game.midGame()
        show()
        screen.compose.onNodeWithText("taken in", substring = true).performScrollTo()
        screen.compose.waitForIdle()
        golden("S6_payouts_rake")
        check("Payouts, 5% house rake")
    }

    @Test
    fun finished() {
        game.finished()
        show()
        golden("S6_payouts_finished")
        check("Payouts, finished")
    }

    /** PP-037: over and everyone paid, so "Save this night" heads the tab. */
    @Test
    fun settledOffersToSaveTheNight() {
        game.settled()
        show()
        golden("S6_payouts_save")
        check("Payouts, settled")
    }

    @Test
    fun aSavedNightSaysWhereItWent() {
        game.settled()
        show(PayoutsIntent.SaveNight)
        check("Payouts, night saved")
    }

    /** The "Round to" label sits on its own line above its control, so it never wraps to "Round / to". */
    private fun assertRoundToOnOneLine(where: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        screen.compose.onNodeWithText("Round to", ignoreCase = true).performScrollTo()
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals("\"Round to\" lines in $where on ${config.id}", 1, layouts.single().lineCount)
    }

    @Test
    fun roundToSitsOnOneLineAboveItsControl() {
        game.midGame()
        show()
        assertRoundToOnOneLine("dollars")
    }

    /** The yen's rounding steps (¥100 / ¥500 / ¥1,000) are the widest labels the control carries. */
    @Test
    fun inYenTheRoundingChoicesFit() {
        withCurrency(AppCurrency.YEN) {
            game.yenNight()
            show()
            screen.compose.onNodeWithText("¥35,000 prize pool", substring = true).assertExists()
            golden("S6_payouts_yen")
            assertRoundToOnOneLine("yen")
            check("Payouts, in yen")
        }
    }

    /** #50: a pool under the rounding step is not split, and says so instead of quietly paying 1st everything. */
    @Test
    fun aPoolUnderTheRoundingStepSaysSo() {
        withCurrency(AppCurrency.YEN) {
            game.yenSmallPool()
            show()
            screen.compose.onNodeWithText("At the ¥500 step, 2nd would round to nothing", substring = true).assertExists()
            check("Payouts, yen pool under the rounding step")
        }
    }

    /** #65: a ¥500 pool at the ¥500 step with 2 places also pays 1st everything, and says so. */
    @Test
    fun aPoolOfOneStepWithTwoPlacesSaysSo() {
        withCurrency(AppCurrency.YEN) {
            game.smallTable(players = 2, buyInCents = 25_000L, rounding = PayoutRounding.FIVE_DOLLARS)
            show()
            screen.compose.onNodeWithText("At the ¥500 step, 2nd would round to nothing", substring = true).assertExists()
            check("Payouts, ¥500 pool at the ¥500 step")
        }
    }

    /** #65: at the smallest step, the note doesn't send the host looking for a smaller one. */
    @Test
    fun atTheSmallestStepTheNoteGivesNoStepAdvice() {
        game.smallTable(players = 2, buyInCents = 50L, rounding = PayoutRounding.ONE_DOLLAR)
        show()
        screen.compose.onNodeWithText("The pool is too small to split in $1 steps", substring = true).assertExists()
        screen.compose.onNodeWithText("Pick a smaller step", substring = true).assertDoesNotExist()
        check("Payouts, $1 pool at the $1 step")
    }

    /** #49: a 30-character winner and a $123,456.78 pool fit at every size and font scale. */
    @Test
    fun aLongNameAndABigPoolFit() {
        game.longNight()
        show()
        assertEquals(30, PayoutsGame.LONG_NAME.length)
        screen.compose.onNodeWithText("$123,456.78 prize pool", substring = true).assertExists()
        golden("S6_payouts_long")
        check("Payouts, long name and big pool")
    }

    /** #49: the same night in yen, whole yen: a ¥123,457 pool. */
    @Test
    fun aLongNameAndABigPoolFitInYen() {
        withCurrency(AppCurrency.YEN) {
            game.longNight()
            show()
            screen.compose.onNodeWithText("¥123,457 prize pool", substring = true).assertExists()
            check("Payouts, long name and big pool in yen")
        }
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
