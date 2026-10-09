package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankScenes
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankTestKit
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.bank.presentation.Purchase
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.InAppShell
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
import org.robolectric.annotation.Config

/**
 * The Bank screen's controls (S5 v2, S5b, S5c): what each cell, sheet and top-bar button sends, the
 * labelled header, and renaming in place. Rendered from real ViewModel states ([BankScenes]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h915dp-port-xhdpi")
class BankContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit
    private val sent = mutableListOf<BankIntent>()

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

    private fun show(state: BankUiState) = setContent { BankContent(state = state, onIntent = { sent += it }) }

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent { PokerTheme(reducedMotion = true) { content() } }
        compose.waitForIdle()
    }

    private fun cell(description: String) = compose.onNodeWithContentDescription(description).performScrollTo()

    @Test
    fun theHeaderNamesEveryColumnAndLocksTheClosedOnes() {
        show(BankScenes.midGame(kit).uiState.value)
        listOf("Buy-in", "Rebuy, closed", "Add-on, closed", "Out", "Paid").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
        val note = "Rebuys closed after level 4 · add-ons closed after break 1. Taken ones stay filled; hold one to correct it."
        compose.onNodeWithText(note)
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun eachCellSendsWhatItDoes() {
        show(BankScenes.beforeBuyIns(kit).uiState.value)
        cell("Dana, buy-in, not paid").performClick()
        cell("Dana, rebuy, none yet").performClick()
        cell("Dana, add-on, none yet").performTouchInput { longClick() }
        cell("Knock out Dana").performClick()
        cell("Dana, paid out, nothing owed yet").performClick()
        assertEquals(
            listOf(
                BankIntent.BuyInToggled(1),
                BankIntent.AddPurchase(1, Purchase.REBUY),
                BankIntent.OpenCount(1, Purchase.ADD_ON),
                BankIntent.OpenKnockout(1),
                BankIntent.OpenPayOut(1),
            ),
            sent
        )
    }

    /** PP-116: Late entry under the list, once the clock runs, with no cutoff set. */
    @Test
    fun lateEntryUnderTheListOpensItsSheet() {
        show(BankScenes.midGame(kit).uiState.value)
        compose.onNodeWithText("Late entry").performScrollTo().performClick()
        assertEquals(listOf(BankIntent.OpenLateEntry), sent)
    }

    @Test
    fun theLateEntrySheetSendsTheNameTypedOrAReEntry() {
        val state = BankScenes.lateEntrySheet(kit).uiState.value
        val sheet = state.sheet as BankSheet.LateEntry
        setContent {
            LateEntrySheetContent(
                sheet = sheet,
                reEntries = state.reEntries,
                onAdd = { sent += BankIntent.AddLateEntry(it) },
                onReEnter = { sent += BankIntent.ReEnter(it) },
                onDismiss = { sent += BankIntent.DismissSheet },
            )
        }
        compose.onNodeWithText("\$50 to sit down · 5,000 chips").assertExists()
        compose.onNodeWithText("Open until the end of level 6.").assertExists()
        compose.onNodeWithContentDescription("Late arrival's name").performTextReplacement("Kai")
        compose.onNodeWithText("Add · \$50 paid").performScrollTo().performClick()
        compose.onNodeWithText("Rita").performScrollTo().assert(
            SemanticsMatcher("re-enters Rita") { it.config[SemanticsActions.OnClick].label == "Re-enter Rita" },
        ).performClick()
        assertEquals(listOf(BankIntent.AddLateEntry("Kai"), BankIntent.ReEnter(BankScenes.RITA)), sent)
    }

    @Test
    fun tappingAPlaceBringsThePlayerBack() {
        show(BankScenes.midGame(kit).uiState.value)
        cell("Rita, out, 8th, knocked out by Marcus. Bring back").performClick()
        assertEquals(listOf(BankIntent.BringBack(BankScenes.RITA)), sent)
    }

    @Test
    fun afterTheCutoffATapDoesNothingButAHoldStillOpensTheCount() {
        show(BankScenes.midGame(kit).uiState.value)
        cell("Dana, rebuy, closed after level 4, 0 taken").performClick()
        cell("Marcus, rebuy, closed after level 4, 1 taken").performClick()
        assertEquals(emptyList<BankIntent>(), sent)
        cell("Marcus, rebuy, closed after level 4, 1 taken").performTouchInput { longClick() }
        assertEquals(listOf(BankIntent.OpenCount(BankScenes.MARCUS, Purchase.REBUY)), sent)
    }

    @Test
    fun theChampionCantBeKnockedOutAndTheOwedAmountShows() {
        show(BankScenes.champion(kit).uiState.value)
        cell("Dana, champion").assertIsNotEnabled()
        cell("Dana, paid out, $260 owed").performClick()
        cell("Marcus, paid out").assertExists()
        assertEquals(listOf(BankIntent.OpenPayOut(BankScenes.DANA)), sent)
    }

    @Test
    fun theTopBarHasUndoTheBellAndResetBehindMore() {
        val viewModel = BankScenes.midGame(kit)
        show(viewModel.uiState.value)
        compose.onNodeWithContentDescription("Undo: Rita is out in 8th · bounty to Marcus").performClick()
        compose.onNodeWithContentDescription("Mute the clock's chime").performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Reset bank…").performClick()
        assertEquals(listOf(BankIntent.Undo, BankIntent.ToggleMute, BankIntent.ShowResetConfirm), sent)
        compose.onNodeWithText("7 of 9 left · $540 collected").assertExists()
    }

    @Test
    fun withNothingToUndoTheUndoButtonRests() {
        show(BankUiState())
        compose.onNodeWithContentDescription("Nothing to undo").assertIsNotEnabled()
    }

    @Test
    fun theKnockoutSheetSendsTheChoiceAndWaitsForOne() {
        val viewModel = with(kit) { BankScenes.midGame(kit).also { it.send(BankIntent.OpenKnockout(BankScenes.THEO)) } }
        val sheet = viewModel.uiState.value.sheet as BankSheet.Knockout
        val answers = mutableListOf<Int?>()
        setContent { KnockoutSheetContent(sheet, onKnockOut = { answers += it }, onDismiss = {}) }
        compose.onNodeWithText("Knock out Theo").assertIsNotEnabled()
        compose.onNodeWithText("Marcus").performClick()
        compose.onNodeWithText("Knock out Theo").assertIsEnabled().performClick()
        compose.onNodeWithText("Nobody · bounty to the champion").performScrollTo().performClick()
        compose.onNodeWithText("Knock out Theo").performClick()
        assertEquals(listOf(BankScenes.MARCUS, null), answers)
        compose.onNodeWithText("7th place".uppercase()).assertExists()
    }

    @Test
    fun thePayOutSheetMarksPaidForTheAmountItShows() {
        val viewModel = with(kit) { BankScenes.champion(kit).also { it.send(BankIntent.OpenPayOut(BankScenes.DANA)) } }
        val sheet = viewModel.uiState.value.sheet as BankSheet.PayOut
        val answers = mutableListOf<Boolean>()
        setContent { PayOutSheetContent(sheet, onSetPaid = { answers += it }, onDismiss = {}) }
        compose.onNodeWithText("Hand over").assertExists()
        compose.onNodeWithText("King's Bounty · own $5 back").assertExists()
        compose.onNodeWithText("Mark paid · $260").performScrollTo().performClick()
        assertEquals(listOf(true), answers)
    }

    @Test
    fun theCountSheetSetsTheCount() {
        val viewModel = with(kit) { BankScenes.rebuysOpen(kit).also { it.send(BankIntent.OpenCount(
            BankScenes.MARCUS,
            Purchase.REBUY
        )) } }
        val sheet = viewModel.uiState.value.sheet as BankSheet.Count
        val answers = mutableListOf<Int>()
        setContent { CountSheetContent(sheet, onSet = { answers += it }, onDismiss = {}) }
        compose.onNodeWithContentDescription("Increase Rebuys").performClick()
        compose.onNodeWithText("Set 3 rebuys").performClick()
        assertEquals(listOf(3), answers)
    }

    @Test
    fun settleUpIsOfferedOnceTheNightIsOverWithBuyInsStillOpen() {
        val state = BankScenes.settleUp(kit).uiState.value
        show(state)
        val payments = state.settleUp!!.transfers.size
        compose.onNodeWithText("Settle up · $payments payments").performScrollTo().performClick()
        assertEquals(listOf(BankIntent.ShowSettleUp), sent)
    }

    @Test
    fun withEveryoneBoughtInThePaidColumnIsTheWayAndSettleUpStaysAway() {
        show(BankScenes.champion(kit).uiState.value)
        compose.onNodeWithText("Settle up", substring = true).assertDoesNotExist()
    }

    @Test
    fun theSettleUpSheetTicksAPaymentAndShares() {
        val state = BankScenes.settleUp(kit).uiState.value
        val first = state.settleUp!!.transfers.first()
        val ticks = mutableListOf<Pair<Transfer, Boolean>>()
        var shared = 0
        setContent {
            SettleUpSheetContent(
                settleUp = state.settleUp!!,
                state = state,
                onSetPaid = { transfer, paid -> ticks += transfer to paid },
                onShare = { shared++ },
                onDismiss = {},
            )
        }
        compose.onNodeWithText("Tick each one when paid").assertExists()
        // The Bank owes the most, so it pays first: "The bank pays Dana", one checkbox with its amount
        compose.onNodeWithText("The bank pays Dana")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        compose.onNodeWithText("Share as text").performScrollTo().performClick()
        assertEquals(listOf(first to true), ticks)
        assertEquals(1, shared)
    }

    @Test
    fun aNameIsSavedOnDoneAndADefaultNameIsSelectedForTyping() {
        val state = with(kit) { newViewModel().also { it.send(BankIntent.PlayerNameChanged(1, "Dana")) }.uiState.value }
        show(state)
        compose.onNodeWithText("Player 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Player 2").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.TextSelectionRange,
            TextRange(0, 8)
        ))
        compose.onNodeWithText("Player 2").performTextReplacement("Marcus")
        compose.onNodeWithText("Marcus").performImeAction()
        compose.waitForIdle()
        assertTrue(sent.toString(), BankIntent.PlayerNameChanged(2, "Marcus") in sent)
    }

    @Test
    fun aNameIsSavedWhenTheFieldLosesFocus() {
        val state = with(kit) { newViewModel().uiState.value }
        show(state)
        compose.onNodeWithText("Player 3").performClick()
        compose.onNodeWithText("Player 3").performTextReplacement("Priya")
        // Focus moves to another name
        compose.onNodeWithText("Player 4").performClick()
        compose.waitForIdle()
        assertTrue(sent.toString(), BankIntent.PlayerNameChanged(3, "Priya") in sent)
    }

    /** Z5: on a tablet the list spans the whole width beside the rail; a cell outside the centre column still takes taps. */
    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun onATabletTheWideListTakesTapsAcrossItsWidth() {
        val state = BankScenes.midGame(kit).uiState.value
        setContent { InAppShell(NavTab.Bank) { BankContent(state = state, onIntent = { sent += it }) } }
        // The Paid column sits right of the old 720 dp column, and the side pane further right still
        cell("Dana, paid out, nothing owed yet").performClick()
        compose.onNodeWithText("Structure").performClick()
        assertEquals(listOf(BankIntent.OpenPayOut(BankScenes.DANA), BankIntent.ShowPayoutStructure), sent)
        compose.onNodeWithText("Pool breakdown".uppercase()).assertExists()
    }
}
