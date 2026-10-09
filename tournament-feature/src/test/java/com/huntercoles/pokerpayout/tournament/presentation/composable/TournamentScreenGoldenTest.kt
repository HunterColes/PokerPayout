package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.components.LocalShellSnackbars
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.presentation.LocalTableKnockouts
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * The Tournament tab (S1 v2, S2, S3, S4, Z1, Z3, Z4) inside the app's shell, on every cell of the
 * device matrix: text fits, nothing visible is clipped, touch targets are 48 dp and don't overlap.
 * Goldens on the [DeviceMatrix.goldens] cells, named after the mockups (`S2_clock_running`), plus
 * the single-size ones on their [DeviceMatrix.pinned] cells (`Z1_clock_small`, `Z3_table_small_land`,
 * `Z4_clock_tablet`, `S2_clock_running_font2x`). The states are the mockups' game night ([TournamentFixture]).
 *
 * A phone held sideways shows the table view once a clock exists, as the app does, so the S1 and S2
 * goldens on the landscape phone cells are table views; the tablet-land cell is the two-pane Z4.
 * The table view has its Knock out button (PP-135); the Bank's panel behind it is a stand-in here
 * ([FakeTableKnockouts]) and bank-feature's QuickKnockoutScreensTest checks the real one.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TournamentScreenGoldenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val store = ViewModelStore()

    /** Under 360 dp the top bar moves the bell into its menu, as the tab does. */
    private val small = config.device.widthDp < SMALL_BELOW_DP
    private lateinit var fixture: TournamentFixture
    private lateinit var setup: TournamentConfigUiState
    private var savedZone: TimeZone? = null

    /** What is on screen; each state is composed fresh (its own scroll positions). */
    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var shownKey by mutableStateOf("")

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")) // "ends about" times are the same on every machine
        fixture = TournamentFixture(store)
        setup = fixture.setupState()
        screen.compose.setContent {
            key(shownKey) { shown?.invoke() }
        }
    }

    @After
    fun tearDown() {
        store.clear()
        savedZone?.let(TimeZone::setDefault)
    }

    @Test
    fun setup() {
        check("S1_setup_before", fixture.ready, TournamentUi(), scroll = true)
        check("S1_setup_invalid", fixture.invalid(), TournamentUi())
    }

    /**
     * PP-035: mystery bounties picked before anyone is out, so the bounty type is open and the
     * envelopes are listed under it (1 × $15 · 2 × $6 · 6 × $3).
     */
    @Test
    fun setupMystery() {
        val mystery = setup.copy(
            config = setup.config.copy(money = setup.money.copy(bountyMode = BountyMode.MYSTERY)),
            knockoutsRecorded = false,
        )
        showAndCheck("S1_setup_mystery", scroll = true) {
            TournamentContent(mystery, fixture.ready, TournamentUi(), TournamentActions())
        }
    }

    @Test
    fun clock() {
        val running = TournamentUi(mode = TournamentMode.Running)
        check("S1_running_strip", fixture.running, running, scroll = true)
        check("S2_clock_running", fixture.running, running)
        check("S2_clock_paused", fixture.running.copy(isRunning = false), running)
        check("S2_clock_final_minutes", fixture.level(6, FINAL_MINUTES_LEFT), running)
        check("S2_clock_overtime", fixture.overtime, running, scroll = true)
        check("S2_clock_finished", fixture.finished, running, scroll = true)
        // Ready: the clock before its first start (the tab itself shows setup then).
        showAndCheck("S2_clock_ready", scroll = false) {
            Column(Modifier.fillMaxSize()) {
                TournamentTopBar(fixture.ready, TournamentMode.Running, TournamentActions(), small = small, wide = false)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ClockContent(setup, fixture.ready, TournamentActions(), ClockLayout.Phone, 16.dp)
                }
            }
        }
    }

    @Test
    fun panel() {
        check("S1_panel_open", fixture.running, TournamentUi(mode = TournamentMode.PanelOpen), scroll = true)
        val unlocked = TournamentUi(mode = TournamentMode.PanelOpen, panelUnlocked = true)
        check("S1_panel_unlocked", fixture.running, unlocked, scroll = true)
    }

    @Test
    fun breaks() {
        val running = TournamentUi(mode = TournamentMode.Running)
        val colorUp = fixture.breaks.first { it.colorUp.isNotEmpty() }
        check("S4_break_colorup", fixture.onBreak(colorUp), running, scroll = true)
        val done = fixture.onBreak(colorUp).copy(colorUpDoneAfterLevels = setOf(colorUp.afterLevel))
        check("S4_break_done", done, running)
        val plain = fixture.breaks.firstOrNull { it.colorUp.isEmpty() }
        val plainState = plain?.let { fixture.onBreak(it) }
            ?: fixture.onBreak(colorUp).copy(colorUpDoneAfterLevels = emptySet()).let { state ->
                // Every break of this game colors up: show the break with its color-up ticked off and no add-ons.
                state.copy(colorUpDoneAfterLevels = setOf(colorUp.afterLevel), purchases = state.purchases.copy(addOnCents = 0))
            }
        check("S4_break_plain", plainState, running)
    }

    /**
     * PP-091 #9: the same night with a chip set set up in Tools (white 25s, red 100s, green 500s,
     * black 1,000s): the break's color-up is drawn and worded with those chips.
     */
    @Test
    fun breakWithChipSet() {
        val running = TournamentUi(mode = TournamentMode.Running)
        val night = fixture.withChipSet()
        val colorUp = night.timeline.segments.filterIsInstance<BreakSegment>().first { it.colorUp.isNotEmpty() }
        check("S4_break_chipset", fixture.onBreak(colorUp, base = night), running, scroll = true)
    }

    @Test
    fun tableView() {
        if (!config.device.isLandscape) return // S3 is the sideways screen; portrait cells show S2 (above)
        val running = TournamentUi(mode = TournamentMode.Running)
        check("S3_table_running", fixture.running.copy(isTableView = true), running)
        check("S3_table_paused", fixture.running.copy(isTableView = true, isRunning = false), running)
        val colorUp = fixture.breaks.first { it.colorUp.isNotEmpty() }
        check("S3_table_break", fixture.onBreak(colorUp).copy(isTableView = true), running)
    }

    /**
     * PP-135: knockouts on the table view. On the bubble (4 left, 3 paid) the players left say so;
     * after a knockout the snackbar's Undo sits bottom left, clear of Knock out, pause and exit.
     */
    @Test
    fun tableViewKnockouts() {
        if (!config.device.isLandscape) return
        val running = TournamentUi(mode = TournamentMode.Running)
        check("S3_table_bubble", fixture.running.withPlayersLeft(BUBBLE_LEFT).copy(isTableView = true), running)

        val snackbars = SnackbarHostState()
        val afterKnockout = fixture.running.withPlayersLeft(AFTER_KNOCKOUT_LEFT).copy(isTableView = true)
        showAndCheck("S3_table_undo", scroll = false) {
            CompositionLocalProvider(LocalShellSnackbars provides snackbars) {
                TournamentContent(setup, afterKnockout, running, TournamentActions())
            }
            LaunchedEffect(snackbars) {
                snackbars.showSnackbar(KNOCKOUT_DONE, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
        }
    }

    /**
     * PP-135: the upright clock on the bubble says so beside the level, as the table view does
     * beside the players left (a phone on its side shows the table view: S3_table_bubble).
     */
    @Test
    fun clockOnTheBubble() {
        if (config.device.isLandscape && config.device != Device.TabletLandscape) return
        check("S2_clock_bubble", fixture.running.withPlayersLeft(BUBBLE_LEFT), TournamentUi(mode = TournamentMode.Running))
    }

    /** The same moment with [left] players still in (each one's stack grows as the field shrinks). */
    private fun TimerUiState.withPlayersLeft(left: Int): TimerUiState =
        copy(table = table.copy(playersLeft = left, averageStack = table.averageStack * table.playersLeft / left))

    /**
     * The fold (S1 v2) 300 ms in: the sections folded to their lines, stacking into the strip. The frame
     * is [FoldScene] at 300 of its 800 ms, the same scene [SetupFold] animates ([SetupFoldTest] runs
     * the animation itself on a paused clock).
     */
    @Test
    fun fold() {
        if (config !in DeviceMatrix.goldens) return
        val timer = fixture.level(1, LEVEL_ONE_LEFT)
        showAndCheck("S1_fold_300ms", scroll = false, checkLayout = false) {
            Column(Modifier.fillMaxSize()) {
                TournamentTopBar(timer, TournamentMode.Folding, TournamentActions(), small = small, wide = false)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FoldScene(setup, timer, 16.dp, progress = FOLD_FRAME_MILLIS / FOLD_MILLIS.toFloat())
                }
            }
        }
    }

    /**
     * The every-size frames, each on the cell it was drawn for ([DeviceMatrix.pinned]): small phone
     * (Z1), small phone sideways (Z3), tablet (Z4), and 200% text on a tall phone.
     */
    @Test
    fun adaptiveSizes() {
        val running = TournamentUi(mode = TournamentMode.Running)
        val name = ADAPTIVE_GOLDENS.firstOrNull { DeviceMatrix.isPinned(it, config) } ?: return
        show(name, fixture.running, running)
        LayoutAssertions.assertTextFits(screen.compose, "$name on ${config.id}")
        screen.compose.onRoot().captureGolden("screens", name, config)
    }

    /**
     * The top bar's buttons are 48 dp both ways on every cell, before the start and on the clock, at
     * every scroll position of the page below. (On 1.3.4 the device matrix once read "Reset
     * tournament" as 48 x 32 dp while setup scrolled.) The screen checks above accept a smaller
     * target with nothing next to it; these must keep their full size.
     */
    @Test
    fun topBarButtonsStayFullSize() {
        show("top bar, setup", fixture.ready, TournamentUi())
        val inSetup = topBarButtons()
        assertTrue("no top bar buttons found in setup on ${config.id}", inSetup.isNotEmpty())
        screen.compose.forEachScrollPosition { position -> assertFullSize("setup on ${config.id}, $position") }

        // A phone held sideways shows the table view on the clock, without the top bar.
        show("top bar, clock", fixture.running, TournamentUi(mode = TournamentMode.Running))
        screen.compose.forEachScrollPosition { position -> assertFullSize("clock on ${config.id}, $position") }
    }

    private fun topBarButtons() = TOP_BAR_BUTTONS.flatMap { label ->
        screen.compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes()
    }

    private fun assertFullSize(where: String) {
        val shrunk = topBarButtons().filter { node ->
            val minPx = with(node.layoutInfo.density) { MIN_TOUCH.toPx() }
            node.size.width + 1 < minPx || node.size.height + 1 < minPx
        }
        assertTrue(
            "$where: ${shrunk.map { "${it.config} is ${it.size.width} x ${it.size.height} px" }}",
            shrunk.isEmpty(),
        )
    }

    private fun check(name: String, timer: TimerUiState, ui: TournamentUi, scroll: Boolean = false) {
        showAndCheck(name, scroll) { TournamentContent(setup, timer, ui, TournamentActions()) }
    }

    private fun show(name: String, timer: TimerUiState, ui: TournamentUi) {
        shownKey = name
        shown = { InTournamentTab { TournamentContent(setup, timer, ui, TournamentActions()) } }
        screen.compose.waitForIdle()
    }

    /** The tab as the app shows it: in the shell, with the Bank's knockout for the table view (PP-135). */
    @Composable
    private fun InTournamentTab(content: @Composable () -> Unit) {
        InAppShell(NavTab.Tournament) {
            CompositionLocalProvider(LocalTableKnockouts provides FakeTableKnockouts) { content() }
        }
    }

    /** Shows [content] in the shell, checks the layout (at every scroll position if [scroll]), records the golden. */
    private fun showAndCheck(name: String, scroll: Boolean, checkLayout: Boolean = true, content: @Composable () -> Unit) {
        shownKey = name
        shown = { InTournamentTab { content() } }
        screen.compose.waitForIdle()
        val where = "$name on ${config.id}"
        if (checkLayout) {
            LayoutAssertions.assertTextFits(screen.compose, where)
            LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        }
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
        when {
            !checkLayout -> Unit
            scroll -> screen.compose.forEachScrollPosition { position ->
                LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            }
            else -> LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
        }
    }

    companion object {
        private const val FINAL_MINUTES_LEFT = 95
        private const val SMALL_BELOW_DP = 360
        private const val LEVEL_ONE_LEFT = 20 * 60
        private const val FOLD_FRAME_MILLIS = 300f
        private const val BUBBLE_LEFT = 4
        private const val AFTER_KNOCKOUT_LEFT = 6
        private const val KNOCKOUT_DONE = "Theo is out in 7th · bounty to Dana"
        private val MIN_TOUCH = 48.dp
        private val TOP_BAR_BUTTONS = listOf("Reset tournament", "Mute chimes", "Unmute chimes", "Table view", "More options")
        private val ADAPTIVE_GOLDENS =
            listOf("Z1_clock_small", "Z3_table_small_land", "Z4_clock_tablet", "S2_clock_running_font2x")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
