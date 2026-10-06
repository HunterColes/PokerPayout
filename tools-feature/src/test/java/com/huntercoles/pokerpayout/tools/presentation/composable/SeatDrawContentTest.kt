package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawIntent
import com.huntercoles.pokerpayout.tools.presentation.SeatDrawUiState
import com.huntercoles.pokerpayout.tools.seats.DrawnTable
import com.huntercoles.pokerpayout.tools.seats.SeatDraw
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Seat draw (S14): the controls send the right intents, each seat is one TalkBack stop ("Seat 3,
 * Alice, table 1"), the button is named by seat with its blinds, the rule is stated, and the deal
 * plays out over about a second, or at once under Reduce motion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class SeatDrawContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<SeatDrawIntent>()
    private val shared = mutableListOf<SeatDraw>()

    private fun show(state: SeatDrawUiState, reducedMotion: Boolean = true, playersOpen: Boolean = false) {
        compose.setContent {
            PokerTheme(reducedMotion = reducedMotion) {
                SeatDrawContent(
                    state,
                    onIntent = { sent += it },
                    onBack = {},
                    onShare = { shared += it },
                    playersOpen = playersOpen,
                )
            }
        }
    }

    /** Dana, Marcus, Priya and Theo at one table; K♥ K♠ 2♦ 9♣: Marcus has the button on suit. */
    private val dealtFour = SeatDrawUiState(
        players = listOf("Dana", "Marcus", "Priya", "Theo"),
        draw = SeatDraw(listOf(DrawnTable(1, listOf("Dana", "Marcus", "Priya", "Theo"), Cards.parseAll("Kh Ks 2d 9c")))),
    )

    @Test
    fun `before a draw, the Bank's players, the steppers, the plan, and Draw seats`() {
        show(SeatDrawFixtures.empty)
        compose.onNodeWithText("From the Bank").assertExists()
        compose.onNodeWithText("Dana, Marcus, Priya, Theo, Jo, Sam, Alex, Rita, Ben").assertExists()
        compose.onNodeWithText("1 table of 9").assertExists()
        compose.onNodeWithContentDescription("Share the seats").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase Players").performClick()
        compose.onNodeWithContentDescription("Decrease Seats per table").performClick()
        compose.onNodeWithText("Edit names").performClick()
        compose.onNodeWithText("Draw seats").performScrollTo().performClick()
        assertEquals(
            listOf(
                SeatDrawIntent.SetPlayerCount(10),
                SeatDrawIntent.SetSeatsPerTable(8),
                SeatDrawIntent.EditNames(open = true),
                SeatDrawIntent.DrawSeats,
            ),
            sent,
        )
    }

    @Test
    fun `name fields are numbered, blank ones show their Player N, and typing renames`() {
        show(SeatDrawFixtures.editing)
        compose.onNodeWithText("Changed for this draw").assertExists()
        compose.onNodeWithText("Player 9").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Player 9 name").performScrollTo().performTextInput("Zed")
        // The field keeps what was typed while the saved list (here never updated) catches up
        compose.onNodeWithContentDescription("Player 9 name").assert(hasText("Zed"))
        compose.onNodeWithText("Use the Bank's names").performScrollTo().performClick()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(
            listOf(SeatDrawIntent.SetName(8, "Zed"), SeatDrawIntent.UseBankNames, SeatDrawIntent.EditNames(open = false)),
            sent,
        )
    }

    @Test
    fun `each seat is one TalkBack stop, table by table, and the players fold to one line`() {
        show(SeatDrawFixtures.twoTables)
        val draw = checkNotNull(SeatDrawFixtures.twoTables.draw)
        draw.tables.forEach { table ->
            compose.onNodeWithText("TABLE ${table.number}").performScrollTo()
            table.seats.forEachIndexed { index, name ->
                compose.onNodeWithContentDescription("Seat ${index + 1}, $name, table ${table.number}").performScrollTo()
            }
        }
        compose.onNodeWithText("14 players\u00A0· 9 per table\u00A0· from the Bank").assertExists()
        compose.onNodeWithContentDescription("Increase Players").assertDoesNotExist()
        compose.onNodeWithText("Who's playing", substring = true, ignoreCase = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Increase Players").assertExists()
    }

    @Test
    fun `with seats drawn, deal for the button, redraw, share, and the rule in words`() {
        show(SeatDrawFixtures.oneTable)
        compose.onNodeWithText("Deal for the button").performClick()
        compose.onNodeWithText("Redraw seats").performClick()
        compose.onNodeWithContentDescription("Share the seats").performClick()
        compose.onNodeWithContentDescription(
            "High card gets the button. If ranks tie, the suit decides: spades, then hearts, then diamonds, then clubs.",
        ).assertExists()
        assertEquals(listOf(SeatDrawIntent.DealButton, SeatDrawIntent.DrawSeats), sent)
        assertEquals(listOf(SeatDrawFixtures.oneTable.draw), shared)
    }

    @Test
    fun `after the deal the button is named by seat, and each seat reads its card and its blind`() {
        show(dealtFour)
        compose.onNodeWithText("Button: Marcus, seat 2").assertExists()
        compose.onNodeWithText("Tie on kings: K♠︎ wins on suit.").assertExists()
        compose.onNodeWithContentDescription("Seat 1, Dana, table 1, King of hearts").assertExists()
        compose.onNodeWithContentDescription("Seat 2, Marcus, table 1, King of spades, Button").assertExists()
        compose.onNodeWithContentDescription("Seat 3, Priya, table 1, 2 of diamonds, Small blind").assertExists()
        compose.onNodeWithContentDescription("Seat 4, Theo, table 1, 9 of clubs, Big blind").assertExists()
        compose.onNodeWithText("Deal for the button").assertDoesNotExist()
        compose.onNodeWithText("Deal again").performClick()
        assertEquals(listOf<SeatDrawIntent>(SeatDrawIntent.DealButton), sent)
    }

    @Test
    fun `a fresh deal lands seat by seat, then shows the button`() {
        compose.mainClock.autoAdvance = false
        show(dealtFour.copy(dealToAnimate = 1), reducedMotion = false)
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText("Button: Marcus, seat 2").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("King of spades", substring = true).assertCountEquals(0)

        compose.mainClock.advanceTimeBy(DEAL_MS)
        compose.onNodeWithText("Button: Marcus, seat 2").assertExists()
        compose.onNodeWithContentDescription("Seat 2, Marcus, table 1, King of spades, Button").assertExists()
    }

    @Test
    fun `under Reduce motion the deal is already done`() {
        compose.mainClock.autoAdvance = false
        show(dealtFour.copy(dealToAnimate = 1), reducedMotion = true)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Button: Marcus, seat 2").assertExists()
    }

    @Test
    fun `an out-of-date draw says so`() {
        show(SeatDrawFixtures.stale)
        compose.onNodeWithText("The players changed since this draw. Redraw seats to seat everyone.").assertExists()
        compose.onNodeWithText("16 players\u00A0· 9 per table\u00A0· changed for this draw").assertExists()
    }

    private companion object {
        /** Four seats: three 110 ms staggers and a 220 ms flip, with room to spare. */
        const val DEAL_MS = 1_000L
    }
}
