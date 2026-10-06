package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import com.huntercoles.pokerpayout.tools.presentation.SlotRef
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The odds screens report the right intent for each tap, and TalkBack reads what the mockups say:
 * "Ace of spades", "Player 2, card 2, empty. Next", "Hearts, already out", the next-card cells and
 * the "Why" sentence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OddsScreenInteractionTest {
    @get:Rule
    val screen = ScreenTestRule(ScreenConfig(Device.TallPhone, 1.0f))

    private val sent = mutableListOf<OddsCalculatorIntent>()

    private fun odds(state: OddsCalculatorUiState) = screen.compose.setContent {
        PokerTheme(reducedMotion = true) { OddsCalculatorContent(state, onIntent = { sent += it }) }
    }

    private fun runOut(state: RunOutState) = screen.compose.setContent {
        PokerTheme(reducedMotion = true) { RunItOutContent(state, fourColour = false, onIntent = { sent += it }) }
    }

    /** Taps the node TalkBack reads as [description], scrolling it into view first if it's in a scrolling column. */
    private fun tap(description: String) {
        val node = screen.compose.onNodeWithContentDescription(description)
        if (node.fetchSemanticsNode().isInScroller()) node.performScrollTo()
        node.performClick()
    }

    private fun SemanticsNode.isInScroller(): Boolean =
        generateSequence(parent) { it.parent }.any { it.config.contains(SemanticsActions.ScrollBy) }

    @Test
    fun `the keypad sends rank then suit, and used cards can't be picked`() {
        odds(OddsFixtures.typing)
        tap("King")
        tap("Spades")
        tap("Delete card")
        screen.compose.onNodeWithContentDescription("Hearts, already out").assertIsNotEnabled()
        screen.compose.onNodeWithText("Done").performClick()
        assertEquals(
            listOf(
                OddsCalculatorIntent.PickRank(11),
                OddsCalculatorIntent.PickSuit(3),
                OddsCalculatorIntent.Backspace,
                OddsCalculatorIntent.CloseKeypad,
            ),
            sent,
        )
    }

    @Test
    fun `card slots read their seat, number and state, and aim the keypad`() {
        odds(OddsFixtures.typing)
        tap("Player 2, card 2, empty. Next")
        tap("Player 1, card 1, Ace of spades")
        tap("Flop card 1, empty")
        tap("Swap Player 1 and Player 2")
        tap("New hand")
        assertEquals(
            listOf(
                OddsCalculatorIntent.SelectSlot(SlotRef.Hole(1, 1)),
                OddsCalculatorIntent.SelectSlot(SlotRef.Hole(0, 0)),
                OddsCalculatorIntent.SelectSlot(SlotRef.Board(0)),
                OddsCalculatorIntent.Swap(0, 1),
                OddsCalculatorIntent.NewHand,
            ),
            sent,
        )
    }

    @Test
    fun `results show exact figures, the next-card grid and why`() {
        odds(OddsFixtures.flopExact)
        screen.compose.onNodeWithText("Nut flush draw + gutshot · win 56.06 · tie 0.00").assertExists()
        screen.compose.onNodeWithText("Overpair, queens · win 43.94 · tie 0.00").assertExists()
        screen.compose.onNodeWithText("16 of 45 put Player 1 ahead").assertExists()
        screen.compose.onNodeWithContentDescription("Queen of spades: Player 1 takes the lead").assertExists()
        screen.compose.onNodeWithContentDescription("Jack of spades: on the table").assertExists()
        screen.compose.onNodeWithContentDescription("7 of hearts: Player 1 stays behind").assertExists()
        val why = "Player 1 is behind now (ace-high against a pair of queens) but has 16 outs twice: " +
            "9 spades, 3 aces, 3 kings and the Q♣︎. That makes Player 1 the favourite by the river."
        screen.compose.onNode(hasText(why)).assertExists()
        screen.compose.onNodeWithText("Run it out").performScrollTo().performClick()
        assertEquals(listOf(OddsCalculatorIntent.RunItOut), sent)
    }

    @Test
    fun `a seat's menu folds, swaps and removes`() {
        odds(OddsFixtures.fourPlayersFold)
        tap("Player 3, options")
        screen.compose.onNodeWithText("Back in the hand").performClick()
        tap("Player 4, options")
        screen.compose.onNodeWithText("Swap with Player 1").performClick()
        tap("Player 4, options")
        screen.compose.onNodeWithText("Remove Player 4").performClick()
        assertEquals(
            listOf(
                OddsCalculatorIntent.Fold(2, folded = false),
                OddsCalculatorIntent.Swap(3, 0),
                OddsCalculatorIntent.RemovePlayer(3),
            ),
            sent,
        )
    }

    @Test
    fun `run it out deals, reruns and leaves`() {
        runOut(OddsFixtures.runOutTurn)
        screen.compose.onNodeWithText("Player 1 needs one of 16 rivers (16 of 44):").assertExists()
        screen.compose.onNodeWithContentDescription("down 19.7").assertExists()
        screen.compose.onNodeWithContentDescription("up 19.7").assertExists()
        screen.compose.onNodeWithText("Deal the river").performScrollTo().performClick()
        screen.compose.onNodeWithText("Run again from the flop").performScrollTo().performClick()
        screen.compose.onNodeWithText("Run it twice").performScrollTo().performClick()
        tap("Landscape view")
        tap("Back")
        assertEquals(
            listOf(
                OddsCalculatorIntent.DealNext,
                OddsCalculatorIntent.RunAgain,
                OddsCalculatorIntent.RunTwice,
                OddsCalculatorIntent.SetRunOutLandscape(true),
                OddsCalculatorIntent.ExitRunItOut,
            ),
            sent,
        )
    }

    @Test
    fun `when the river misses, the hand that was ahead holds`() {
        runOut(OddsFixtures.runOutRiverP2)
        screen.compose.onNodeWithText("River dealt · Player 2 holds").assertExists()
        screen.compose.onNodeWithText("Player 2 holds with a pair of queens").assertExists()
    }

    @Test
    fun `when the river hits, the drawing hand wins`() {
        runOut(OddsFixtures.runOutRiverP1)
        screen.compose.onNodeWithText("River dealt · Player 1 wins").assertExists()
        screen.compose.onNodeWithText("Player 1 wins with a flush").assertExists()
    }
}
