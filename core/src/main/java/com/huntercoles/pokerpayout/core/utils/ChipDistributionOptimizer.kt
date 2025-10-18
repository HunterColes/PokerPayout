package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.design.ChipDenominations
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Optimizer that selects the best chip denominations to fit a mathematical curve.
 * 
 * Algorithm:
 * 1. Generate candidate denomination sets
 * 2. For each set, fit quantities to curve to minimize distance
 * 3. Adjust quantities to exactly match target value (starting chips)
 * 4. Target ~50 total chips per person
 * 5. Calculate fit score where 1.0 = perfect fit, 0.0 = terrible fit
 * 6. Return the set with best fit score
 */
object ChipDistributionOptimizer {
    
    private const val TARGET_CHIP_COUNT = 50 // Target total physical chips per person
    private const val TARGET_TOTAL_CHIPS_FOR_CALCULATION = 30.0 // Target for curve calculation
    private const val PREFERRED_RANGE_FIT_BOOST = 2.0 // Boost fit score for preferred chip count ranges
    
    /**
     * Find optimal chip distribution for a target value and curve
     * 
     * @param targetValue Total chip value (e.g., 500, 5000, 50000)
     * @param smallestChip Minimum denomination allowed (e.g., 25 for blinds)
     * @param denominationCount Number of different chip types to use (default 5)
     * @param curve Mathematical curve to fit
     * @return Optimized chip distribution with fit score
     */
    fun optimize(
        targetValue: Int,
        smallestChip: Int,
        denominationCount: Int = 5,
        curve: ChipDistributionCurve
    ): ChipDistributionResult {
        
        // Get available denominations >= smallestChip
        // User wants largest chip to be no more than 1/4 of total starting chips
        // So get chips from smallestChip up to targetValue/4
        val availableDenoms = ChipDenominations.getChipsUpTo(targetValue / 4)
            .filter { it.value >= smallestChip }
            .map { it.value }
            .sorted()
        
        if (availableDenoms.isEmpty()) {
            throw IllegalStateException("No valid denominations found for targetValue=$targetValue, smallestChip=$smallestChip")
        }
        
        val validCount = denominationCount.coerceIn(1, availableDenoms.size)
        
        if (availableDenoms.size <= validCount) {
            // Use all available
            return optimizeForDenominations(targetValue, availableDenoms, curve)
        }
        
        // SLIDING WINDOW OPTIMIZATION
        // Try every consecutive window of 'validCount' denominations
        // PREFER: Total chips in reasonable range
        // REQUIRE: All chips must have at least 1 chip, window must start with smallestChip
        // Select the solution with best fit score among valid solutions
        
        var bestResult: ChipDistributionResult? = null
        var bestFitScore = -1.0
        
        for (startIdx in 0..(availableDenoms.size - validCount)) {
            val windowDenoms = availableDenoms.subList(startIdx, startIdx + validCount)
            
            // Skip windows that don't start with smallestChip
            if (windowDenoms.first() != smallestChip) continue
            
            val result = optimizeForDenominations(targetValue, windowDenoms, curve)
            
            // Check requirements
            val hasMinimumChips = result.quantities.all { it >= 1 }
            
            if (hasMinimumChips) {
                // Valid solution - check if it has better fit score
                // Prefer solutions in reasonable chip range
                val inPreferredRange = when {
                    targetValue <= 1000 -> result.totalChips in 20..100
                    targetValue <= 10000 -> result.totalChips in 40..120
                    else -> result.totalChips in 50..150
                }
                
                val score = if (inPreferredRange) result.fitScore * PREFERRED_RANGE_FIT_BOOST else result.fitScore // Boost preferred solutions
                
                if (score > bestFitScore) {
                    bestResult = result
                    bestFitScore = score
                }
            }
        }
        
        return bestResult ?: throw IllegalStateException("No valid chip distribution found")
    }
    
    /**
     * Select the best N denominations from available chips
     * Strategy: Use geometric spacing for better coverage
     */
    private fun selectBestDenominations(
        available: List<Int>,
        count: Int,
        targetValue: Int
    ): List<Int> {
        if (available.size <= count) return available
        
        // Always include the smallest
        val result = mutableListOf(available.first())
        
        // For remaining slots, pick evenly spaced denominations
        val remaining = count - 1
        if (remaining > 0) {
            val step = (available.size - 1).toDouble() / remaining
            for (i in 1..remaining) {
                val index = (i * step).toInt().coerceIn(1, available.size - 1)
                result.add(available[index])
            }
        }
        
        return result.sorted().distinct().take(count)
    }
    
    /**
     * Optimize quantities for a specific set of denominations using perfect curve fitting
     * 
     * Algorithm:
     * 1. Sort denominations < targetValue
     * 2. Normalize X (denominations) to 0-1 range
     * 3. Calculate ideal Y using curve equation (perfect fit)
     * 4. Scale Y to get exact quantities that fit the curve
     * 5. Round to whole numbers
     * 6. Use small perturbations (±1, ±2) to adjust to exact total
     */
    private fun optimizeForDenominations(
        targetValue: Int,
        denominations: List<Int>,
        curve: ChipDistributionCurve
    ): ChipDistributionResult {
        
        val sortedDenoms = denominations.sorted()
        
        // Step 1: Normalize X (denominations) to 0-1 range
        val minDenom = sortedDenoms.first().toDouble()
        val maxDenom = sortedDenoms.last().toDouble()
        
        val normalizedX = if (minDenom == maxDenom) {
            List(sortedDenoms.size) { 0.5 }
        } else {
            sortedDenoms.map { (it - minDenom) / (maxDenom - minDenom) }
        }
        
        // Step 2: Get perfect Y values from curve equation
        val idealY = normalizedX.map { x -> curve.getValue(x).coerceIn(0.0, 1.0) }
        
        // Step 3: Calculate exact quantities that fit the curve perfectly
        val exactQuantities = calculatePerfectCurveFit(sortedDenoms, idealY, targetValue)
        
        // Step 4: Round to whole numbers
        val roundedQuantities = exactQuantities.map { it.toInt() }
        val roundedValue = sortedDenoms.zip(roundedQuantities).sumOf { (denom, qty) -> denom * qty }
        
        // Ensure no zeros - if any quantity is 0, set to 1 and adjust others
        val adjustedQuantities = roundedQuantities.toMutableList()
        val zeroIndices = roundedQuantities.withIndex().filter { it.value == 0 }.map { it.index }
        for (idx in zeroIndices) {
            adjustedQuantities[idx] = 1
        }
        // Compensate by reducing the largest quantities
        var compensationNeeded = zeroIndices.size
        var idx = adjustedQuantities.size - 1
        while (compensationNeeded > 0 && idx >= 0) {
            if (adjustedQuantities[idx] > 1) {
                adjustedQuantities[idx]--
                compensationNeeded--
            }
            idx--
        }
        
        val finalRoundedQuantities = adjustedQuantities
        val finalRoundedValue = sortedDenoms.zip(finalRoundedQuantities).sumOf { (denom, qty) -> denom * qty }
        
        // Step 5: Adjust with small perturbations to hit exact total
        val finalQuantities = adjustWithSmallPerturbations(
            sortedDenoms, 
            finalRoundedQuantities, 
            idealY, 
            targetValue,
            curve
        )
        val finalValue = sortedDenoms.zip(finalQuantities).sumOf { (d, q) -> d * q }
        
        // Step 6: Calculate fit score
        val maxQty = finalQuantities.maxOrNull()?.toDouble() ?: 1.0
        val normalizedQty = if (maxQty > 0.0) {
            finalQuantities.map { it / maxQty }
        } else {
            List(finalQuantities.size) { 0.0 }
        }
        
        val fitScore = calculateFitScore(idealY, normalizedQty)
        val totalChips = finalQuantities.sum()
        val actualValue = sortedDenoms.zip(finalQuantities).sumOf { (denom, qty) -> denom * qty }
        
        return ChipDistributionResult(
            denominations = sortedDenoms,
            quantities = finalQuantities,
            fitScore = fitScore,
            totalChips = totalChips,
            totalValue = actualValue,
            curveUsed = curve
        )
    }
    
    /**
     * Calculate quantities that follow the curve shape but with reasonable total chip counts
     */
    private fun calculatePerfectCurveFit(
        denominations: List<Int>,
        idealY: List<Double>,
        targetValue: Int
    ): List<Double> {
        // Instead of perfect mathematical fit, distribute proportionally with reasonable chip counts
        val targetTotalChips = TARGET_TOTAL_CHIPS_FOR_CALCULATION // Aim for about 30 chips total
        val totalIdealWeight = idealY.sum()
        
        // Distribute chip counts proportionally to the curve
        val chipCounts = idealY.map { y -> (y / totalIdealWeight) * TARGET_TOTAL_CHIPS_FOR_CALCULATION }
        
        // Now scale these chip counts so their total value equals targetValue
        val totalValueFromChipCounts = denominations.zip(chipCounts).sumOf { (denom, count) -> denom * count }
        val scaleFactor = targetValue / totalValueFromChipCounts
        
        return chipCounts.map { count -> count * scaleFactor }
    }
    
    /**
     * Adjust quantities with small perturbations (±1, ±2) to hit exact total
     * Uses combinatorial search to find best combination that preserves curve fit
     */
    private fun adjustWithSmallPerturbations(
        denominations: List<Int>,
        baseQuantities: List<Int>,
        idealY: List<Double>,
        targetValue: Int,
        curve: ChipDistributionCurve
    ): List<Int> {
        val currentValue = denominations.zip(baseQuantities).sumOf { (d, q) -> d * q }
        val error = targetValue - currentValue
        
        if (error == 0) {
            return baseQuantities // Already perfect!
        }
        
        // Try small adjustments: ±1 to ±20 for each denomination
        val adjustments = (-20..20).filter { it != 0 }
        val n = denominations.size
        
        // Find best combination of adjustments
        var bestQuantities = baseQuantities
        var bestScore = Double.MAX_VALUE
        var bestError = abs(error)
        
        // Try single adjustments first
        for (i in 0 until n) {
            for (adj in adjustments) {
                val newQty = baseQuantities[i] + adj
                if (newQty >= 1) {  // Changed from >= 0 to >= 1
                    val testQuantities = baseQuantities.toMutableList()
                    testQuantities[i] = newQty
                    if (isValidCurvePattern(testQuantities, curve)) {
                        val testValue = denominations.zip(testQuantities).sumOf { (d, q) -> d * q }
                        val testError = abs(targetValue - testValue)
                        
                        if (testError < bestError) {
                            bestError = testError
                            bestQuantities = testQuantities
                        }
                    }
                }
            }
        }
        
        // If single adjustments didn't work, try double adjustments
        if (bestError > 0) {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    for (adj1 in adjustments) {
                        for (adj2 in adjustments) {
                            val newQty1 = baseQuantities[i] + adj1
                            val newQty2 = baseQuantities[j] + adj2
                            if (newQty1 >= 1 && newQty2 >= 1) {
                                val testQuantities = baseQuantities.toMutableList()
                                testQuantities[i] = newQty1
                                testQuantities[j] = newQty2
                                if (isValidCurvePattern(testQuantities, curve)) {
                                    val testValue = denominations.zip(testQuantities).sumOf { (d, q) -> d * q }
                                    val testError = abs(targetValue - testValue)
                                    
                                    if (testError < bestError) {
                                        bestError = testError
                                        bestQuantities = testQuantities
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // If still not exact, try triple adjustments
        if (bestError > 0) {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    for (k in j + 1 until n) {
                        for (adj1 in adjustments) {
                            for (adj2 in adjustments) {
                                for (adj3 in adjustments) {
                                    val newQty1 = baseQuantities[i] + adj1
                                    val newQty2 = baseQuantities[j] + adj2
                                    val newQty3 = baseQuantities[k] + adj3
                                    if (newQty1 >= 1 && newQty2 >= 1 && newQty3 >= 1) {
                                        val testQuantities = baseQuantities.toMutableList()
                                        testQuantities[i] = newQty1
                                        testQuantities[j] = newQty2
                                        testQuantities[k] = newQty3
                                        if (isValidCurvePattern(testQuantities, curve)) {
                                            val testValue = denominations.zip(testQuantities).sumOf { (d, q) -> d * q }
                                            val testError = abs(targetValue - testValue)
                                            
                                            if (testError < bestError) {
                                                bestError = testError
                                                bestQuantities = testQuantities
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return bestQuantities
    }

    /**
     * Check if a quantity adjustment is valid for the given curve pattern
     */
    private fun isValidForCurve(newQty: Int, index: Int, baseQuantities: List<Int>, curve: ChipDistributionCurve): Boolean {
        return when (curve) {
            ChipDistributionCurve.LinearSteep -> {
                // For LinearSteep: quantities should be non-increasing (decreasing or equal)
                when (index) {
                    0 -> newQty >= baseQuantities.getOrElse(1) { 0 } // First >= second
                    baseQuantities.size - 1 -> baseQuantities.getOrElse(index - 1) { Int.MAX_VALUE } >= newQty // Last <= previous
                    else -> {
                        val prev = baseQuantities.getOrElse(index - 1) { Int.MAX_VALUE }
                        val next = baseQuantities.getOrElse(index + 1) { 0 }
                        prev >= newQty && newQty >= next
                    }
                }
            }
            ChipDistributionCurve.LinearModerate -> {
                // Similar logic for LinearModerate
                when (index) {
                    0 -> newQty >= baseQuantities.getOrElse(1) { 0 }
                    baseQuantities.size - 1 -> baseQuantities.getOrElse(index - 1) { Int.MAX_VALUE } >= newQty
                    else -> {
                        val prev = baseQuantities.getOrElse(index - 1) { Int.MAX_VALUE }
                        val next = baseQuantities.getOrElse(index + 1) { 0 }
                        prev >= newQty && newQty >= next
                    }
                }
            }
            else -> true // Other curves don't have strict ordering requirements
        }
    }

    /**
     * Check if the entire quantity array follows the curve pattern
     */
    private fun isValidCurvePattern(quantities: List<Int>, curve: ChipDistributionCurve): Boolean {
        return when (curve) {
            ChipDistributionCurve.LinearSteep -> {
                // Check that quantities are non-increasing
                for (i in 0 until quantities.size - 1) {
                    if (quantities[i] < quantities[i + 1]) {
                        return false
                    }
                }
                true
            }
            ChipDistributionCurve.LinearModerate -> {
                // Same logic for LinearModerate
                for (i in 0 until quantities.size - 1) {
                    if (quantities[i] < quantities[i + 1]) {
                        return false
                    }
                }
                true
            }
            else -> true // Other curves don't have strict ordering requirements
        }
    }
    
    /**
     * Compute penalty for deviating from ideal curve
     */
    private fun computeFitPenalty(quantities: List<Int>, idealY: List<Double>): Double {
        val maxQty = quantities.maxOrNull()?.toDouble() ?: 1.0
        if (maxQty <= 0.0) return Double.MAX_VALUE
        
        val normalizedQty = quantities.map { it / maxQty }
        
        var sumSqDist = 0.0
        for (i in idealY.indices) {
            val diff = idealY[i] - normalizedQty[i]
            sumSqDist += diff * diff
        }
        
        return kotlin.math.sqrt(sumSqDist / idealY.size)
    }
    /**
     * Calculate fit score: 1.0 = perfect fit, 0.0 = terrible fit
     * Measures how well normalized quantities match the ideal curve
     */
    private fun calculateFitScore(
        idealY: List<Double>,
        normalizedQty: List<Double>
    ): Double {
        require(idealY.size == normalizedQty.size)
        
        if (idealY.isEmpty()) return 0.0
        
        // Calculate RMS vertical distance
        var sumSqDist = 0.0
        for (i in idealY.indices) {
            val diff = idealY[i] - normalizedQty[i]
            sumSqDist += diff * diff
        }
        
        val rms = kotlin.math.sqrt(sumSqDist / idealY.size)
        
        // Maximum possible RMS distance is sqrt(2) ≈ 1.414
        // Invert so 1.0 = perfect, 0.0 = terrible
        val maxRMS = kotlin.math.sqrt(2.0)
        return (1.0 - (rms / maxRMS)).coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculate fit score for an existing distribution
     * Useful for testing and validation
     */
    fun calculateFitScoreForDistribution(
        denominations: List<Int>,
        quantities: List<Int>,
        curve: ChipDistributionCurve
    ): Double {
        require(denominations.size == quantities.size)
        
        val sortedPairs = denominations.zip(quantities).sortedBy { it.first }
        val sortedDenoms = sortedPairs.map { it.first }
        val sortedQtys = sortedPairs.map { it.second }
        
        val minValue = sortedDenoms.first()
        val maxValue = sortedDenoms.last()
        
        // Normalize x positions
        val normalizedX = sortedDenoms.map { denom ->
            if (maxValue == minValue) 0.5
            else (denom - minValue).toDouble() / (maxValue - minValue)
        }
        
        // Get ideal Y from curve
        val idealY = normalizedX.map { x -> curve.getValue(x).coerceIn(0.0, 1.0) }
        
        // Normalize quantities
        val maxQty = sortedQtys.maxOrNull()?.toDouble() ?: 1.0
        val normalizedQty = if (maxQty > 0.0) {
            sortedQtys.map { it / maxQty }
        } else {
            List(sortedQtys.size) { 0.0 }
        }
        
        return calculateFitScore(idealY, normalizedQty)
    }
}
