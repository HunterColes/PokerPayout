package com.huntercoles.pokerpayout.bank.presentation.composable

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankRowModel
import com.huntercoles.pokerpayout.bank.presentation.BankScenes
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.DANA
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.JO
import com.huntercoles.pokerpayout.bank.presentation.BankScenes.THEO
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankTestKit
import com.huntercoles.pokerpayout.bank.presentation.BankViewModel
import com.huntercoles.pokerpayout.bank.presentation.PlayerData
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.design.components.LocalShellSnackbars
import com.huntercoles.pokerpayout.core.design.components.PokerAppShell
import com.huntercoles.pokerpayout.core.design.components.PokerSnackbarHost
import com.huntercoles.pokerpayout.core.design.components.RequestShellChrome
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-135: the knockout from the full-screen clock, through its panel ([QuickKnockoutRoute]) on a
 * real Bank ViewModel over real preferences, against the same knockout from the Bank (its Out cell
 * and its sheet's answer, as [BankContent] sends them). In every bounty mode the two leave exactly
 * the same records behind, key for key, so everything read from them comes out the same: the
 * Bank's rows, places and bounties, the mystery envelopes drawn and left, the payouts, and the
 * players left that the clock counts. Then Undo, the snackbar rule (a new Undo replaces the old
 * one), and Back and Close recording nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w780dp-h360dp-land-xhdpi")
class QuickKnockoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    /** Whether the clock's panel is up; [QuickKnockoutRoute] puts it away when it is done. */
    private var open by mutableStateOf(false)
    private var closings = 0

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

    /** Everything a knockout leaves behind: the Bank's records key by key, and what the Bank shows of them. */
    private data class Recorded(
        val prefs: Map<String, Any?>,
        val players: List<PlayerData>,
        val order: List<Int>,
        val rows: List<BankRowModel>,
        val payouts: PayoutTable,
        val envelopesLeft: List<Long>,
        val playersLeft: Int,
        val snackbar: String?,
    )

    private fun BankTestKit.recorded(viewModel: BankViewModel, snackbar: String?): Recorded {
        val state = viewModel.uiState.value
        return Recorded(
            prefs = context.getSharedPreferences("bank_prefs", Context.MODE_PRIVATE).all.toSortedMap(),
            players = state.players,
            order = state.eliminationOrder,
            rows = state.rows,
            payouts = state.payoutTable,
            envelopesLeft = state.envelopesLeft,
            playersLeft = state.activePlayers,
            snackbar = snackbar,
        )
    }

    private fun scene(mode: BountyMode): BankViewModel = when (mode) {
        BountyMode.STANDARD -> BankScenes.midGame(kit)
        BountyMode.PROGRESSIVE -> BankScenes.progressive(kit)
        BountyMode.MYSTERY -> BankScenes.mystery(kit)
    }

    /** The clock's panel over [viewModel], open. */
    private fun showPanel(viewModel: BankViewModel) {
        open = true
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                if (open) {
                    QuickKnockoutRoute(
                        onClose = {
                            open = false
                            closings++
                        },
                        viewModel = viewModel,
                    )
                }
            }
        }
        sync()
    }

    /** Lets the ViewModel's flows reach the screen (they run on the test dispatcher) and the screen settle. */
    private fun sync() {
        repeat(SYNC_ROUNDS) {
            kit.runCurrent()
            compose.waitForIdle()
        }
    }

    /** Taps [text], scrolled into view first unless it sits outside the list ([scroll] false: Nobody). */
    private fun tap(text: String, scroll: Boolean = true) {
        val node = compose.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.performClick()
        sync()
    }

    private fun tapDescribed(description: String) {
        compose.onNodeWithContentDescription(description).performClick()
        sync()
    }

    /**
     * Knocks Theo out in [mode], credited to [eliminator] (null: nobody), once from the Bank and once
     * from the clock's panel, each on the same fresh night, and checks both leave the same behind.
     */
    private fun assertSameAsTheBank(mode: BountyMode, eliminator: Int?) {
        // From the Bank: Theo's Out cell opens the sheet, the sheet's answer knocks him out
        val fromBank = run {
            val viewModel = scene(mode)
            viewModel.acceptIntent(BankIntent.OpenKnockout(THEO))
            viewModel.acceptIntent(BankIntent.KnockOut(THEO, eliminator))
            val message = kit.snackbarMessage()
            kit.settle()
            kit.recorded(viewModel, message)
        }
        kit.clear()
        kit = BankTestKit(dispatcher) // the same night again, with the same seeded envelope draws

        // From the clock: Knock out, Theo, then who did it
        val viewModel = scene(mode)
        showPanel(viewModel)
        compose.onNodeWithText("Who's out?").assertIsDisplayed()
        tap("Theo")
        compose.onNodeWithText("Who knocked Theo out?").assertIsDisplayed()
        val credited = eliminator?.let { BankScenes.NAMES[it - 1] }
        if (credited != null) tap(credited) else tap(nobodyLabel(mode), scroll = false)
        val message = kit.snackbarMessage()
        if (mode == BountyMode.MYSTERY && credited != null) {
            // The envelope opens in the panel; the clock comes back once it has been seen
            compose.onNodeWithText("$credited draws").assertIsDisplayed()
            assertTrue("open while the envelope shows", open)
            tap("Close")
        }
        kit.settle()
        sync()
        val fromClock = kit.recorded(viewModel, message)

        assertTrue("$mode: the panel closes once the knockout is recorded", !open && closings == 1)
        assertNull("$mode: no sheet left behind", viewModel.uiState.value.sheet)
        assertEquals("$mode, credited to $eliminator", fromBank, fromClock)
        assertTrue("$mode: Theo is out", fromClock.players.first { it.id == THEO }.out)
    }

    private fun nobodyLabel(mode: BountyMode) = when (mode) {
        BountyMode.MYSTERY -> "Nobody · envelope stays for the champion"
        else -> "Nobody · bounty to the champion"
    }

    @Test
    fun aStandardKnockoutFromTheClockIsTheBanksKnockout() = assertSameAsTheBank(BountyMode.STANDARD, DANA)

    @Test
    fun aProgressiveKnockoutFromTheClockIsTheBanksKnockout() = assertSameAsTheBank(BountyMode.PROGRESSIVE, DANA)

    @Test
    fun aMysteryKnockoutFromTheClockDrawsTheBanksEnvelope() = assertSameAsTheBank(BountyMode.MYSTERY, DANA)

    @Test
    fun aKnockoutCreditedToNobodyIsTheBanksToo() {
        assertSameAsTheBank(BountyMode.STANDARD, null)
    }

    @Test
    fun aMysteryKnockoutCreditedToNobodyDrawsNothing() = assertSameAsTheBank(BountyMode.MYSTERY, null)

    @Test
    fun theSecondQuestionShowsWhatTheKnockoutPays() {
        val viewModel = BankScenes.progressive(kit)
        showPanel(viewModel)
        tap("Theo")
        // 6th place (Ben, Rita and Alex are out); Theo's $5 is at stake; Dana's bounty has grown to
        // $10, Marcus's to $7.50
        compose.onNodeWithText("6TH PLACE").assertIsDisplayed()
        compose.onNodeWithText("\$5 BOUNTY").assertIsDisplayed()
        compose.onNodeWithText("bounty \$10").assertExists()
        compose.onNodeWithText("bounty \$7.50").assertExists()
        // Those already out can be credited too, as in the Bank's sheet
        compose.onNodeWithText("Already out".uppercase()).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rita").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun undoOnTheSnackbarPutsTheKnockoutAndItsEnvelopeBack() {
        val viewModel = BankScenes.mystery(kit)
        val before = kit.recorded(viewModel, null)
        showPanel(viewModel)
        tap("Theo")
        tap("Dana")
        assertNotEquals(before.envelopesLeft, viewModel.uiState.value.envelopesLeft)

        // Undo while the envelope is still showing: it goes back in the pool, and the clock comes back
        kit.pressSnackbarUndo()
        sync()
        assertTrue("the panel closes", !open)
        val after = kit.recorded(viewModel, null)
        assertEquals(before.copy(prefs = emptyMap()), after.copy(prefs = emptyMap()))
        // And as saved: a Bank opened now reads the night as it was
        val reopened = kit.newViewModel()
        assertEquals(before.players to before.order, reopened.uiState.value.players to reopened.uiState.value.eliminationOrder)
        assertEquals(before.envelopesLeft, reopened.uiState.value.envelopesLeft)
    }

    @Test
    fun aNewUndoReplacesTheOldOne() {
        val viewModel = BankScenes.midGame(kit)
        showPanel(viewModel)
        tap("Theo")
        tap("Dana")
        assertEquals("Theo is out in 7th · bounty to Dana", kit.snackbarMessage())

        // Two out in the same hand: the second knockout's snackbar replaces the first's
        open = true
        sync()
        tap("Jo")
        tap("Marcus")
        assertEquals("Jo is out in 6th · bounty to Marcus", kit.snackbarMessage())

        // Its Undo takes back the second knockout only
        kit.pressSnackbarUndo()
        sync()
        val players = viewModel.uiState.value.players.associateBy { it.id }
        assertTrue("Jo is back in", !players.getValue(JO).out)
        assertTrue("Theo stays out", players.getValue(THEO).out)
        assertEquals(DANA, players.getValue(THEO).eliminatedBy)
        assertNull("Jo's credit to Marcus goes with it", players.getValue(JO).eliminatedBy)
        assertNull("nothing else to undo from the snackbar", kit.snackbars.hostState.currentSnackbarData)
    }

    /**
     * The clock and the Bank tab share the Bank's one ViewModel (bankViewModel): a knockout from the
     * clock is the newest action in the Bank's history, and the top bar's Undo takes it back.
     */
    @Test
    fun theBanksUndoTakesBackAKnockoutFromTheClock() {
        val viewModel = BankScenes.midGame(kit)
        val before = kit.recorded(viewModel, null)
        showPanel(viewModel)
        tap("Theo")
        tap("Dana")
        kit.settle() // the snackbar's 8 s are over
        assertEquals("Theo is out in 7th · bounty to Dana", viewModel.uiState.value.undoLabel)

        viewModel.acceptIntent(BankIntent.Undo)
        kit.settle()
        assertEquals(before.copy(prefs = emptyMap()), kit.recorded(viewModel, null).copy(prefs = emptyMap()))
    }

    @Test
    fun backAndCloseRecordNothing() {
        val viewModel = BankScenes.midGame(kit)
        val before = kit.recorded(viewModel, null)
        showPanel(viewModel)
        tap("Theo")
        tapDescribed("Back to who's out")
        compose.onNodeWithText("Who's out?").assertIsDisplayed()
        tap("Theo")
        tapDescribed("Close knockout")
        assertTrue("closed", !open)
        kit.settle()
        assertEquals(before, kit.recorded(viewModel, null))
        assertNull(viewModel.uiState.value.sheet)
        assertNull("no snackbar", kit.snackbars.hostState.currentSnackbarData)
    }

    @Test
    fun theFirstQuestionListsOnlyThePlayersStillIn() {
        val viewModel = BankScenes.midGame(kit)
        showPanel(viewModel)
        listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        // Rita and Ben are out already
        compose.onNodeWithText("Rita").assertDoesNotExist()
        compose.onNodeWithText("Ben").assertDoesNotExist()
        // TalkBack says what a tap does: "double-tap to knock out Theo"
        compose.onNodeWithText("Theo").assert(
            SemanticsMatcher("knocks Theo out") { it.config[SemanticsActions.OnClick].label == "Knock out Theo" },
        )
        assertTrue(viewModel.uiState.value.sheet !is BankSheet.Knockout)
    }

    /**
     * As on the full-screen clock: the app's shell asked for the whole window, which hosts the
     * app's snackbars itself (the table view's TableSnackbars, here a plain host). The Undo after a
     * knockout from the panel shows there.
     */
    @Test
    fun theUndoShowsOnAScreenThatHasTheWholeWindow() {
        val viewModel = BankScenes.midGame(kit)
        open = true
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                PokerAppShell(
                    items = pokerNavItems(),
                    selectedIndex = 0,
                    onSelect = {},
                    snackbarHostState = kit.snackbars.hostState,
                ) {
                    RequestShellChrome(immersive = true)
                    Box(Modifier.fillMaxSize()) {
                        if (open) QuickKnockoutRoute(onClose = { open = false }, viewModel = viewModel)
                        LocalShellSnackbars.current?.let { PokerSnackbarHost(it, Modifier.align(Alignment.BottomStart)) }
                    }
                }
            }
        }
        sync()
        tap("Theo")
        tap("Dana")
        assertTrue("closed", !open)
        compose.onNodeWithText("Theo is out in 7th · bounty to Dana").assertIsDisplayed()
        compose.onNodeWithText("UNDO").performClick()
        sync()
        kit.settle()
        assertTrue("Theo is back in", !viewModel.uiState.value.players.first { it.id == THEO }.out)
    }

    private companion object {
        const val SYNC_ROUNDS = 3
    }
}
