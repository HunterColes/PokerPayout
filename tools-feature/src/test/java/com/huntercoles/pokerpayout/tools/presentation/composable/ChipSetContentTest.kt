package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.tools.presentation.ChipSetIntent
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import com.huntercoles.pokerpayout.tools.presentation.ColourEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chip set's controls (S11) send the right intents, and the screen says what the plan says:
 * the stack sums, the shortfall, the color-up plan, TalkBack names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class ChipSetContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val sent = mutableListOf<ChipSetIntent>()

    private fun show(state: ChipSetUiState, settingsOpen: Boolean = false) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ChipSetContent(state, onIntent = { sent += it }, onBack = {}, settingsOpen = settingsOpen)
            }
        }
    }

    @Test
    fun `steppers move counts in fives, rows open the colour sheet, reset and add a colour`() {
        show(ChipSetFixtures.ok)
        compose.onNodeWithContentDescription("Increase Green 25 chips, by 5").performClick()
        compose.onNodeWithContentDescription("Decrease Black 100 chips, by 5").performClick()
        compose.onNodeWithText("Purple 500").performClick()
        compose.onNodeWithContentDescription("Reset chip set").performClick()
        compose.onNodeWithText("Add a colour").performScrollTo().performClick()
        assertEquals(
            listOf(
                ChipSetIntent.SetCount(ChipColour.Green, 155),
                ChipSetIntent.SetCount(ChipColour.Black, 145),
                ChipSetIntent.EditColour(ChipColour.Purple),
                ChipSetIntent.Reset,
                ChipSetIntent.AddColour,
            ),
            sent
        )
    }

    @Test
    fun `counts step to the next multiple of five`() {
        val stepped = listOf(152 to 153, 152 to 151, 0 to 1, 4 to 3, 9_998 to 9_999).map { (from, to) -> steppedCount(from, to) }
        assertEquals(listOf(155, 150, 5, 0, 9_999), stepped)
    }

    @Test
    fun `the stack picture adds up and the reserve and color-up plan are shown`() {
        show(ChipSetFixtures.ok)
        // 12 × 25 + 12 × 100 + 5 × 500 + 1 × 1K = 5,000 in 30 chips
        listOf("12 × 25", "300", "12 × 100", "1,200", "5 × 500", "2,500", "1 × 1K", "30 chips a stack · 4 colours · 5,000")
            .forEach { compose.onNodeWithText(it).performScrollTo() }
        compose.onNodeWithText("Green and black run out first", substring = true).performScrollTo()
        compose.onNodeWithText("Break 1, after Level 4", substring = true, useUnmergedTree = true).performScrollTo()
        compose.onNodeWithContentDescription("Break 1, after Level 4: green 25s into black 100s.", substring = true)
            .performScrollTo()
    }

    @Test
    fun `a short set says which chips and how many`() {
        show(ChipSetFixtures.short)
        compose.onNodeWithText("Short 6 black 100s for 9 players.").performScrollTo()
        compose.onNodeWithText("Your chips make 7 full stacks of 5,000 now.").performScrollTo()
        compose.onNodeWithText("+6 more").performScrollTo()
    }

    @Test
    fun `stack settings unfold and send their intents`() {
        show(ChipSetFixtures.ok)
        compose.onNodeWithText("Stack settings", ignoreCase = true).performScrollTo().performClick()
        compose.onNodeWithText("Mostly middle chips").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Increase Keep back for rebuys and add-ons").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Decrease Colours per stack, at most").performScrollTo().performClick()
        assertEquals(
            listOf(
                ChipSetIntent.SetShape(ChipDistributionCurve.BellCurve),
                ChipSetIntent.SetReserve(1),
                ChipSetIntent.SetMaxColours(4),
            ),
            sent
        )
        compose.onNodeWithText("More small chips").assertIsSelected()
    }

    @Test
    fun `stacks kept back say they come from the Tournament, and step from its estimate`() {
        show(ChipSetFixtures.fromTournament, settingsOpen = true)
        compose.onNodeWithText("Keep back 14", substring = true).performScrollTo()
        compose.onNodeWithText("From Tournament setup: 5 for rebuys (about half the players) and 9 for add-ons (one each).")
            .performScrollTo()
        compose.onNodeWithText("Use Tournament's estimate").assertDoesNotExist()
        compose.onNodeWithContentDescription("Increase Keep back for rebuys and add-ons").performScrollTo().performClick()
        assertEquals(listOf<ChipSetIntent>(ChipSetIntent.SetReserve(15)), sent)
    }

    @Test
    fun `a number of your own says what the Tournament suggests, and goes back to it`() {
        val own = ChipSetFixtures.state(ChipSetFixtures.ok.inventory, reserve = 3, estimate = ChipSetFixtures.nightEstimate)
        show(own, settingsOpen = true)
        compose.onNodeWithText("Your own · Tournament setup suggests 14").performScrollTo()
        compose.onNodeWithText("Use Tournament's estimate").performScrollTo().performClick()
        assertEquals(listOf<ChipSetIntent>(ChipSetIntent.SetReserve(null)), sent)
    }

    @Test
    fun `a game with no rebuys or add-ons says so`() {
        show(ChipSetFixtures.ok, settingsOpen = true)
        compose.onNodeWithText("From Tournament setup: no rebuys or add-ons.").performScrollTo()
    }

    @Test
    fun `the colour sheet refuses a value another colour has, and saves or removes`() {
        val state = ChipSetFixtures.ok
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ColourEditorContent(state, ColourEditor(ChipColour.Green), onIntent = { sent += it })
            }
        }
        compose.onNodeWithText("25").performTextReplacement("100")
        compose.onNodeWithText("Black is already worth 100.").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("100").performTextReplacement("50")
        compose.onNodeWithText("150").performTextReplacement("120")
        compose.onNodeWithContentDescription("Orange").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Remove Green 25").performScrollTo().performClick()
        assertEquals(
            listOf(
                ChipSetIntent.SaveColour(InventoryChip(ChipColour.Orange, 50, 120), replacing = ChipColour.Green),
                ChipSetIntent.RemoveColour(ChipColour.Green),
            ),
            sent
        )
    }
}
