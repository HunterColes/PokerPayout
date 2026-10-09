package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tools.presentation.DealIntent
import com.huntercoles.pokerpayout.tools.presentation.DealUiState
import com.huntercoles.pokerpayout.tools.presentation.OutsIntent
import com.huntercoles.pokerpayout.tools.presentation.OutsUiState
import com.huntercoles.pokerpayout.tools.presentation.PrizeSource
import com.huntercoles.pokerpayout.tools.presentation.SidePotsIntent
import com.huntercoles.pokerpayout.tools.presentation.SidePotsUiState
import com.huntercoles.pokerpayout.tools.table.Street
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The table tools on a phone: the controls send the right intents, every control has a TalkBack name
 * that says whose it is ("Chips Dana put in", "Dana folded", "Remove Dana"), and each result reads
 * as one stop with its numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class TableToolsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val sidePots = mutableListOf<SidePotsIntent>()
    private val deal = mutableListOf<DealIntent>()
    private val outs = mutableListOf<OutsIntent>()

    private fun show(state: SidePotsUiState) = compose.setContent {
        PokerTheme(reducedMotion = true) { SidePotsContent(state, onIntent = { sidePots += it }, onBack = {}) }
    }

    private fun show(state: DealUiState) = compose.setContent {
        PokerTheme(reducedMotion = true) { DealContent(state, onIntent = { deal += it }, onBack = {}) }
    }

    private fun show(state: OutsUiState) = compose.setContent {
        PokerTheme(reducedMotion = true) { OutsContent(state, onIntent = { outs += it }, onBack = {}) }
    }

    @Test
    fun `side pots name every control by player, and send what is typed`() {
        show(TableToolFixtures.sidePots)
        compose.onNodeWithText("4 players · 2,450 in").assertExists()
        compose.onNodeWithContentDescription("Chips Dana put in").assert(hasText("300"))
        compose.onNodeWithContentDescription("Theo folded").assertIsOn()
        compose.onNodeWithContentDescription("Dana folded").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Remove Theo").performScrollTo().performClick()
        compose.onNodeWithText("Add player").performScrollTo().performClick()
        compose.onNodeWithContentDescription("New hand").performClick()
        assertEquals(
            listOf(
                SidePotsIntent.SetFolded(0, true),
                SidePotsIntent.RemovePlayer(3),
                SidePotsIntent.AddPlayer,
                SidePotsIntent.NewHand,
            ),
            sidePots,
        )
    }

    @Test
    fun `each pot says how much, who can win it, and which chips make it`() {
        show(TableToolFixtures.sidePots)
        compose.onNode(hasText("Main pot") and hasText("1,050") and hasText("Dana, Marcus or Priya can win it"))
            .performScrollTo()
            .assert(hasText("Up to 300 from each player"))
        compose.onNode(hasText("Side pot 1") and hasText("1,000") and hasText("Marcus or Priya can win it"))
            .performScrollTo()
            .assert(hasText("Over 300, up to 800 from each player"))
        compose.onNode(hasText("Back to Marcus") and hasText("400")).performScrollTo()
        compose.onNodeWithText("Adds up to 2,450, everything put in.").performScrollTo()
    }

    @Test
    fun `side pots before anything is in, blank players are Player N, and typing sends names and chips`() {
        show(TableToolFixtures.sidePotsEmpty)
        compose.onNodeWithText("Type what each player put in, and the pots show here.").performScrollTo()
        compose.onNodeWithContentDescription("New hand").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Player 1 name").performTextInput("Zed")
        compose.onNodeWithContentDescription("Chips Player 1 put in").performTextInput("150")
        compose.onNodeWithContentDescription("Chips Player 2 put in").performTextInput("1x")
        assertEquals(listOf(SidePotsIntent.SetName(0, "Zed"), SidePotsIntent.SetChips(0, 150)), sidePots)
    }

    @Test
    fun `everyone folded says nobody can win`() {
        show(TableToolFixtures.sidePotsAllFolded)
        compose.onNodeWithText("Everyone with chips in has folded, so nobody can win.", substring = true).performScrollTo()
    }

    @Test
    fun `the deal shows ICM and chip chop for each player, and the money they add up to`() {
        val state = TableToolFixtures.deal
        val split = checkNotNull(state.deal)
        show(state)
        compose.onNodeWithText("3 players · $850 to share").assertExists()
        compose.onNodeWithText("Still in, from the Bank").assertExists()
        listOf("Dana", "Marcus", "Priya").forEachIndexed { index, name ->
            compose.onNode(
                hasText(name) and hasText("ICM") and hasText(FormatUtils.formatCents(split.icm[index])) and
                    hasText(FormatUtils.formatCents(split.chipChop[index])),
            ).performScrollTo()
        }
        // Chip chop: $180 each, then the $310 left at 50 / 31.25 / 18.75 per cent.
        assertEquals(listOf(33_500L, 27_688L, 23_812L), split.chipChop)
        compose.onNodeWithText("Plus $50 to whoever wins.").performScrollTo()
        compose.onNodeWithText("Each way adds up to $850.").performScrollTo()
        compose.onNodeWithText("Chip chop: everyone gets the smallest prize left (\$180)", substring = true).performScrollTo()
    }

    @Test
    fun `the deal's controls name their player and send what is typed`() {
        show(TableToolFixtures.dealNoChips)
        compose.onNodeWithText("Type every player's chips to see the deal.").performScrollTo()
        compose.onNodeWithContentDescription("Chips Dana has").performTextInput("9000")
        compose.onNodeWithContentDescription("Remove Marcus").performClick()
        compose.onNodeWithText("Type them").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Start over").performClick()
        assertEquals(
            listOf(
                DealIntent.SetChips(0, 9_000),
                DealIntent.RemovePlayer(1),
                DealIntent.SetSource(PrizeSource.Typed),
                DealIntent.StartOver,
            ),
            deal,
        )
    }

    @Test
    fun `typed prizes that go up are named`() {
        show(TableToolFixtures.dealTyped)
        compose.onNodeWithText("Typed for this deal").assertExists()
        compose.onNodeWithContentDescription("4th prize").performScrollTo()
        compose.onNodeWithText("4th pays more than 3rd. Each place pays no more than the one above.").performScrollTo()
    }

    @Test
    fun `outs show the exact chances beside the rules of thumb, and the price of the call`() {
        show(TableToolFixtures.outsFlop)
        compose.onNodeWithText("9 outs on the flop").assertExists()
        compose.onNode(hasText("By the river") and hasText("35.0%") and hasText("Rule of 4: 36%") and hasText("1.9 to 1 against"))
            .performScrollTo()
        compose.onNode(hasText("Next card") and hasText("19.1%") and hasText("Rule of 2: 18%") and hasText("4.2 to 1 against"))
            .performScrollTo()
        compose.onNodeWithText("You need 25.0% to call").performScrollTo()
        compose.onNodeWithText("Pot odds 3 to 1").performScrollTo()
        compose.onNodeWithText("Next card 19.1% against 25.0% needed: too short for one card.").performScrollTo()
        compose.onNodeWithText("By the river 35.0%: enough if you're all in.").performScrollTo()
    }

    @Test
    fun `outs controls send the street, the count, a common draw and the pot`() {
        show(OutsUiState(street = Street.Turn, outs = 4))
        compose.onNode(hasText("On the river") and hasText("8.7%")).performScrollTo()
        compose.onNodeWithText("On the flop").performClick()
        compose.onNodeWithContentDescription("Increase Outs").performClick()
        compose.onNodeWithContentDescription("Flush + open-ended, 15 outs").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pot, with the bet").performScrollTo().performTextInput("250")
        compose.onNodeWithContentDescription("To call").performScrollTo().performTextInput("50")
        assertEquals(
            listOf(
                OutsIntent.SetStreet(Street.Flop),
                OutsIntent.SetOuts(5),
                OutsIntent.SetOuts(15),
                OutsIntent.SetPot(250),
                OutsIntent.SetCall(50),
            ),
            outs,
        )
    }
}
