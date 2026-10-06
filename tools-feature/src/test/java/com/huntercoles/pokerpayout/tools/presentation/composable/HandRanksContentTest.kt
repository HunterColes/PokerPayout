package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.tools.poker.HandRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hand ranks (S12): the frequency column, kickers for TalkBack, and the back arrow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class HandRanksContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each hand shows how often it comes up by the river`() {
        compose.setContent { PokerTheme(reducedMotion = true) { HandRanksContent(fourColour = false, onBack = {}) } }
        val shown = listOf("0.003%", "1 in 30,940", "0.03%", "1 in 3,591", "0.17%", "1 in 595", "2.6%", "1 in 39") +
            listOf("43.8%", "1 in 2.3", "17.4%", "1 in 5.7")
        shown.forEach { compose.onNodeWithText(it).performScrollTo() }
        compose.onNodeWithText("133,784,560", substring = true).performScrollTo()
    }

    @Test
    fun `the shares and 1-in figures round as the spec says`() {
        assertEquals(
            listOf("0.003", "0.03", "0.17", "2.6", "3.0", "4.6", "4.8", "23.5", "43.8", "17.4"),
            HandRank.entries.map(::handShare)
        )
        assertEquals(
            listOf("30,940", "3,591", "595", "39", "33", "22", "21", "4.3", "2.3", "5.7"),
            HandRank.entries.map(::oneInText)
        )
    }

    @Test
    fun `kickers are named as kickers, and back works`() {
        var back = 0
        compose.setContent { PokerTheme(reducedMotion = true) { HandRanksContent(fourColour = true, onBack = { back++ }) } }
        compose.onNodeWithContentDescription("7 of clubs, kicker").performScrollTo()
        // Kickers: four of a kind 1, three of a kind 2, two pair 1, one pair 3, high card 4
        compose.onAllNodesWithContentDescription(", kicker", substring = true, useUnmergedTree = true).assertCountEquals(11)
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back == 1)
    }
}
