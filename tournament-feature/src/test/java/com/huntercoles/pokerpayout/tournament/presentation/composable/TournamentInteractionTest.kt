package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What each control on the Tournament tab sends: the ±1 nudges, play/pause and the level skips, the
 * bell (tap and hold), the strip and its panel with "Unlock to edit…", Start, the payouts row,
 * "End break now", "Color-up done" and its undo, "Record in Bank", and the reset question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-port")
class TournamentInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture
    private val timerIntents = mutableListOf<TimerIntent>()
    private val setupIntents = mutableListOf<TournamentConfigIntent>()
    private val opened = mutableListOf<String>()
    private var ui by mutableStateOf(TournamentUi())
    private var timer by mutableStateOf<TimerUiState?>(null)
    private var setup by mutableStateOf<TournamentConfigUiState?>(null)
    private var composed = false

    @Before
    fun setUp() {
        fixture = TournamentFixture(store)
    }

    @After
    fun tearDown() = store.clear()

    private fun show(state: TimerUiState, startUi: TournamentUi, setupState: TournamentConfigUiState = fixture.setupState()) {
        ui = startUi
        timer = state
        setup = setupState
        if (!composed) {
            composed = true
            val actions = TournamentActions(
                onSetupIntent = { setupIntents += it },
                onTimerIntent = { timerIntents += it },
                updateUi = { ui = it(ui) },
                openBank = { opened += "bank" },
                openPayouts = { opened += "payouts" },
                openSound = { opened += "sound" },
            )
            compose.setContent {
                PokerTheme(reducedMotion = true) {
                    val shownSetup = setup
                    val shownTimer = timer
                    if (shownSetup != null && shownTimer != null) TournamentContent(shownSetup, shownTimer, ui, actions)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tap(description: String) = compose.onNodeWithContentDescription(description).scrolledTo().performClick().also {
        compose.waitForIdle()
    }

    private fun tapText(text: String) = compose.onNodeWithText(text).scrolledTo().performClick().also { compose.waitForIdle() }

    /** Scrolled into view when it is in a scrolling column; as it is otherwise. */
    private fun SemanticsNodeInteraction.scrolledTo(): SemanticsNodeInteraction =
        runCatching { performScrollTo() }.getOrDefault(this)

    /**
     * PP-035: the bounty type sends its choice while nobody is out; from the first knockout it is
     * fixed (a tap sends nothing) and the line under it says so.
     */
    @Test
    fun `the bounty type sends its choice until the first knockout`() {
        val open = fixture.setupState().copy(knockoutsRecorded = false)
        show(fixture.ready, TournamentUi(), open)
        tapText("Progressive")
        assertEquals(listOf(TournamentConfigIntent.UpdateBountyMode(BountyMode.PROGRESSIVE)), setupIntents)

        setupIntents.clear()
        show(fixture.ready, TournamentUi(), open.copy(knockoutsRecorded = true))
        tapText("Mystery")
        assertTrue(setupIntents.isEmpty())
        compose.onNodeWithText("Fixed now that someone is out.", substring = true).assertExists()
    }

    /** PP-035: once a mystery envelope is drawn, the players stepper only goes up, and says why. */
    @Test
    fun `the player count can't go lower once a mystery envelope is drawn`() {
        val open = fixture.setupState()
        val mystery = open.copy(config = open.config.copy(money = open.money.copy(bountyMode = BountyMode.MYSTERY)))
        show(fixture.ready, TournamentUi(), mystery.copy(envelopesDrawn = true))

        compose.onNodeWithText("Can't go lower now that envelopes are drawn").scrolledTo().assertExists()
        compose.onNodeWithContentDescription("Decrease Players").scrolledTo().assertIsNotEnabled()
        tap("Decrease Players")
        assertTrue(setupIntents.isEmpty())
        tap("Increase Players")
        assertEquals(listOf(TournamentConfigIntent.UpdatePlayerCount(open.playerCount + 1)), setupIntents)

        // Before any envelope is drawn it goes both ways, with the usual hint
        setupIntents.clear()
        show(fixture.ready, TournamentUi(), mystery)
        compose.onNodeWithText("Bank gets one row per player").scrolledTo().assertExists()
        tap("Decrease Players")
        assertEquals(listOf(TournamentConfigIntent.UpdatePlayerCount(open.playerCount - 1)), setupIntents)
    }

    @Test
    fun `the clock's controls send the nudges, play-pause and the level skips`() {
        show(fixture.running, TournamentUi(mode = TournamentMode.Running))
        tap("Remove one minute")
        tap("Add one minute")
        tap("Pause timer")
        tap("Next blind level")
        tap("Previous blind level")
        assertEquals(
            listOf(
                TimerIntent.NudgeMinutes(-1),
                TimerIntent.NudgeMinutes(1),
                TimerIntent.ToggleTimer,
                TimerIntent.NextBlindLevel,
                TimerIntent.PreviousBlindLevel,
            ),
            timerIntents,
        )
    }

    @Test
    fun `the bell mutes on a tap and opens Sound on a hold`() {
        show(fixture.running, TournamentUi(mode = TournamentMode.Running))
        tap("Mute chimes")
        assertEquals(listOf(TimerIntent.ToggleMute), timerIntents)
        compose.onNodeWithContentDescription("Mute chimes").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(listOf("sound"), opened)
    }

    /**
     * The strip unfolds the panel; "Unlock to edit…" asks in a sheet first (a modal window, which the
     * device tour taps through), and once unlocked a blind change keeps the clock's level.
     */
    @Test
    fun `the strip opens setup over the clock, and unlocked blind changes keep the level`() {
        show(fixture.running, TournamentUi(mode = TournamentMode.Running))
        compose.onNodeWithText("Setup").performClick()
        compose.waitForIdle()
        assertEquals(TournamentMode.PanelOpen, ui.mode)
        compose.onNodeWithText("Locked while the clock runs", ignoreCase = true).assertExists()
        compose.onNodeWithText("Unlock to edit…").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Level length", substring = true)).assertDoesNotExist()

        ui = ui.unlock()
        compose.waitForIdle()
        compose.onNodeWithText("Unlocked: money and blinds", ignoreCase = true).assertExists()
        val levels = compose.onNode(hasSetTextAction() and hasText("Level length", substring = true))
        levels.performScrollTo().requestFocus()
        levels.performTextReplacement("15")
        levels.performImeAction()
        compose.waitForIdle()
        assertEquals(TimerIntent.KeepingLevel(TimerIntent.UpdateRoundLength(15)), timerIntents.last())

        tap("Close setup")
        assertEquals(TournamentMode.Running, ui.mode)
        assertFalse(ui.panelUnlocked)
    }

    @Test
    fun `start folds setup into the clock, and the payouts row opens the Payouts tab`() {
        show(fixture.ready, TournamentUi())
        compose.onNode(hasText("Payouts") and hasText("rounded to", substring = true)).scrolledTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("payouts"), opened)

        tapText("Start clock")
        assertEquals(listOf<TimerIntent>(TimerIntent.ToggleTimer), timerIntents)
        assertTrue(ui.startPressed)
    }

    @Test
    fun `on a break, tick the color-up, record add-ons in the Bank and end it now`() {
        val colorUp = fixture.breaks.first { it.colorUp.isNotEmpty() }
        show(fixture.onBreak(colorUp), TournamentUi(mode = TournamentMode.Running))
        tapText("Color-up done")
        tapText("Record in Bank")
        tapText("End break now")
        assertEquals(listOf(TimerIntent.MarkColorUpDone(), TimerIntent.EndBreakNow), timerIntents)
        assertEquals(listOf("bank"), opened)
    }

    /**
     * PP-091 #9: with a chip set set up in Tools, the break names and draws your chips (white 25s,
     * red 100s), not a common home set's (green 25s, black 100s).
     */
    @Test
    fun `a break colors up with your chip set once one is set up`() {
        val standard = fixture.breaks.first { it.colorUp.isNotEmpty() }
        assertEquals(25, standard.colorUpSwaps.first().chip)
        show(fixture.onBreak(standard), TournamentUi(mode = TournamentMode.Running))
        compose.onNodeWithContentDescription("green 25s for 1 ", substring = true).assertExists()

        val night = fixture.withChipSet()
        val colorUp = night.timeline.segments.filterIsInstance<BreakSegment>().first { it.colorUp.isNotEmpty() }
        assertEquals(ChipColour.White, night.chipSet?.colourOf(25))
        show(fixture.onBreak(colorUp, base = night), TournamentUi(mode = TournamentMode.Running))
        compose.onNodeWithContentDescription("white 25s for 1 ", substring = true).assertExists()
        compose.onNodeWithContentDescription("green 25s", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a ticked color-up can be undone`() {
        val colorUp = fixture.breaks.first { it.colorUp.isNotEmpty() }
        val done = fixture.onBreak(colorUp).copy(colorUpDoneAfterLevels = setOf(colorUp.afterLevel))
        show(done, TournamentUi(mode = TournamentMode.Running))
        tapText("Undo")
        assertEquals(listOf(TimerIntent.MarkColorUpDone(done = false)), timerIntents)
    }

    /** Reset asks first (the question is a sheet: the device tour answers it). Mid-game it is "New tournament…". */
    @Test
    fun `reset asks first, from setup and from the clock's menu`() {
        show(fixture.ready, TournamentUi())
        tap("Reset tournament")
        assertEquals(listOf<TournamentConfigIntent>(TournamentConfigIntent.ShowResetDialog), setupIntents)

        show(fixture.running, TournamentUi(mode = TournamentMode.Running))
        tap("More options")
        compose.onNode(hasText("New tournament…") and hasClickAction()).performClick()
        compose.waitForIdle()
        val asked = TournamentConfigIntent.ShowResetDialog
        assertEquals(listOf<TournamentConfigIntent>(asked, asked), setupIntents)
    }
}
