package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Result of blind fitting algorithm including the fitted blinds and quality score.
 */
data class BlindFittingResult(
    val blinds: List<Int>,
    val fitScore: Double,
    val calculatedGrowthRate: Double
)

/**
 * Fits blind values to an exponential growth curve using logarithmic regression.
 * Uses the first level (smallestChip) and final level (startingChips) as fixed endpoints,
 * then selects valid poker amounts that minimize squared error from the ideal curve.
 * 
 * The required exponential growth rate is calculated as:
 * r = (startingChips / smallestChip)^(1 / (numRounds - 1))
 * 
 * This calculated rate must be between MIN_BLIND_GROWTH_RATE (1.3) and MAX_BLIND_GROWTH_RATE (2.0)
 * or the configuration is considered invalid.
 */
object BlindFittingAlgorithm {

    /**
     * Fits blinds to exponential growth curve and returns fitted values with quality score.
     * 
     * @throws IllegalArgumentException if the calculated growth rate is outside valid bounds (1.3-2.0)
     */
    fun fitBlinds(
        numRounds: Int,
        smallestChip: Int,
        startingChips: Int
    ): BlindFittingResult {
        require(numRounds >= 2) { "Must have at least 2 rounds" }
        require(smallestChip > 0) { "Smallest chip must be positive" }
        require(startingChips >= smallestChip) { "Starting chips must be >= smallest chip" }
        
        // Calculate the required exponential growth rate for this configuration
        val calculatedGrowthRate = calculateGrowthRate(
            numRounds = numRounds,
            smallestChip = smallestChip,
            startingChips = startingChips
        )
        
        // Validate the calculated growth rate is within acceptable bounds
        // Allow a small tolerance (5%) for rates slightly outside bounds
        val minRate = BlindStructureConstants.MIN_BLIND_GROWTH_RATE
        val maxRate = BlindStructureConstants.MAX_BLIND_GROWTH_RATE
        val tolerance = 0.05
        
        require(calculatedGrowthRate >= minRate * (1 - tolerance)) {
            "Calculated growth rate %.3f is too low (min: %.2f). Try fewer rounds or larger starting chips."
                .format(calculatedGrowthRate, minRate)
        }
        require(calculatedGrowthRate <= maxRate * (1 + tolerance)) {
            "Calculated growth rate %.3f is too high (max: %.2f). Try more rounds or smaller starting chips."
                .format(calculatedGrowthRate, maxRate)
        }

        // Generate candidate blinds using exponential growth with calculated rate
        val candidateBlinds = generateCandidateBlinds(
            numRounds = numRounds,
            smallestChip = smallestChip,
            startingChips = startingChips,
            calculatedGrowthRate = calculatedGrowthRate
        )
        
        // Calculate fit score using the calculated growth rate
        val fitScore = calculateFitScore(
            blinds = candidateBlinds,
            calculatedGrowthRate = calculatedGrowthRate
        )
        
        return BlindFittingResult(
            blinds = candidateBlinds,
            fitScore = fitScore,
            calculatedGrowthRate = calculatedGrowthRate
        )
    }
    
    /**
     * Calculates the required exponential growth rate to go from smallest chip
     * to starting chips over the given number of rounds.
     * 
     * Formula: r = (startingChips / smallestChip)^(1 / (numRounds - 1))
     * 
     * This represents the constant multiplier needed for exponential growth
     * from smallestChip to startingChips over numRounds levels.
     */
    private fun calculateGrowthRate(
        numRounds: Int,
        smallestChip: Int,
        startingChips: Int
    ): Double {
        val ratio = startingChips.toDouble() / smallestChip.toDouble()
        val exponent = 1.0 / (numRounds - 1)
        return ratio.pow(exponent)
    }

    /**
     * Generates blind progression using exponential growth with the target rate.
     */
    private fun generateCandidateBlinds(
        numRounds: Int,
        smallestChip: Int,
        startingChips: Int,
        calculatedGrowthRate: Double
    ): List<Int> {
        val result = mutableListOf<Int>()
        
        // First level is always the smallest chip
        result.add(smallestChip)
        
        // For intermediate levels, use exponential growth with calculated rate
        for (i in 1 until numRounds - 1) {
            // Calculate ideal value using exponential growth: smallestChip * r^i
            val idealValue = smallestChip * calculatedGrowthRate.pow(i.toDouble())
            
            // Round to nearest valid poker amount, ensuring it's > previous
            val previousBlind = result.last()
            val roundedValue = roundToValidBlindAmount(idealValue.toInt(), smallestChip, previousBlind, startingChips)
            result.add(roundedValue)
        }
        
        // Last level is always the starting chips
        result.add(startingChips)
        
        return result
    }

    /**
     * Rounds a value to the nearest valid blind amount.
     * Rules:
     * - Values MUST be multiples of smallest chip (CRITICAL - non-negotiable)
     * - Values > SMOOTH_NUMBER_THRESHOLD (25) SHOULD end in 0 (preferred but not forced)
     * - Must be strictly greater than previous blind
     * 
     * Strategy: We try to honor smooth numbers when it doesn't significantly hurt fit quality.
     * If the ideal rounded value is close to a smooth number, we'll snap to it. Otherwise,
     * we'll use the closest valid multiple of smallestChip even if it doesn't end in 0.
     */
    private fun roundToValidBlindAmount(value: Int, smallestChip: Int, previousBlind: Int, maxValue: Int): Int {
        // Round to nearest multiple of smallestChip first
        var candidate = ((value.toDouble() / smallestChip + 0.5).toInt()) * smallestChip
        
        // Ensure strictly greater than previous
        if (candidate <= previousBlind) {
            candidate = previousBlind + smallestChip
        }
        
        // If above threshold, try to find a nearby smooth number (ends in 0)
        if (candidate > BlindStructureConstants.SMOOTH_NUMBER_THRESHOLD) {
            val roundedTo10 = ((candidate.toDouble() / 10 + 0.5).toInt()) * 10
            
            // Use the smooth number if it:
            // 1. Is divisible by smallestChip
            // 2. Is greater than previous
            // 3. Doesn't overshoot maxValue
            // We're more lenient here - accept smooth numbers even if they're a bit further away
            if (roundedTo10 % smallestChip == 0 && 
                roundedTo10 > previousBlind && 
                roundedTo10 < maxValue) {
                candidate = roundedTo10
            }
        }
        
        // Cap at maxValue
        if (candidate >= maxValue) {
            candidate = maxValue
        }
        
        return candidate
    }
    
    /**
     * Calculate least common multiple of two numbers.
     */
    private fun lcm(a: Int, b: Int): Int {
        return (a * b) / gcd(a, b)
    }
    
    /**
     * Calculate greatest common divisor using Euclidean algorithm.
     */
    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    /**
     * Calculates fit quality score using root mean square error in log space.
     * Score = 1 - RMSE, where RMSE is computed on log-transformed values.
     * Higher scores (closer to 1.0) indicate better fit to exponential curve.
     * 
     * Uses the calculated growth rate as the ideal exponential curve.
     */
    private fun calculateFitScore(
        blinds: List<Int>,
        calculatedGrowthRate: Double
    ): Double {
        if (blinds.size < 2) return 1.0
        
        val firstBlind = blinds.first().toDouble()
        
        // Calculate RMSE in log space against ideal exponential curve
        var sumSquaredError = 0.0
        for (i in blinds.indices) {
            val expectedValue = firstBlind * calculatedGrowthRate.pow(i.toDouble())
            val expectedLog = ln(expectedValue)
            val actualLog = ln(blinds[i].toDouble())
            val error = actualLog - expectedLog
            sumSquaredError += error * error
        }
        
        val rmse = sqrt(sumSquaredError / blinds.size)
        
        // Fit score: 1 - RMSE (clamped to [0, 1])
        return (1.0 - rmse).coerceIn(0.0, 1.0)
    }
}
