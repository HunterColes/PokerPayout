package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankScenes
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankTestKit
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.bank.presentation.RegularsModel
import com.huntercoles.pokerpayout.core.design.components.PokerSheetContent
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
 * Tonight's players (S26, PP-110) over the Bank, inside the app's shell on every cell of the device
 * matrix: text fits and isn't clipped at any scroll position, every target is 48 dp and named for
 * TalkBack. Goldens on [DeviceMatrix.goldens]: `S26_regulars` (six of nine seats named, twelve
 * regulars) and `S26_regulars_empty` (the first time). A name being typed, and every seat named, get
 * the layout checks too. The sheet is drawn as it looks open, over the Bank and its scrim (a modal
 * window doesn't capture under Robolectric); its states come from the real ViewModel ([BankScenes]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RegularsSheetTest(private val config: ScreenConfig) {
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

    /** The Bank behind until the checks: then only the sheet, as a modal sheet's screen can't be reached. */
    private val screenBehind = mutableStateOf(true)

    private fun show(viewModel: BankViewModel, query: String = "") {
        val state = viewModel.uiState.value
        val model = RegularsModel.of(state, (state.sheet as BankSheet.Regulars).order)
        screen.compose.setContent {
            InAppShell(NavTab.Bank) {
                Box(Modifier.fillMaxSize()) {
                    if (screenBehind.value) {
                        BankContent(state = state.copy(sheet = null), onIntent = {})
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM)))
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        PokerSheetContent {
                            RegularsSheetContent(model, onToggle = {}, onAdd = {}, onDismiss = {}, initialQuery = query)
                        }
                    }
                }
            }
        }
        screen.compose.waitForIdle()
    }

    private fun check(what: String, golden: String? = null) {
        if (golden != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", golden, config)
        screenBehind.value = false
        screen.compose.waitForIdle()
        val where = "$what on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    @Test
    fun regulars() {
        show(BankScenes.regulars(kit))
        check("Tonight's players", golden = "S26_regulars")
    }

    @Test
    fun firstTime() {
        show(BankScenes.noRegulars(kit))
        check("Tonight's players, nobody yet", golden = "S26_regulars_empty")
    }

    @Test
    fun typing() {
        show(BankScenes.regulars(kit), query = "a")
        check("Tonight's players, typing")
    }

    @Test
    fun nobodyByThatName() {
        show(BankScenes.regulars(kit), query = "Quentin")
        check("Tonight's players, a new name")
    }

    @Test
    fun everySeatNamed() = with(kit) {
        val viewModel = BankScenes.regulars(kit)
        listOf("Rita", "Alex", "Ben").forEach { viewModel.send(BankIntent.ToggleRegular(it)) }
        show(viewModel)
        check("Tonight's players, every seat named")
    }

    companion object {
        private const val SCRIM = 0.62f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
