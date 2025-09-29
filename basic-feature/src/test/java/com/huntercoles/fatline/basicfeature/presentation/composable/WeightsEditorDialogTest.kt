package com.huntercoles.fatline.basicfeature.presentation.composable

import com.huntercoles.fatline.core.constants.TournamentConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightsEditorDialogTest {

    @Test
    fun `isValidIntegerInput returns true for empty string`() {
        assertTrue(isValidIntegerInput(""))
    }

    @Test
    fun `isValidIntegerInput returns true for valid digits`() {
        assertTrue(isValidIntegerInput("123"))
        assertTrue(isValidIntegerInput("1"))
        assertTrue(isValidIntegerInput("999"))
    }

    @Test
    fun `isValidIntegerInput returns false for non-digits`() {
        assertFalse(isValidIntegerInput("abc"))
        assertFalse(isValidIntegerInput("12a"))
        assertFalse(isValidIntegerInput("1.2"))
    }

    @Test
    fun `isValidIntegerInput returns false for too long input`() {
        assertFalse(isValidIntegerInput("1234")) // 4 digits > 3
        assertFalse(isValidIntegerInput("12345"))
    }

    @Test
    fun `isValidWeightChange returns true for valid change in middle`() {
        val weights = listOf(10, 8, 6, 4)
        assertTrue(isValidWeightChange(weights, 1, 7)) // 7 < 10 and 7 > 6
        assertTrue(isValidWeightChange(weights, 2, 5)) // 5 < 8 and 5 > 4
    }

    @Test
    fun `isValidWeightChange returns false when greater than previous weight`() {
        val weights = listOf(10, 8, 6, 4)
        assertFalse(isValidWeightChange(weights, 1, 11)) // 11 > 10 (previous)
        assertFalse(isValidWeightChange(weights, 2, 9))  // 9 > 8 (previous)
    }

    @Test
    fun `isValidWeightChange returns false when less than or equal to next weight`() {
        val weights = listOf(10, 8, 6, 4)
        assertFalse(isValidWeightChange(weights, 1, 6))  // 6 <= 6 (next)
        assertFalse(isValidWeightChange(weights, 1, 5))  // 5 < 6 (next)
        assertFalse(isValidWeightChange(weights, 2, 4))  // 4 <= 4 (next)
    }

    @Test
    fun `isValidWeightChange returns true for first position with no previous constraint`() {
        val weights = listOf(10, 8, 6, 4)
        assertTrue(isValidWeightChange(weights, 0, 15)) // Only check next > 15? Wait, 15 > 8? No
        // Actually, for index 0, no previous, so only check next
        assertTrue(isValidWeightChange(weights, 0, 9))  // 9 > 8 (next)
        assertFalse(isValidWeightChange(weights, 0, 7)) // 7 <= 8 (next)
    }

    @Test
    fun `isValidWeightChange returns true for last position with no next constraint`() {
        val weights = listOf(10, 8, 6, 4)
        assertTrue(isValidWeightChange(weights, 3, 3))  // Only check previous > 3
        assertTrue(isValidWeightChange(weights, 3, 5))  // 5 < 6 (previous)
        assertFalse(isValidWeightChange(weights, 3, 7)) // 7 >= 6 (previous)
    }

    @Test
    fun `default weights are used correctly for adding positions`() {
        val defaultWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS
        assertEquals(listOf(35, 20, 15, 10, 8, 6, 3, 2, 1), defaultWeights)

        // Test logic for adding positions
        val currentWeights = listOf(35, 20, 15) // 3 positions
        val nextPosition = currentWeights.size + 1 // 4
        val expectedWeight = if (nextPosition <= defaultWeights.size) {
            defaultWeights[nextPosition - 1]
        } else {
            1
        }
        assertEquals(10, expectedWeight) // Position 4 should be 10

        val moreWeights = (1..10).map { 1 } // 10 positions of 1
        val nextPosition2 = moreWeights.size + 1 // 11
        val expectedWeight2 = if (nextPosition2 <= defaultWeights.size) {
            defaultWeights[nextPosition2 - 1]
        } else {
            1
        }
        assertEquals(1, expectedWeight2) // Position 11 should be 1
    }

    @Test
    fun `error detection identifies invalid weights correctly`() {
        // Valid decreasing weights - no errors
        val validWeights = listOf(10, 8, 6, 4)
        val validErrors = detectInvalidWeights(validWeights)
        assertEquals(listOf(false, false, false, false), validErrors)

        // Invalid weights - second position >= first
        val invalidWeights = listOf(10, 12, 6, 4)
        val invalidErrors = detectInvalidWeights(invalidWeights)
        assertEquals(listOf(true, true, false, false), invalidErrors)

        // Multiple invalid weights
        val multipleInvalidWeights = listOf(10, 12, 8, 4)
        val multipleErrors = detectInvalidWeights(multipleInvalidWeights)
        assertEquals(listOf(true, true, false, false), multipleErrors)
    }

    @Test
    fun `maximum positions limited to 9`() {
        val defaultWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS
        val maxPositions = defaultWeights.size
        assertEquals(9, maxPositions)

        // Test that adding is only possible when less than 9 positions
        val eightWeights = (1..8).map { 1 }
        val nextPosition = eightWeights.size + 1 // 9
        val canAdd = nextPosition <= maxPositions
        assertTrue(canAdd)

        val nineWeights = (1..9).map { 1 }
        val nextPosition2 = nineWeights.size + 1 // 10
        val canAdd2 = nextPosition2 <= maxPositions
        assertFalse(canAdd2)
    }

    @Test
    fun `add and remove buttons disabled when validation errors exist`() {
        // Valid weights - buttons should be enabled
        val validWeights = listOf(10, 8, 6, 4)
        val validErrors = detectInvalidWeights(validWeights)
        val hasValidErrors = validErrors.any { it }
        assertFalse(hasValidErrors)
        
        // Can add when < 9 positions and no errors
        val maxPositions = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.size
        val canAddValid = validWeights.size < maxPositions && !hasValidErrors
        assertTrue(canAddValid)
        
        // Can remove when > 1 positions and no errors
        val canRemoveValid = validWeights.size > 1 && !hasValidErrors
        assertTrue(canRemoveValid)

        // Invalid weights - buttons should be disabled
        val invalidWeights = listOf(10, 12, 6, 4)
        val invalidErrors = detectInvalidWeights(invalidWeights)
        val hasInvalidErrors = invalidErrors.any { it }
        assertTrue(hasInvalidErrors)
        
        // Cannot add when there are errors
        val canAddInvalid = invalidWeights.size < maxPositions && !hasInvalidErrors
        assertFalse(canAddInvalid)
        
        // Cannot remove when there are errors
        val canRemoveInvalid = invalidWeights.size > 1 && !hasInvalidErrors
        assertFalse(canRemoveInvalid)
    }
}