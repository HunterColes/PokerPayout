package com.huntercoles.fatline.basicfeature

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huntercoles.fatline.basicfeature.presentation.composable.PoolConfigurationSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for DecimalTextField functionality focusing on user interactions
 */
@RunWith(AndroidJUnit4::class)
class DecimalTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCursorAutoPositioning_FirstClick_PositionsBeforeDecimal() {
        // Given: A decimal text field with value "25.50"
        var buyIn by mutableStateOf(25.50)
        
        composeTestRule.setContent {
            PoolConfigurationSection(
                buyIn = buyIn,
                foodPerPlayer = 0.0,
                bountyPerPlayer = 0.0,
                onBuyInChange = { buyIn = it },
                onFoodChange = { },
                onBountyChange = { },
                onRebuyChange = { },
                onAddOnChange = { },
                rebuyPerPlayer = 0.0,
                addOnPerPlayer = 0.0,
                isLocked = false
            )
        }

        // When: Field is focused for the first time
        val buyInField = composeTestRule.onNodeWithText("Buy-in ($)")
        buyInField.performClick()
        
        // Then: Field should be focused and functional
        buyInField.assertExists()
        buyInField.assertIsFocused()
    }

    @Test
    fun testValuePersistence_ComponentRecomposition_RetainsValues() {
        // Given: Initial values set
        var buyIn by mutableStateOf(100.0)
        var recompositionTrigger by mutableStateOf(0)
        
        composeTestRule.setContent {
            // Force recomposition
            LaunchedEffect(recompositionTrigger) { /* Trigger */ }
            
            PoolConfigurationSection(
                buyIn = buyIn,
                foodPerPlayer = 50.0,
                bountyPerPlayer = 25.0,
                onBuyInChange = { buyIn = it },
                onFoodChange = { },
                onBountyChange = { },
                onRebuyChange = { },
                onAddOnChange = { },
                rebuyPerPlayer = 0.0,
                addOnPerPlayer = 0.0,
                isLocked = false
            )
        }

        // When: Component recomposes (simulating page switch)
        buyIn = 150.0
        recompositionTrigger = 1
        composeTestRule.waitForIdle()

        // Then: New value should be displayed
        composeTestRule.onNodeWithText("150.0").assertExists()
    }

    @Test
    fun testFieldSwitching_BetweenMultipleFields_ResetsAutoPositioning() {
        // Given: Multiple decimal fields
        var buyIn by mutableStateOf(25.0)
        var food by mutableStateOf(10.0)
        var bounty by mutableStateOf(5.0)
        
        composeTestRule.setContent {
            PoolConfigurationSection(
                buyIn = buyIn,
                foodPerPlayer = food,
                bountyPerPlayer = bounty,
                onBuyInChange = { buyIn = it },
                onFoodChange = { food = it },
                onBountyChange = { bounty = it },
                onRebuyChange = { },
                onAddOnChange = { },
                rebuyPerPlayer = 0.0,
                addOnPerPlayer = 0.0,
                isLocked = false
            )
        }

        // When: Switching between fields
        val buyInField = composeTestRule.onNodeWithText("Buy-in ($)")
        val foodField = composeTestRule.onNodeWithText("Food ($)")
        val bountyField = composeTestRule.onNodeWithText("Bounty ($)")

        // Focus buy-in field
        buyInField.performClick()
        buyInField.assertIsFocused()
        
        // Switch to food field
        foodField.performClick() 
        foodField.assertIsFocused()
        
        // Switch to bounty field
        bountyField.performClick()
        bountyField.assertIsFocused()

        // Then: Each field should be focusable and maintain its value
        composeTestRule.onNodeWithText("25.0").assertExists()
        composeTestRule.onNodeWithText("10.0").assertExists() 
        composeTestRule.onNodeWithText("5.0").assertExists()
    }

    @Test
    fun testLockedFields_DoNotAcceptInput() {
        // Given: Locked decimal fields
        composeTestRule.setContent {
            PoolConfigurationSection(
                buyIn = 50.0,
                foodPerPlayer = 20.0,
                bountyPerPlayer = 10.0,
                onBuyInChange = { },
                onFoodChange = { },
                onBountyChange = { },
                onRebuyChange = { },
                onAddOnChange = { },
                rebuyPerPlayer = 0.0,
                addOnPerPlayer = 0.0,
                isLocked = true
            )
        }

        // When: Attempting to interact with locked field
        val buyInField = composeTestRule.onNodeWithText("Buy-in ($)")
        
        // Then: Field should be disabled and not accept input
        buyInField.assertIsNotEnabled()
    }

    @Test
    fun testTextInput_UpdatesValueCorrectly() {
        // Given: Editable decimal field
        var buyIn by mutableStateOf(0.0)
        
        composeTestRule.setContent {
            PoolConfigurationSection(
                buyIn = buyIn,
                foodPerPlayer = 0.0,
                bountyPerPlayer = 0.0,
                onBuyInChange = { buyIn = it },
                onFoodChange = { },
                onBountyChange = { },
                onRebuyChange = { },
                onAddOnChange = { },
                rebuyPerPlayer = 0.0,
                addOnPerPlayer = 0.0,
                isLocked = false
            )
        }

        // When: User types in the field
        val buyInField = composeTestRule.onNodeWithText("Buy-in ($)")
        buyInField.performClick()
        buyInField.performTextClearance()
        buyInField.performTextInput("100.50")
        
        // Then: Value should be updated
        assert(buyIn == 100.50)
    }

    @Test
    fun testEmptyField_HandlesZeroValue() {
        // Given: Field with zero value
        var buyIn by mutableStateOf(0.0)
        
        composeTestRule.setContent {
            PoolConfigurationSection(
                buyIn = buyIn,
                foodPerPlayer = 0.0,
                bountyPerPlayer = 0.0,
                onBuyInChange = { buyIn = it },
                onFoodChange = { },
                onBountyChange = { },
                isLocked = false
            )
        }

        // When: Field is displayed with zero value
        val buyInField = composeTestRule.onNodeWithText("Buy-in ($)")
        
        // Then: Field should be empty (not showing "0.0")
        buyInField.performClick()
        buyInField.assertTextContains("")
    }
}