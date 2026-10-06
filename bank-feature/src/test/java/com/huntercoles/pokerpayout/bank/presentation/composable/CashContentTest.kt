package com.huntercoles.pokerpayout.bank.presentation.composable

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashScenes
import com.huntercoles.pokerpayout.bank.presentation.cash.CashTestKit
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The cash game's controls (S13): what each line, tick, button and sheet field sends, what TalkBack
 * reads, and the real route: the mode switch keeping both games, and Share as plain text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h915dp-port-xhdpi")
class CashContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: CashTestKit
    private val sent = mutableListOf<CashIntent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = CashTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent { PokerTheme(reducedMotion = true) { content() } }
        compose.waitForIdle()
    }

    private fun show(state: CashUiState) = setContent {
        CashLedgerContent(
            state = state,
            onIntent = { sent += it },
            onShare = {},
            modeSwitch = { BankModeSwitch(BankMode.CASH, onSwitch = { sent += CashIntent.SwitchMode(it) }) },
        )
    }

    /** A merged line, by its name, reading [spoken] after it. */
    private fun line(name: String, vararg spoken: String) = compose.onNode(hasText(name) and hasClickAction())
        .performScrollTo()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, spoken.toList()))

    @Test
    fun aLineReadsAsOneButtonAndOpensThePlayer() {
        val state = CashScenes.balanced(kit).uiState.value
        show(state)
        line("Dana", "in $40", "out $112", "up $72").performClick()
        line("Sam", "in $20", "out $0", "down $20")
        line("Jo", "in $40", "out $52", "up $12")
        assertEquals(listOf(CashIntent.OpenPlayer(state.players.first { it.name == "Dana" }.id)), sent)
    }

    @Test
    fun aLineBeforeTheCountSaysSo() {
        show(CashScenes.counting(kit).uiState.value)
        line("Theo", "in $80", "chips not counted yet")
        line("Dana", "in $40", "out $112", "up $72")
    }

    @Test
    fun aPaymentIsOneCheckboxThatTicks() {
        val state = CashScenes.settling(kit).uiState.value
        show(state)
        val theo = state.transfers[0]
        compose.onNodeWithText("Theo pays Dana").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .assert(hasText("$47"))
            .performClick()
        compose.onNodeWithText("Marcus pays Dana").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        assertEquals(listOf(CashIntent.SetPaid(theo, false), CashIntent.SetPaid(state.transfers[1], true)), sent)
        compose.onNodeWithText("Share as text").performScrollTo().assertIsEnabled()
    }

    @Test
    fun anUnbalancedCountOffersTheSplitAndHoldsBackTheShare() {
        show(CashScenes.off(kit).uiState.value)
        compose.onNodeWithText("OFF BY $5").assertExists()
        compose.onNodeWithText("Split the $5").performScrollTo().performClick()
        compose.onNodeWithText("Share as text").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Who pays whom waits for the chip check: recount, or split the difference.").assertExists()
        assertEquals(listOf<CashIntent>(CashIntent.SplitDifference), sent)
    }

    @Test
    fun aChosenSplitSaysSoAndCanBeTakenBack() {
        show(CashScenes.split(kit).uiState.value)
        compose.onNodeWithText("SPLIT").assertExists()
        compose.onNodeWithText("The extra $5 comes off the stacks, each in proportion to its chips.").assertExists()
        compose.onNodeWithText("Recount instead").performScrollTo().performClick()
        assertEquals(listOf<CashIntent>(CashIntent.Recount), sent)
    }

    @Test
    fun theTopBarHasUndoAndClearBehindMore() {
        show(CashScenes.balanced(kit).uiState.value)
        compose.onNodeWithContentDescription("Undo: Theo cashed out $33").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Clear the cash game").performClick()
        compose.onNodeWithText("Cash game").performClick()
        compose.onNodeWithText("Tournament").performClick()
        assertEquals(
            listOf(
                CashIntent.Undo,
                CashIntent.ClearGame,
                CashIntent.SwitchMode(BankMode.CASH),
                CashIntent.SwitchMode(BankMode.TOURNAMENT),
            ),
            sent,
        )
    }

    @Test
    fun anEmptyGameSaysWhatToDo() {
        show(CashScenes.empty(kit).uiState.value)
        compose.onNodeWithText("Nobody at the table yet").assertExists()
        compose.onNodeWithText("Cash game · nobody in yet").assertExists()
        compose.onNodeWithContentDescription("Nothing to undo").assertIsNotEnabled()
        compose.onNodeWithText("Add player").performClick()
        assertEquals(listOf<CashIntent>(CashIntent.ShowAddPlayer), sent)
    }

    // The sheets --------------------------------------------------------------------------------------

    @Test
    fun thePlayerSheetTopsUpCountsAndRenames() {
        val state = CashScenes.balanced(kit).uiState.value
        val theo = state.players.first { it.name == "Theo" }
        setContent { CashPlayerSheetContent(theo, state, onIntent = { sent += it }, onDone = {}) }

        // Top up at the last amount, then at one typed in
        compose.onNodeWithText("Top up $20").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Top-up for Theo").performTextReplacement("25.50")
        compose.onNodeWithText("Top up $25.50").performScrollTo().performClick()
        // A top-up recorded by mistake goes
        compose.onNodeWithContentDescription("Remove Top-up 2, $20").performClick()
        // The count, committed on Done
        compose.onNodeWithContentDescription("Theo's chips counted out").performScrollTo().performTextReplacement("40")
        compose.onNodeWithContentDescription("Theo's chips counted out").performImeAction()
        // The name, committed on Done
        compose.onNodeWithContentDescription("Name").performScrollTo().performTextReplacement("Theodore")
        compose.onNodeWithContentDescription("Name").performImeAction()
        compose.waitForIdle()
        assertEquals(
            listOf(
                CashIntent.TopUp(theo.id, 2_000L),
                CashIntent.TopUp(theo.id, 2_550L),
                CashIntent.RemoveBuyIn(theo.id, 2),
                CashIntent.SetCashOut(theo.id, 4_000L),
                CashIntent.Rename(theo.id, "Theodore"),
            ),
            sent,
        )
        compose.onNodeWithText("Down $47 on the night").assertExists()
        compose.onNodeWithText("Remove Theo").performScrollTo().performClick()
        assertEquals(CashIntent.RemovePlayer(theo.id), sent.last())
    }

    @Test
    fun clearingTheCountMeansNotCountedYet() {
        val state = CashScenes.balanced(kit).uiState.value
        val sam = state.players.first { it.name == "Sam" }
        setContent { CashPlayerSheetContent(sam, state, onIntent = { sent += it }, onDone = {}) }
        // Sam lost everything: his count shows as 0, not empty
        compose.onNodeWithText("0").assertExists()
        compose.onNodeWithContentDescription("Sam's chips counted out").performScrollTo().performTextReplacement("")
        compose.onNodeWithContentDescription("Sam's chips counted out").performImeAction()
        compose.waitForIdle()
        assertEquals(listOf<CashIntent>(CashIntent.SetCashOut(sam.id, null)), sent)
    }

    @Test
    fun theAddSheetStartsAtTheLastBuyIn() {
        val state = CashScenes.balanced(kit).uiState.value
        setContent { CashAddPlayerSheetContent(state, onIntent = { sent += it }, onDismiss = {}) }
        compose.onNodeWithText("Leave empty for Player 7").assertExists()
        compose.onNodeWithText("Add Player 7").assertIsEnabled()
        compose.onNodeWithContentDescription("Name").performTextReplacement("Ben")
        compose.onNodeWithText("Add Ben").performClick()
        compose.onNodeWithContentDescription("Buy-in").performTextReplacement("")
        compose.onNodeWithText("Add Ben").assertIsNotEnabled()
        // The last player to sit down (Theo) bought in for $40
        assertEquals(listOf<CashIntent>(CashIntent.AddPlayer("Ben", 4_000L)), sent)
    }


    // The real route ----------------------------------------------------------------------------------

    @Test
    fun theRouteSwitchesBetweenTheTwoGamesAndKeepsBoth() = with(kit) {
        bank.configure(players = 4, buyIn = 20.0)
        val tournament = bank.newViewModel()
        val cash = CashScenes.balanced(kit)
        setContent { BankRoute(viewModel = tournament, cashViewModel = cash) }

        // The tournament's Bank, with the switch on top
        compose.onNodeWithContentDescription("Player 1, buy-in, not paid").performScrollTo().performClick()
        bank.settle()
        compose.onNodeWithText("Cash game").performClick()
        compose.waitForIdle()
        assertEquals(BankMode.CASH, cash.uiState.value.mode)
        compose.onNodeWithText("CHIP CHECK").assertExists()

        compose.onNodeWithText("Tournament").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Player 1, buy-in, paid").performScrollTo().assertExists()
        assertTrue(tournament.uiState.value.players.first().buyIn)
        assertEquals(6, cash.uiState.value.players.size)
    }

    @Test
    fun shareSendsTheSettleUpAsPlainText() = with(kit) {
        val tournament = bank.newViewModel()
        val cash = CashScenes.settling(kit)
        cash.acceptIntent(CashIntent.SwitchMode(BankMode.CASH))
        setContent { BankRoute(viewModel = tournament, cashViewModel = cash) }
        compose.onNodeWithText("Share as text").performScrollTo().performClick()
        compose.waitForIdle()
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val text = send.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text, text.startsWith("Poker night: cash game\nCash in $260 · counted out $260 · balanced"))
        assertTrue(text, text.contains("Theo pays Dana $47 (paid)\nMarcus pays Dana $25"))
        assertEquals(CashTransfer(6, 1, 4_700L), cash.uiState.value.transfers.first())
    }
}
