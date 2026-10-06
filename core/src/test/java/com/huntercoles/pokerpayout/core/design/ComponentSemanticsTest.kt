package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardSlot
import com.huntercoles.pokerpayout.core.design.components.CardSlotState
import com.huntercoles.pokerpayout.core.design.components.EquityBar
import com.huntercoles.pokerpayout.core.design.components.KnockoutBadge
import com.huntercoles.pokerpayout.core.design.components.PayoutPreview
import com.huntercoles.pokerpayout.core.design.components.PayoutStructureContent
import com.huntercoles.pokerpayout.core.design.components.PlayingCard
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.PokerNavBar
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.ToggleChip
import com.huntercoles.pokerpayout.core.design.components.UndoSnackbar
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What TalkBack hears and what taps do, component by component (design spec section 6). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentSemanticsTest {
    @get:Rule
    val screen = ScreenTestRule(ScreenConfig(Device.Phone, 1.0f))
    private val rule get() = screen.compose

    @Test
    fun cardsReadTheirNameAndShowTenAsTen() {
        show {
            CardFace(PlayingCard("T", "h"))
            CardFace(PlayingCard("A", "s"), fourColour = true)
        }
        rule.onNodeWithContentDescription("10 of hearts").assertExists()
        rule.onNodeWithContentDescription("Ace of spades").assertExists()
        rule.onNodeWithText("T", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun cardSlotsSayWhatTheyAre() {
        show {
            CardSlot(CardSlotState.Next)
            CardSlot(CardSlotState.Random)
            CardSlot(CardSlotState.Empty, contentDescription = "Player 2, card 2, empty")
        }
        rule.onNodeWithContentDescription("Next card").assertExists()
        rule.onNodeWithContentDescription("Random card").assertExists()
        rule.onNodeWithContentDescription("Player 2, card 2, empty").assertExists()
    }

    @Test
    fun toggleChipIsACheckboxAndLockedMeansDisabled() {
        var buyIn by mutableStateOf(false)
        var rebuyTaps = 0
        show {
            ToggleChip("Buy-in", checked = buyIn, onCheckedChange = { buyIn = it })
            ToggleChip("Rebuy", checked = true, onCheckedChange = { rebuyTaps++ }, locked = true, count = 1)
        }
        val buyInChip = rule.onNodeWithContentDescription("Buy-in")
        buyInChip.assert(hasRole(Role.Checkbox)).assert(hasToggleState(ToggleableState.Off))
        buyInChip.performClick()
        assertTrue(buyIn)
        buyInChip.assert(hasToggleState(ToggleableState.On))

        val rebuy = rule.onNodeWithContentDescription("Rebuy, 1 taken, Locked")
        rebuy.assertIsNotEnabled().assert(hasToggleState(ToggleableState.On))
        rebuy.performClick()
        assertEquals(0, rebuyTaps)
    }

    /** A place weighted at or above the one before it is an error TalkBack reads, not just a red amount (PP-024). */
    @Test
    fun weightsOutOfOrderAreErrorsNotJustRed() {
        show {
            PayoutStructureContent(
                current = PayoutSettings(weights = listOf(10, 20, 5), preset = null, rounding = PayoutRounding.ONE_DOLLAR),
                preview = PayoutPreview(prizePoolCents = 35_000L, playerCount = 9),
                onSave = {},
                onDismiss = {},
            )
        }
        // 1st (10) is under 2nd (20), so both are out of order; 3rd (5) is fine.
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error), useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun segmentsAreRadioButtons() {
        var choice by mutableStateOf("$5")
        show {
            PokerSegmentedControl(listOf("$1", "$5", "$10"), choice, onSelect = { choice = it }, label = { it })
        }
        rule.onNodeWithText("$5").assertIsSelected()
        rule.onNodeWithText("$10").assert(hasRole(Role.RadioButton)).performClick()
        assertEquals("$10", choice)
        rule.onNodeWithText("$10").assertIsSelected()
    }

    @Test
    fun navBarHasFourTabsAndTheFirstLabelIsAParameter() {
        var selected by mutableIntStateOf(0)
        show {
            PokerNavBar(pokerNavItems(), selectedIndex = selected, onSelect = { selected = it })
        }
        listOf("Tournament", "Bank", "Payouts", "Tools").forEach { rule.onNodeWithText(it).assert(hasRole(Role.Tab)) }
        rule.onNodeWithText("Tournament").assertIsSelected()
        rule.onNodeWithText("Tools").performClick()
        assertEquals(3, selected)
    }

    @Test
    fun navBarFirstTabCanBeRenamed() {
        show {
            PokerNavBar(pokerNavItems(firstTabLabel = "Clock"), selectedIndex = 0, onSelect = {})
        }
        rule.onNodeWithText("Clock").assertIsSelected()
        rule.onNodeWithText("Tournament").assertDoesNotExist()
    }

    @Test
    fun stepperStopsAtItsEndsAndRepeatsWhileHeld() {
        var players by mutableIntStateOf(2)
        show {
            PokerStepper(players, onValueChange = { players = it }, range = 2..30, label = "Players")
        }
        rule.onNodeWithContentDescription("Decrease Players").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Increase Players").assertIsEnabled().performClick()
        assertEquals(3, players)

        // Hold for 1.5 s of virtual time: a step on the long press, then one per repeat interval.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Increase Players").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_MS)
        rule.onNodeWithContentDescription("Increase Players").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("held for 1.5 s: $players", players > 10)
        val afterRelease = players
        rule.mainClock.advanceTimeBy(HOLD_MS)
        assertEquals("stops when released", afterRelease, players)
    }

    @Test
    fun topBarTitleIsAHeadingAndBackIsLabelled() {
        var backs = 0
        show {
            PokerTopBar(title = "Odds", subtitle = "Flop · exact", onBack = { backs++ })
        }
        rule.onNodeWithText("Odds").assert(isHeading())
        rule.onNodeWithContentDescription("Back").assert(hasRole(Role.Button)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun badgesPillsAndBarsReadAsWords() {
        show {
            PokerPill("Final minute")
            KnockoutBadge(2)
            KnockoutBadge(1)
            EquityBar(win = 0.53f, tie = 0.014f)
            EquityBar(win = 0.671f, tie = 0f, estimate = true)
            PokerChip(25)
        }
        rule.onNodeWithText("FINAL MINUTE").assertExists()
        rule.onNodeWithContentDescription("2 knockouts").assertExists()
        rule.onNodeWithContentDescription("1 knockout").assertExists()
        rule.onNodeWithContentDescription("Win 53.0%, tie 1.4%").assertExists()
        rule.onNodeWithContentDescription("Win about 67.1%, tie about 0.0%, still estimating").assertExists()
        rule.onNodeWithContentDescription("Green 25 chip").assertExists()
    }

    @Test
    fun fieldErrorIsAnnounced() {
        show {
            PokerField("5,010", {}, label = "Starting stack", supportingText = "Try 5,000.", isError = true)
        }
        val field = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        field.assertTextEquals("5,010")
        assertEquals("Try 5,000.", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error))
    }

    @Test
    fun undoSnackbarActionRuns() {
        var undone = false
        show {
            UndoSnackbar(message = "Rita is out in 8th", onUndo = { undone = true })
        }
        rule.onNodeWithText("UNDO").performClick()
        assertTrue(undone)
    }

    /** Renders [content] in the theme, one component under the other, with motion off. */
    private fun show(content: @Composable ColumnScope.() -> Unit) {
        rule.setContent { PokerTheme(reducedMotion = true) { Column(content = content) } }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasToggleState(state: ToggleableState) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    private companion object {
        const val HOLD_MS = 1_500L
    }
}
