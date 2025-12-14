package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.pow

/**
 * Core tests for BlindFittingAlgorithm focusing on fit score quality and validation.
 * 
 * Fit Score: Measures how well blinds fit exponential curve. 1.0 = perfect, lower = more deviation.
 * Formula: Score = 1 - RMSE (root mean square error in log space)
 * 
 * Calculated Growth Rate: r = (startingChips / smallestChip)^(1 / (numRounds - 1))
 * This is the required exponential growth rate, not a preset target.
 * Must be between MIN (1.3) and MAX (2.0) or configuration is invalid.
 */
class BlindFittingAlgorithmTest {

    @Test
    fun `perfect fit score 1_0 with standard 1 hour game`() {
        // 1hr game, 10min rounds = 6 rounds
        // Perfect doubling: 50 × 2^5 = 1600
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 6,
            smallestChip = 50,
            startingChips = 1600
        )

        assertEquals(2.0, result.calculatedGrowthRate, 0.001)
        assertEquals(listOf(50, 100, 200, 400, 800, 1600), result.blinds)
        assertEquals(1.0, result.fitScore, 0.001, "Perfect exponential curve should have fit score = 1.0")
    }

    @Test
    fun `perfect fit score 1_0 with quick tournament`() {
        // 1hr game, 15min rounds = 4 rounds
        // Perfect doubling: 50 × 2^3 = 400
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 4,
            smallestChip = 50,
            startingChips = 400
        )

        assertEquals(2.0, result.calculatedGrowthRate, 0.001)
        assertEquals(listOf(50, 100, 200, 400), result.blinds)
        assertEquals(1.0, result.fitScore, 0.001, "Perfect exponential curve should have fit score = 1.0")
    }

    @Test
    fun `perfect fit score 1_0 with small stakes game`() {
        // 1.5hr game, 10min rounds = 9 rounds
        // Perfect doubling: 10 × 2^8 = 2560
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 9,
            smallestChip = 10,
            startingChips = 2560
        )

        assertEquals(2.0, result.calculatedGrowthRate, 0.001)
        assertEquals(listOf(10, 20, 40, 80, 160, 320, 640, 1280, 2560), result.blinds)
        assertEquals(1.0, result.fitScore, 0.001, "Perfect exponential curve should have fit score = 1.0")
    }

    @Test
    fun `perfect fit score 1_0 with medium stakes game`() {
        // 1.25hr game, 15min rounds = 5 rounds
        // Perfect doubling: 25 × 2^4 = 400
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 5,
            smallestChip = 25,
            startingChips = 400
        )

        assertEquals(2.0, result.calculatedGrowthRate, 0.001)
        assertEquals(listOf(25, 50, 100, 200, 400), result.blinds)
        assertEquals(1.0, result.fitScore, 0.001, "Perfect exponential curve should have fit score = 1.0")
    }

    @Test
    fun `default tournament configuration achieves excellent fit`() {
        // 3h duration, 20min rounds = 9 rounds, 50→5000
        // Target rate ≈ 1.778 (between 1.3-2.0 bounds)
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 9,
            smallestChip = 50,
            startingChips = 5000
        )

        assertTrue(result.calculatedGrowthRate in 1.3..2.0)
        assertEquals(50, result.blinds.first())
        assertEquals(5000, result.blinds.last())
        assertEquals(9, result.blinds.size)
        assertTrue(result.fitScore > 0.9, "Default config should have fit score > 0.9, got ${result.fitScore}")
        assertTrue(result.blinds.all { it % 50 == 0 })
    }

    @Test
    fun `minimum growth rate boundary at 1_3`() {
        // Target rate ~1.3 with many rounds
        // Tests lower bound of acceptable growth rate
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 15,
            smallestChip = 25,
            startingChips = 1000
        )

        assertTrue(result.calculatedGrowthRate >= 1.3)
        assertTrue(result.fitScore > 0.65, "Boundary case should still have reasonable fit, got ${result.fitScore}")
    }

    @Test
    fun `maximum growth rate boundary at 2_0`() {
        // Target rate exactly 2.0 with 8 rounds
        // Tests upper bound of acceptable growth rate
        val startChip = 25
        val targetRate = 2.0
        val numRounds = 8
        val endChip = (startChip * targetRate.pow(numRounds - 1)).toInt()

        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = numRounds,
            smallestChip = startChip,
            startingChips = endChip
        )

        assertEquals(2.0, result.calculatedGrowthRate, 0.01)
        assertTrue(result.fitScore > 0.9, "Doubling growth should have excellent fit, got ${result.fitScore}")
    }

    @Test
    fun `too few rounds exceeds maximum growth rate`() {
        // 3 rounds from 25→5000: target rate ≈ 14.14 >> 2.0
        var exceptionThrown = false
        try {
            BlindFittingAlgorithm.fitBlinds(
                numRounds = 3,
                smallestChip = 25,
                startingChips = 5000
            )
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("growth rate") == true)
        }
        assertTrue(exceptionThrown, "Should reject excessive growth rate")
    }

    @Test
    fun `too many rounds falls below minimum growth rate`() {
        // 50 rounds from 25→5000: target rate ≈ 1.113 < 1.3
        var exceptionThrown = false
        try {
            BlindFittingAlgorithm.fitBlinds(
                numRounds = 50,
                smallestChip = 25,
                startingChips = 5000
            )
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("growth rate") == true)
        }
        assertTrue(exceptionThrown, "Should reject insufficient growth rate")
    }

    @Test
    fun `blinds grow monotonically`() {
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 12,
            smallestChip = 25,
            startingChips = 5000
        )

        for (i in 1 until result.blinds.size) {
            assertTrue(
                result.blinds[i] > result.blinds[i - 1],
                "Blind ${result.blinds[i]} at index $i should be > ${result.blinds[i - 1]}"
            )
        }
    }

    @Test
    fun `consecutive growth rates within bounds`() {
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 12,
            smallestChip = 25,
            startingChips = 5000
        )

        for (i in 1 until result.blinds.size) {
            val growthRate = result.blinds[i].toDouble() / result.blinds[i - 1]
            assertTrue(
                growthRate >= BlindStructureConstants.MIN_BLIND_GROWTH_RATE,
                "Growth rate $growthRate at level $i should be >= ${BlindStructureConstants.MIN_BLIND_GROWTH_RATE}"
            )
            assertTrue(
                growthRate <= BlindStructureConstants.MAX_BLIND_GROWTH_RATE,
                "Growth rate $growthRate at level $i should be <= ${BlindStructureConstants.MAX_BLIND_GROWTH_RATE}"
            )
        }
    }

    @Test
    fun `smooth numbers preferred above threshold`() {
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 15,
            smallestChip = 25,
            startingChips = 5000
        )

        val valuesAboveThreshold = result.blinds.filter { it > BlindStructureConstants.SMOOTH_NUMBER_THRESHOLD }
        val smoothNumbers = valuesAboveThreshold.filter { it % 10 == 0 }
        val smoothPercentage = if (valuesAboveThreshold.isNotEmpty()) {
            (smoothNumbers.size.toDouble() / valuesAboveThreshold.size) * 100
        } else 100.0
        
        assertTrue(
            smoothPercentage >= 60.0,
            "At least 60% of blinds > ${BlindStructureConstants.SMOOTH_NUMBER_THRESHOLD} should end in 0, got ${smoothPercentage.toInt()}%"
        )
    }
}
