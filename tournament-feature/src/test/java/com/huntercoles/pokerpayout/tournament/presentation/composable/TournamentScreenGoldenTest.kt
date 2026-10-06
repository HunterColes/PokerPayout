package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.navigation.NavTab
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
 * the single-size ones (`Z1_clock_small`, `Z3_table_small_land`, `Z4_clock_tablet`,
 * `S2_clock_running_font2x`). The states are the mockups' game night ([TournamentFixture]).
 *
 * A phone held sideways shows the table view once a clock exists, as the app does, so the S1 and S2
 * goldens on the landscape phone cells are table views; the tablet-land cell is the two-pane Z4.
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

    /** The every-size frames: small phone (Z1), small phone sideways (Z3), tablet (Z4), and 200% text. */
    @Test
    fun adaptiveSizes() {
        val running = TournamentUi(mode = TournamentMode.Running)
        val name = when {
            config.fontScale != 1f ->
                if (config.device == Device.TallPhone && config.fontScale == 2f) "S2_clock_running_font2x" else null
            config.device == Device.SmallPhone -> "Z1_clock_small"
            config.device == Device.SmallPhoneLandscape -> "Z3_table_small_land"
            config.device == Device.TabletLandscape -> "Z4_clock_tablet"
            else -> null
        } ?: return
        show(name, fixture.running, running)
        LayoutAssertions.assertTextFits(screen.compose, "$name on ${config.id}")
        screen.compose.onRoot().captureGolden("screens", name, config)
    }

    private fun check(name: String, timer: TimerUiState, ui: TournamentUi, scroll: Boolean = false) {
        showAndCheck(name, scroll) { TournamentContent(setup, timer, ui, TournamentActions()) }
    }

    private fun show(name: String, timer: TimerUiState, ui: TournamentUi) {
        shownKey = name
        shown = { InAppShell(NavTab.Tournament) { TournamentContent(setup, timer, ui, TournamentActions()) } }
        screen.compose.waitForIdle()
    }

    /** Shows [content] in the shell, checks the layout (at every scroll position if [scroll]), records the golden. */
    private fun showAndCheck(name: String, scroll: Boolean, checkLayout: Boolean = true, content: @Composable () -> Unit) {
        shownKey = name
        shown = { InAppShell(NavTab.Tournament) { content() } }
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

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
