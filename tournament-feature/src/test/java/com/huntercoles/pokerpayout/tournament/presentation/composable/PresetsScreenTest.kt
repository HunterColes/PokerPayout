package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.tournament.domain.presets.Starter
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetSheet
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState
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
 * The presets sheet (PP-032, S15) on every cell of the device matrix: text fits and isn't clipped at
 * any scroll position, and every target is 48 dp without overlapping. Goldens on
 * [DeviceMatrix.goldens]: the list before the start (`S15_presets_list`), the list mid-game with
 * loading off (`S15_presets_locked`), the save form (`S15_presets_save`) and the load question
 * (`S15_presets_load`), and a new install's list, nothing saved yet and the starter nights under it
 * (`S15_presets_starters`, PP-113). The sheet is drawn as it looks open, over the tab and its scrim (a modal
 * window doesn't capture under Robolectric); the layout checks then run on the sheet alone.
 * The presets row on the setup page and in the panel is in the S1 goldens ([TournamentScreenGoldenTest]).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PresetsScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture
    private lateinit var setup: TournamentConfigUiState
    private var savedZone: TimeZone? = null

    /** False once the golden is taken: the tab behind a modal sheet can't be reached, so only the sheet is checked. */
    private val screenBehind = mutableStateOf(true)

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")) // "Last used" dates are the same on every machine
        fixture = TournamentFixture(store)
        setup = fixture.setupState()
    }

    @After
    fun tearDown() {
        store.clear()
        savedZone?.let(TimeZone::setDefault)
    }

    /** [state]'s sheet over the tab as [timer] and [ui] have it. */
    private fun show(state: PresetsUiState, timer: TimerUiState = fixture.ready, ui: TournamentUi = TournamentUi()) {
        val sheet = requireNotNull(state.sheet)
        screen.compose.setContent {
            InAppShell(NavTab.Tournament) {
                Box(Modifier.fillMaxSize()) {
                    if (screenBehind.value) TournamentContent(setup, timer, ui, TournamentActions())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM)))
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        PokerSheetContent(title = presetsSheetTitle(state, sheet)) {
                            PresetsSheetBody(state, sheet, PresetsFixture.SUGGESTED_NAME, onIntent = {}, onShare = {})
                        }
                    }
                }
            }
        }
        screen.compose.waitForIdle()
    }

    /** The golden [name] (on the golden cells), then the layout checks on the sheet alone. */
    private fun check(what: String, name: String? = null) {
        if (name != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
        screenBehind.value = false
        screen.compose.waitForIdle()
        val where = "$what on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = false)
        screen.compose.forEachScrollPosition { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $it") }
    }

    @Test
    fun list() {
        show(PresetsFixture.list)
        check("Presets list", "S15_presets_list")
    }

    @Test
    fun lockedMidGame() {
        show(PresetsFixture.list.copy(canLoad = false), fixture.running, TournamentUi(mode = TournamentMode.PanelOpen))
        check("Presets list mid-game", "S15_presets_locked")
    }

    @Test
    fun saveForm() {
        show(PresetsFixture.list.copy(sheet = PresetSheet.Save))
        check("Save as preset", "S15_presets_save")
    }

    @Test
    fun loadQuestion() {
        show(PresetsFixture.list.copy(sheet = PresetSheet.ConfirmLoad(PresetsFixture.friday.id)))
        check("Load Friday?", "S15_presets_load")
    }

    @Test
    fun startersOnANewInstall() {
        show(PresetsFixture.firstNight)
        check("Presets on a new install, with the starters", "S15_presets_starters")
    }

    @Test
    fun starterLoadQuestion() {
        show(PresetsFixture.list.copy(sheet = PresetSheet.ConfirmStarter(Starter.DEEP_STACK)))
        check("Load Deep stack?")
    }

    @Test
    fun emptyList() {
        show(PresetsUiState(sheet = PresetSheet.List))
        check("No presets yet")
    }

    @Test
    fun renameForm() {
        show(PresetsFixture.list.copy(sheet = PresetSheet.Rename(PresetsFixture.deepStack.id)))
        check("Rename preset")
    }

    @Test
    fun saveFormWithoutAChipSet() {
        show(PresetsUiState(presets = PresetsFixture.all, sheet = PresetSheet.Save))
        check("Save as preset, no chip set yet")
    }

    companion object {
        private const val SCRIM = 0.62f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
