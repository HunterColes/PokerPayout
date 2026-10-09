package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.design.components.LocalShellSnackbars
import com.huntercoles.pokerpayout.core.presentation.LocalTableKnockouts
import com.huntercoles.pokerpayout.core.presentation.TableKnockouts
import com.huntercoles.pokerpayout.tournament.presentation.MoneyStage
import com.huntercoles.pokerpayout.tournament.presentation.TableStats
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-135 on the table view (S3): Knock out opens the Bank's knockout over the clock (a stand-in
 * here, [FakeTableKnockouts]); while it is open the clock's controls are out of TalkBack's reach
 * and Back puts the knockout away, not the table view. No button with one player left, or where
 * there is no Bank. The snackbar's Undo shows on the table view, and the players left say when it
 * is the bubble. The paid places come from the one payout calculation, through the clock's
 * ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w780dp-h360dp-land-xhdpi")
class TableKnockoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture

    @Before
    fun setUp() {
        fixture = TournamentFixture(store)
    }

    @After
    fun tearDown() = store.clear()

    /** The table view on show; a test may change it after [show]. */
    private var state by mutableStateOf(TimerUiState())

    private fun show(
        shown: TimerUiState = fixture.running.copy(isTableView = true),
        knockouts: TableKnockouts? = FakeTableKnockouts,
    ) {
        state = shown
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalTableKnockouts provides knockouts) {
                    TableViewContent(state, onIntent = {}, onExit = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun knockOut() = compose.onNodeWithText(KNOCK_OUT)

    @Test
    fun knockOutOpensTheBanksKnockoutOverTheClock() {
        show()
        knockOut().assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(FakeTableKnockouts.OPEN).assertIsDisplayed()
        // Under the panel the clock is only to look at
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertDoesNotExist()
        knockOut().assertDoesNotExist()

        compose.onNodeWithText(FakeTableKnockouts.DONE).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(FakeTableKnockouts.OPEN).assertDoesNotExist()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertIsDisplayed()
    }

    @Test
    fun backPutsTheKnockoutAwayAndKeepsTheTableView() {
        show()
        knockOut().performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(FakeTableKnockouts.OPEN).assertDoesNotExist()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertIsDisplayed()
        knockOut().assertIsDisplayed()
    }

    @Test
    fun noKnockOutWithOnePlayerLeft() {
        val running = fixture.running
        show(running.copy(isTableView = true, table = running.table.copy(playersLeft = 1)))
        knockOut().assertDoesNotExist()
    }

    @Test
    fun noKnockOutWithoutTheBank() {
        show(knockouts = null)
        knockOut().assertDoesNotExist()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertIsDisplayed()
    }

    @Test
    fun theUndoAfterAKnockoutShowsOnTheTableView() {
        val snackbars = SnackbarHostState()
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalTableKnockouts provides FakeTableKnockouts, LocalShellSnackbars provides snackbars) {
                    TableViewContent(fixture.running.copy(isTableView = true), onIntent = {}, onExit = {})
                }
                LaunchedEffect(snackbars) {
                    snackbars.showSnackbar("Theo is out in 7th · bounty to Dana", "Undo", duration = SnackbarDuration.Indefinite)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Theo is out in 7th · bounty to Dana").assertIsDisplayed()
        compose.onNodeWithText("UNDO").assertIsDisplayed()
        // The controls stay in reach beside it
        knockOut().assertIsDisplayed()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertIsDisplayed()
    }

    @Test
    fun thePlayersLeftSayWhenItIsTheBubble() {
        val running = fixture.running
        assertEquals("the mockups' night pays 3 places", 3, running.table.paidPlaces)
        show(running.copy(isTableView = true, table = running.table.copy(playersLeft = 4)))
        compose.onNodeWithText("4 of 9 left").assertIsDisplayed()
        compose.onNodeWithText("ON THE BUBBLE").assertIsDisplayed()

        state = running.copy(isTableView = true, table = running.table.copy(playersLeft = 3))
        compose.waitForIdle()
        compose.onNodeWithText("IN THE MONEY").assertIsDisplayed()

        state = running.copy(isTableView = true)
        compose.waitForIdle()
        compose.onNodeWithText("7 of 9 left").assertIsDisplayed()
        compose.onNodeWithText("ON THE BUBBLE").assertDoesNotExist()
        compose.onNodeWithText("IN THE MONEY").assertDoesNotExist()
    }

    @Test
    fun theClockCountsThePaidPlacesAndTheBubbleFromTheBank() {
        // Five of nine out in the Bank: four left, three paid
        fixture.bankPreferences.saveEliminationOrder(listOf(9, 8, 7, 6, 5))
        val table = fixture.timerViewModel().uiState.value.table
        assertEquals(4, table.playersLeft)
        assertEquals(3, table.paidPlaces)
        assertEquals(MoneyStage.BUBBLE, table.moneyStage)
    }

    @Test
    fun whereThePlayersLeftStandAgainstTheMoney() {
        fun stage(left: Int, paid: Int = 3, players: Int = 9) =
            TableStats(playerCount = players, playersLeft = left, paidPlaces = paid).moneyStage
        assertEquals(MoneyStage.NONE, stage(left = 9))
        assertEquals(MoneyStage.NONE, stage(left = 5))
        assertEquals(MoneyStage.BUBBLE, stage(left = 4))
        assertEquals(MoneyStage.IN_THE_MONEY, stage(left = 3))
        assertEquals(MoneyStage.IN_THE_MONEY, stage(left = 2))
        assertEquals("a champion: nothing to say", MoneyStage.NONE, stage(left = 1))
        assertEquals("everyone is paid anyway", MoneyStage.NONE, stage(left = 2, paid = 2, players = 2))
        assertEquals("nothing paid", MoneyStage.NONE, stage(left = 4, paid = 0))
    }

    private companion object {
        const val KNOCK_OUT = "Knock out"
        const val EXIT_TABLE_VIEW = "Exit table view"
    }
}
