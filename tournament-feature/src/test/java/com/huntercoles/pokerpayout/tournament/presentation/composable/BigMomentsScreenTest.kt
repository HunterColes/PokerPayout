package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.presentation.LocalTableKnockouts
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment
import com.huntercoles.pokerpayout.tournament.presentation.MomentBanner
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.payouts.NightSave
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutRowModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-111 on screen: a big moment's banner on the clock (it says what happened, TalkBack hears it,
 * and it goes after its time), the champion's card that leads back to their screen, and the
 * champion's screen itself (S25): what each of its buttons asks for, Back on it, and the last
 * knockout's mystery envelope showing over it first. The clock's ViewModel is BigMomentsClockTest's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w780dp-h360dp-land-xhdpi")
class BigMomentsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture
    private val sent = mutableListOf<TimerIntent>()
    private val asked = mutableListOf<WinnerAction>()
    private var state by mutableStateOf(TimerUiState())

    @Before
    fun setUp() {
        fixture = TournamentFixture(store)
    }

    @After
    fun tearDown() = store.clear()

    private fun TimerUiState.withMoment(moment: BigMoment, left: Int, names: List<String> = emptyList()) = copy(
        moment = MomentBanner(moment, id = 7, playersLeft = left, names = names, lowestPrizeCents = 9_500L),
        table = table.copy(playersLeft = left),
    )

    /** One player left, Dana, the champion. */
    private fun TimerUiState.championDana() = copy(table = table.copy(playersLeft = 1, championName = "Dana"))

    private fun showSlot(shown: TimerUiState, reducedMotion: Boolean = true) {
        state = shown
        compose.setContent {
            PokerTheme(reducedMotion = reducedMotion) { MomentSlot(state.momentSlot, onIntent = { sent += it }) }
        }
        compose.waitForIdle()
    }

    @Test
    fun eachMomentSaysWhatHappened() {
        showSlot(fixture.running.withMoment(BigMoment.BUBBLE, left = 4))
        compose.onNodeWithText("On the bubble").assertIsDisplayed()
        compose.onNodeWithText("One more out, then everyone left is paid.").assertIsDisplayed()

        state = fixture.running.withMoment(BigMoment.IN_THE_MONEY, left = 3)
        compose.waitForIdle()
        compose.onNodeWithText("In the money!").assertIsDisplayed()
        compose.onNodeWithText("Everyone left wins at least $95.").assertIsDisplayed()

        state = fixture.running.withMoment(BigMoment.FINAL_TABLE, left = 9)
        compose.waitForIdle()
        compose.onNodeWithText("Final table").assertIsDisplayed()
        compose.onNodeWithText("9 players left, all at one table.").assertIsDisplayed()

        state = fixture.running.withMoment(BigMoment.HEADS_UP, left = 2, names = listOf("Dana", "Marcus"))
        compose.waitForIdle()
        compose.onNodeWithText("Heads-up").assertIsDisplayed()
        compose.onNodeWithText("Dana against Marcus").assertIsDisplayed()
    }

    @Test
    fun talkBackHearsTheMomentAsItComes() {
        showSlot(fixture.running.withMoment(BigMoment.BUBBLE, left = 4))
        compose.onNode(
            hasText("On the bubble") and SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        ).assertExists()
    }

    @Test
    fun aMomentGoesAfterItsTime() {
        // The clock moves only when the test says, so the banner's glint and its time are in step with it
        compose.mainClock.autoAdvance = false
        showSlot(fixture.running.withMoment(BigMoment.BUBBLE, left = 4), reducedMotion = false)
        compose.mainClock.advanceTimeBy(MomentTiming.SHOW_MILLIS - 500)
        compose.onNodeWithText("On the bubble").assertIsDisplayed()
        assertEquals(emptyList<TimerIntent>(), sent)
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        assertEquals(listOf<TimerIntent>(TimerIntent.MomentSeen(7)), sent)

        // Seen, the clock's state has no moment: the slot is empty and takes no room
        state = fixture.running
        compose.waitForIdle()
        compose.onNodeWithText("On the bubble").assertDoesNotExist()
    }

    @Test
    fun theChampionsCardLeadsBackToTheirScreen() {
        val running = fixture.running
        showSlot(running.copy(table = running.table.copy(playersLeft = 1, championName = "Dana")))
        compose.onNodeWithText("Dana is the champion").assertIsDisplayed()
        compose.onNodeWithText("See the results").performClick()
        assertEquals(listOf<TimerIntent>(TimerIntent.OpenWinner), sent)

        // While their screen is open, the card isn't on the clock under it
        state = state.copy(winnerOpen = true)
        compose.waitForIdle()
        compose.onNodeWithText("Dana is the champion").assertDoesNotExist()
    }

    private val winner = WinnerModel(
        championName = "Dana",
        prizeCents = 22_500L,
        bountyCents = 1_000L,
        rows = listOf(
            PayoutRowModel(1, 22_500L, 50.0, "Dana"),
            PayoutRowModel(2, 13_000L, 28.9, "Marcus"),
            PayoutRowModel(3, 9_500L, 21.1, "Priya"),
        ),
        night = NightSave.NotOver,
    )

    private var shownWinner by mutableStateOf(winner)

    private fun showWinner(model: WinnerModel) {
        shownWinner = model
        compose.setContent {
            PokerTheme(reducedMotion = true) { WinnerContent(shownWinner, onAction = { asked += it }) }
        }
        compose.waitForIdle()
    }

    /** The champion's name, the screen's heading (the payouts list names them too). */
    private fun championName(name: String) = compose.onNode(hasText(name) and isHeading())

    @Test
    fun theChampionsScreenSaysWhoWonAndWhatEveryPaidPlaceWon() {
        showWinner(winner)
        compose.onNodeWithText("CHAMPION").assertIsDisplayed()
        championName("Dana").assertIsDisplayed()
        compose.onNodeWithText("Wins $225").assertIsDisplayed()
        compose.onNodeWithText("Plus $10 in bounties").assertIsDisplayed()
        compose.onNodeWithText("Marcus").assertIsDisplayed()
        compose.onNodeWithText("$130").assertIsDisplayed()
        compose.onNodeWithText("Priya").assertIsDisplayed()
    }

    @Test
    fun beforeEveryoneIsPaidItLeadsToTheBank() {
        showWinner(winner)
        compose.onNodeWithText("Pay everyone in the Bank, then save this night to History.").assertIsDisplayed()
        compose.onNodeWithText("Save this night").assertDoesNotExist()
        compose.onNodeWithText("Pay out in the Bank").performClick()
        compose.onNodeWithContentDescription("Back to the clock").performClick()
        assertEquals(listOf(WinnerAction.OpenBank, WinnerAction.Close), asked)
    }

    @Test
    fun onceEveryoneIsPaidItSavesTheNightAndThenSaysWhere() {
        showWinner(winner.copy(night = NightSave.Offered))
        compose.onNodeWithText("Save this night").performClick()
        assertEquals(listOf(WinnerAction.SaveNight), asked)

        shownWinner = winner.copy(night = NightSave.Saved)
        compose.waitForIdle()
        compose.onNodeWithText("Saved to History, in the Tools tab.").assertIsDisplayed()
        compose.onNodeWithText("Save this night").assertDoesNotExist()
        compose.onNodeWithText("Open History").performClick()
        assertEquals(listOf(WinnerAction.SaveNight, WinnerAction.OpenHistory), asked)
    }

    private fun showTable(shown: TimerUiState) {
        state = shown
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                CompositionLocalProvider(LocalTableKnockouts provides FakeTableKnockouts) {
                    TableViewContent(
                        state,
                        onIntent = { sent += it },
                        onExit = {},
                        winner = WinnerPane(winner) { asked += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun onTheTableViewTheChampionsScreenTakesTheClocksPlaceAndBackClosesIt() {
        val running = fixture.running
        showTable(running.championDana().copy(isTableView = true, winnerOpen = true))
        championName("Dana").assertIsDisplayed()
        compose.onNodeWithText("Knock out").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf<TimerIntent>(TimerIntent.CloseWinner), sent)
    }

    @Test
    fun theLastKnockoutsEnvelopeShowsOverTheChampionsScreenFirst() {
        val running = fixture.running
        showTable(running.copy(isTableView = true, table = running.table.copy(playersLeft = 2)))
        compose.onNodeWithText("Knock out").performClick()
        compose.waitForIdle()
        // The knockout leaves one player: the champion's screen opens under the panel's envelope
        state = state.copy(winnerOpen = true, table = state.table.copy(playersLeft = 1, championName = "Dana"))
        compose.waitForIdle()
        compose.onNodeWithText(FakeTableKnockouts.OPEN).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to the clock").assertDoesNotExist() // TalkBack skips it

        // Back puts the envelope away first, then closes the champion's screen
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(FakeTableKnockouts.OPEN).assertDoesNotExist()
        championName("Dana").assertIsDisplayed()
        assertEquals(emptyList<TimerIntent>(), sent)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf<TimerIntent>(TimerIntent.CloseWinner), sent)
    }

    @Test
    fun theTableViewShowsAMomentOverTheDigitsAndKeepsItsControls() {
        showTable(fixture.running.copy(isTableView = true).withMoment(BigMoment.IN_THE_MONEY, left = 3))
        compose.onNodeWithText("In the money!").assertIsDisplayed()
        compose.onNodeWithText("Knock out").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit table view").assertIsDisplayed()
        compose.onNodeWithText("IN THE MONEY").assertIsDisplayed() // the pill, as before
    }
}
