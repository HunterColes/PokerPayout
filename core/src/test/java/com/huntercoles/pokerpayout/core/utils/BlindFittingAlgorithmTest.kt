package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `default tournament configuration fits within the documented bounds`() {
        // 3h duration, 20min rounds = 9 rounds, 50 -> 5000: average step 100^(1/8) = 1.778
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 9,
            smallestChip = 50,
            startingChips = 5000
        )

        assertEquals(1.7783, result.calculatedGrowthRate, 0.0001)
        assertEquals(9, result.blinds.size)
        assertEquals(50, result.blinds.first())
        assertEquals(5000, result.blinds.last())
        assertTrue(result.blinds.all { it % 50 == 0 }, "multiples of 50: ${result.blinds}")
        val steps = result.blinds.zipWithNext { a, b -> b.toDouble() / a }
        assertTrue(
            steps.all { it in BlindStructureConstants.MIN_BLIND_GROWTH_RATE..BlindStructureConstants.MAX_BLIND_GROWTH_RATE },
            "steps $steps"
        )
        assertTrue(result.fitScore > 0.9, "Default config should have fit score > 0.9, got ${result.fitScore}")
    }

    @Test
    fun `calculated growth rate is the average step from smallest chip to starting stack`() {
        // (2500 / 25)^(1/10) = 100^(1/10)
        assertEquals(1.5849, BlindFittingAlgorithm.fitBlinds(11, 25, 2500).calculatedGrowthRate, 0.0001)
        // (6400 / 100)^(1/6) = 2
        assertEquals(2.0, BlindFittingAlgorithm.fitBlinds(7, 100, 6400).calculatedGrowthRate, 1e-9)
    }

    @Test
    fun `maximum growth rate of 2_0 gives an exact doubling ladder`() {
        // 25 x 2^7 = 3200 over 8 rounds
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = 8,
            smallestChip = 25,
            startingChips = 3200
        )

        assertEquals(2.0, result.calculatedGrowthRate, 1e-9)
        assertEquals(listOf(25, 50, 100, 200, 400, 800, 1600, 3200), result.blinds)
        assertEquals(1.0, result.fitScore, 1e-9)
    }

    @Test
    fun `too few rounds exceeds maximum growth rate`() {
        // 3 rounds from 25 -> 5000: average step 200^(1/2) = 14.1
        val error = assertFailsWith<IllegalArgumentException> {
            BlindFittingAlgorithm.fitBlinds(numRounds = 3, smallestChip = 25, startingChips = 5000)
        }
        assertTrue("growth rate 14.142 is too high" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `too many rounds falls below minimum growth rate`() {
        // 50 rounds from 25 -> 5000: average step 200^(1/49) = 1.114
        val error = assertFailsWith<IllegalArgumentException> {
            BlindFittingAlgorithm.fitBlinds(numRounds = 50, smallestChip = 25, startingChips = 5000)
        }
        assertTrue("growth rate 1.114 is too low" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `invalid inputs are rejected`() {
        assertFailsWith<IllegalArgumentException> { BlindFittingAlgorithm.fitBlinds(1, 25, 5000) }
        assertFailsWith<IllegalArgumentException> { BlindFittingAlgorithm.fitBlinds(9, 0, 5000) }
        assertFailsWith<IllegalArgumentException> { BlindFittingAlgorithm.fitBlinds(9, 100, 50) }
    }

    @Test
    fun `levels strictly increase, or the configuration is rejected`() {
        val result = runCatching {
            BlindFittingAlgorithm.fitBlinds(numRounds = 5, smallestChip = 25, startingChips = 100)
        }

        result.onSuccess { fit ->
            assertTrue(fit.blinds.zipWithNext().all { (a, b) -> b > a }, "repeated level in ${fit.blinds}")
        }
    }

    @Test
    fun `every level is a multiple of the smallest chip, or the configuration is rejected`() {
        val result = runCatching {
            BlindFittingAlgorithm.fitBlinds(numRounds = 9, smallestChip = 25, startingChips = 1010)
        }

        result.onSuccess { fit ->
            assertTrue(fit.blinds.all { it % 25 == 0 }, "non-multiple of 25 in ${fit.blinds}")
        }
    }

    @Test
    fun `an average rate in band is still rejected when whole chips can't climb that slowly`() {
        // 25 -> 1000 in 15 levels averages 1.30x, but from 25 the only in-band steps are 50, then
        // 75 or 100, ... (each at least ceil(1.3 x previous) chips): the slowest ladder passes 1000
        // by level 12. Before PP-020 this was accepted as 25, 50, 75, 100, 125 ... (1.25x steps).
        listOf(Triple(15, 25, 1000), Triple(5, 25, 100)).forEach { (rounds, chip, stack) ->
            val error = assertFailsWith<BlindLadderException> { BlindFittingAlgorithm.fitBlinds(rounds, chip, stack) }
            assertEquals(LadderProblem.TOO_FLAT, error.problem)
            val message = error.message.orEmpty()
            assertTrue("too low" in message && "Try fewer rounds or larger starting chips" in message, message)
        }
    }

    @Test
    fun `a stack that isn't a whole number of smallest chips is rejected with the nearest multiples`() {
        val error = assertFailsWith<BlindLadderException> { BlindFittingAlgorithm.fitBlinds(9, 25, 1010) }
        assertEquals(LadderProblem.STACK_NOT_MULTIPLE_OF_CHIP, error.problem)
        assertTrue("Try 1000 or 1025" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `feasible stacks are exactly the slowest-to-fastest climb for the level count`() {
        // 9 levels from 50: slowest climb 1, 2, 3, 4, 6, 8, 11, 15, 20 chips; fastest 2^8 = 256.
        assertEquals(1_000L..12_800L, BlindFittingAlgorithm.feasibleStackRange(9, 50))
        assertTrue(BlindFittingAlgorithm.isFeasible(9, 50, 1_000))
        assertTrue(BlindFittingAlgorithm.isFeasible(9, 50, 12_800))
        assertTrue(!BlindFittingAlgorithm.isFeasible(9, 50, 950))
        assertTrue(!BlindFittingAlgorithm.isFeasible(9, 50, 12_850))
        assertTrue(!BlindFittingAlgorithm.isFeasible(9, 50, 1_025), "not a multiple of 50")
        // Both ends are reachable: the slowest ladder is forced
        assertEquals(
            listOf(50, 100, 150, 200, 300, 400, 550, 750, 1_000),
            BlindFittingAlgorithm.fitBlinds(9, 50, 1_000).blinds
        )
        assertEquals((0..8).map { 50 shl it }, BlindFittingAlgorithm.fitBlinds(9, 50, 12_800).blinds)
    }

    @Test
    fun `the default ladder reads like a printed blind sheet`() {
        assertEquals(
            listOf(50, 100, 150, 300, 500, 800, 1_500, 3_000, 5_000),
            BlindFittingAlgorithm.fitBlinds(numRounds = 9, smallestChip = 50, startingChips = 5_000).blinds
        )
    }

    @Test
    fun `the fallback ladder is in band whenever the configuration is feasible`() {
        // The candidate search normally finds the ladder; the greedy fallback must never fail either.
        val configurations = listOf(
            Triple(9, 50, 5_000),
            Triple(15, 25, 5_000),
            Triple(9, 50, 1_000),
            Triple(12, 1, 1_000),
            Triple(30, 1, 50_000)
        )
        configurations.forEach { (rounds, chip, stack) ->
            val ladder = BlindLadderSearch.greedyLadder(rounds, chip, (stack / chip).toLong()).map { it * chip }
            assertEquals(chip.toLong(), ladder.first())
            assertEquals(stack.toLong(), ladder.last())
            assertTrue(
                ladder.zipWithNext().all { (a, b) -> 13 * a <= 10 * b && b <= 2 * a },
                "out of band: $ladder"
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
