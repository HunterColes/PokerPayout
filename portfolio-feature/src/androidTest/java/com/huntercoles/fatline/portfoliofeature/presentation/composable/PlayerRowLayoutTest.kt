package com.huntercoles.fatline.portfoliofeature.presentation.composable

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huntercoles.fatline.core.design.AndroidStarterTheme
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerRowLayoutTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun playerRow_displaysStatusChipsInSingleRow() {
        val player = PlayerData(id = 1, name = "Player 1")

        composeTestRule.setContent {
            AndroidStarterTheme {
                PlayerRow(
                    player = player,
                    placementNumber = null,
                    isKnockingOut = false,
                    isChampionHighlight = false,
                    outEnabled = true,
                    rebuyEnabled = true,
                    addonEnabled = true,
                    onNameChange = {},
                    onActionRequested = {},
                    onAnimationComplete = {}
                )
            }
        }

        val chipDescriptions = listOf(
            "Still in",
            "Rebuy available",
            "Add-on available",
            "Buy-in pending",
            "Payout pending"
        )

        val nodes = chipDescriptions.map { description ->
            composeTestRule.onNodeWithContentDescription(description)
                .assertExists()
                .fetchSemanticsNode()
        }

        val topPositions = nodes.map { it.boundsInRoot.top }
        val bottomPositions = nodes.map { it.boundsInRoot.bottom }
        val leftPositions = nodes.map { it.boundsInRoot.left }

        val topSpread = (topPositions.maxOrNull() ?: 0f) - (topPositions.minOrNull() ?: 0f)
        val bottomSpread = (bottomPositions.maxOrNull() ?: 0f) - (bottomPositions.minOrNull() ?: 0f)

        // Allow a small tolerance (in pixels) for rendering differences
        val tolerance = 4f
        assertTrue("Status chips should share the same row; top spread=$topSpread", topSpread <= tolerance)
        assertTrue("Status chips should share the same row; bottom spread=$bottomSpread", bottomSpread <= tolerance)

        // Ensure chips are ordered from left to right
        val isStrictlyIncreasing = leftPositions.zipWithNext().all { (current, next) -> current < next }
        assertTrue("Status chips should be laid out horizontally from left to right", isStrictlyIncreasing)
    }

    @Test
    fun playerRow_showsPurchaseCountsWhenPresent() {
        val player = PlayerData(
            id = 7,
            name = "Sample Player",
            rebuys = 2,
            addons = 3
        )

        composeTestRule.setContent {
            AndroidStarterTheme {
                PlayerRow(
                    player = player,
                    placementNumber = null,
                    isKnockingOut = false,
                    isChampionHighlight = false,
                    outEnabled = true,
                    rebuyEnabled = true,
                    addonEnabled = true,
                    onNameChange = {},
                    onActionRequested = {},
                    onAnimationComplete = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Rebuy active (2)").assertExists()
        composeTestRule.onNodeWithContentDescription("Add-on active (3)").assertExists()
    }
}
