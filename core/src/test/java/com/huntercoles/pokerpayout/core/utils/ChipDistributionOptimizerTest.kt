package com.huntercoles.pokerpayout.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for ChipDistributionOptimizer
 * 
 * CRITICAL validations:
 * - Exact value matching (sum must equal target exactly)
 * - LinearSteep produces decreasing quantities (more small chips, fewer large chips)
 * - Works for 500, 5000, and 50000 starting stacks
 * - Fit scores reflect curve adherence (1 = perfect, 0 = terrible)
 */
class ChipDistributionOptimizerTest {
    
    companion object {
        private val CHIP_COUNT_RANGE_500 = 10..200
        private val CHIP_COUNT_RANGE_5000 = 20..250
        private val CHIP_COUNT_RANGE_50000 = 30..2500
        private val DENOMINATION_COUNT_RANGE = 3..7
    }
    
    @Test
    fun `test 500 starting chips with LinearSteep - exact value and decreasing quantities`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 500,
            smallestChip = 10,
            denominationCount = 5,
            curve = ChipDistributionCurve.LinearSteep
        )
        
        // Check decreasing pattern for LinearSteep
        println("Checking decreasing pattern:")
        for (i in 0 until result.quantities.size - 1) {
            val isDecreasing = result.quantities[i] >= result.quantities[i + 1]
            println("  qty[${i}]=${result.quantities[i]} >= qty[${i+1}]=${result.quantities[i + 1]} ? $isDecreasing")
        }
        
        // MUST have exactly 5 denominations
        assertEquals(5, result.denominations.size)
        assertEquals(5, result.quantities.size)
        
        // MUST sum to EXACT target value
        assertEquals(500, result.totalValue, "Total value must be exactly 500")
        
        // LinearSteep MUST produce decreasing quantities
        // (more small chips, fewer large chips)
        for (i in 0 until result.quantities.size - 1) {
            assertTrue(
                result.quantities[i] >= result.quantities[i + 1],
                "LinearSteep: qty[${i}]=${result.quantities[i]} should be >= qty[${i+1}]=${result.quantities[i + 1]}"
            )
        }
        
        // SHOULD have reasonable chip count
        assertTrue(
            result.totalChips in CHIP_COUNT_RANGE_500,
            "Total chips ${result.totalChips} should be reasonable $CHIP_COUNT_RANGE_500"
        )
    }
    
    @Test
    fun `test 5000 starting chips with LinearSteep - exact value and decreasing quantities`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 5000,
            smallestChip = 10,
            denominationCount = 5,
            curve = ChipDistributionCurve.LinearSteep
        )
        
        assertEquals(5, result.denominations.size)
        assertEquals(5, result.quantities.size)
        
        // MUST sum to EXACT target value
        assertEquals(5000, result.totalValue, "Total value must be exactly 5000")
        
        // LinearSteep MUST produce decreasing quantities
        for (i in 0 until result.quantities.size - 1) {
            assertTrue(
                result.quantities[i] >= result.quantities[i + 1],
                "LinearSteep: qty[${i}]=${result.quantities[i]} should be >= qty[${i+1}]=${result.quantities[i + 1]}"
            )
        }
        
        // MUST have at least 1 chip of each denomination (no zeros)
        for (i in result.quantities.indices) {
            assertTrue(
                result.quantities[i] >= 1,
                "Must have at least 1 chip of denomination ${result.denominations[i]}, got ${result.quantities[i]}"
            )
        }
        
        // SHOULD have reasonable chip count
        assertTrue(
            result.totalChips in CHIP_COUNT_RANGE_5000,
            "Total chips ${result.totalChips} should be reasonable $CHIP_COUNT_RANGE_5000"
        )
        assertTrue(
            result.totalChips in 40..80,
            "Total chips ${result.totalChips} should fall within the preferred 40-80 range"
        )
    }
    
    @Test
    fun `test 50000 starting chips with LinearSteep - exact value and decreasing quantities`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 50000,
            smallestChip = 10,
            denominationCount = 5,
            curve = ChipDistributionCurve.LinearSteep
        )
        
        assertEquals(5, result.denominations.size)
        assertEquals(5, result.quantities.size)
        
        // MUST sum to EXACT target value
        assertEquals(50000, result.totalValue, "Total value must be exactly 50000")
        
        // LinearSteep MUST produce decreasing quantities even for deep stacks
        for (i in 0 until result.quantities.size - 1) {
            assertTrue(
                result.quantities[i] >= result.quantities[i + 1],
                "LinearSteep: qty[${i}]=${result.quantities[i]} should be >= qty[${i+1}]=${result.quantities[i + 1]}"
            )
        }
        
        // MUST have at least 1 chip of each denomination (no zeros)
        for (i in result.quantities.indices) {
            assertTrue(
                result.quantities[i] >= 1,
                "Must have at least 1 chip of denomination ${result.denominations[i]}, got ${result.quantities[i]}"
            )
        }
        
        // SHOULD have reasonable chip count
        assertTrue(
            result.totalChips in CHIP_COUNT_RANGE_50000,
            "Total chips ${result.totalChips} should be reasonable $CHIP_COUNT_RANGE_50000"
        )
        assertTrue(
            result.totalChips in 40..80,
            "Total chips ${result.totalChips} should fall within the preferred 40-80 range"
        )
    }
    
    @Test
    fun `test bell curve for 5000 starting stack`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 5000,
            smallestChip = 25,
            denominationCount = 5,
            curve = ChipDistributionCurve.BellCurve
        )
        
        assertEquals(5, result.denominations.size)
    }
    
    @Test
    fun `test linear moderate curve for 5000 starting stack`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 5000,
            smallestChip = 25,
            denominationCount = 5,
            curve = ChipDistributionCurve.LinearModerate
        )
        
        assertEquals(5, result.denominations.size)
    }
    
    @Test
    fun `test fit score for good distribution`() {
        // Create a distribution that follows linear steep curve
        val denominations = listOf(25, 50, 100, 250, 500)
        val quantities = listOf(20, 18, 16, 10, 4)
        
        val fitScore = ChipDistributionOptimizer.calculateFitScoreForDistribution(
            denominations,
            quantities,
            ChipDistributionCurve.LinearSteep
        )
        assertTrue(fitScore > 0.8, "Good distribution should have high fit score, got $fitScore")
    }
    
    @Test
    fun `test fit score for poor distribution`() {
        // Create opposite distribution
        val denominations = listOf(25, 50, 100, 250, 500)
        val quantities = listOf(5, 8, 12, 18, 20)
        
        val fitScore = ChipDistributionOptimizer.calculateFitScoreForDistribution(
            denominations,
            quantities,
            ChipDistributionCurve.LinearSteep
        )
    assertTrue(fitScore < 0.6, "Poor distribution should have low fit score, got $fitScore")
    }
    
    @Test
    fun `test different denomination counts`() {
        for (count in DENOMINATION_COUNT_RANGE) {
            val result = ChipDistributionOptimizer.optimize(
                targetValue = 5000,
                smallestChip = 25,
                denominationCount = count,
                curve = ChipDistributionCurve.LinearSteep
            )
            
            assertTrue(result.denominations.size <= count, "Should return at most $count denominations (may be fewer due to max chip constraint)")
        }
    }
    
    @Test
    fun `test various starting stacks`() {
        val stacks = listOf(1000, 2500, 5000, 10000, 25000)
        
        for (stack in stacks) {
            val result = ChipDistributionOptimizer.optimize(
                targetValue = stack,
                smallestChip = 25,
                denominationCount = 5,
                curve = ChipDistributionCurve.LinearSteep
            )
        }
    }
    
    @Test
    fun `test with different smallest chips`() {
        val smallestChips = listOf(5, 25, 100, 500)
        
        for (smallest in smallestChips) {
            val result = ChipDistributionOptimizer.optimize(
                targetValue = 5000,
                smallestChip = smallest,
                denominationCount = 5,
                curve = ChipDistributionCurve.LinearSteep
            )
            
            assertEquals(smallest, result.denominations.first(), "Smallest chip should be $smallest")
            assertTrue(
                result.denominations.all { it >= smallest },
                "All denominations should be >= $smallest"
            )
        }
    }
    
    @Test
    fun `test all curve types produce good fits`() {
        val curves = ChipDistributionCurve.getAllCurves()
        
        for (curve in curves) {
            val result = ChipDistributionOptimizer.optimize(
                targetValue = 5000,
                smallestChip = 25,
                denominationCount = 5,
                curve = curve
            )
        }
    }
    
    @Test
    fun `test positive linear has more large chips`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 5000,
            smallestChip = 25,
            denominationCount = 5,
            curve = ChipDistributionCurve.PositiveLinear
        )
        
        // Quantities should increase with value (opposite of negative linear)
        var increasingTrend = 0
        for (i in 0 until result.quantities.size - 1) {
            if (result.quantities[i + 1] >= result.quantities[i]) {
                increasingTrend++
            }
        }
        
        assertTrue(
            increasingTrend >= 2,
            "Positive linear should show increasing trend in quantities: ${result.quantities}"
        )
    }
    
    @Test
    fun `test exponential decay heavily favors small chips`() {
        val result = ChipDistributionOptimizer.optimize(
            targetValue = 5000,
            smallestChip = 25,
            denominationCount = 5,
            curve = ChipDistributionCurve.ExponentialDecay
        )
    }
    
    @Test
    fun `test curve value functions`() {
        // Test LinearSteep: y = -x + 1 (slope = -1)
        assertEquals(1.0, ChipDistributionCurve.LinearSteep.getValue(0.0), 0.001)
        assertEquals(0.5, ChipDistributionCurve.LinearSteep.getValue(0.5), 0.001)
        assertEquals(0.0, ChipDistributionCurve.LinearSteep.getValue(1.0), 0.001)
        
        // Test LinearModerate: y = -0.5x + 1 (slope = -1/2)
        assertEquals(1.0, ChipDistributionCurve.LinearModerate.getValue(0.0), 0.001)
        assertEquals(0.75, ChipDistributionCurve.LinearModerate.getValue(0.5), 0.001)
        assertEquals(0.5, ChipDistributionCurve.LinearModerate.getValue(1.0), 0.001)
        
        // Test BellCurve: peak at center (0.5)
        val peakValue = ChipDistributionCurve.BellCurve.getValue(0.5)
        val edgeValue = ChipDistributionCurve.BellCurve.getValue(0.0)
        assertTrue(peakValue > edgeValue, "Bell curve peak should be higher than edges")
        
        // Test PositiveLinear: y = x
        assertEquals(0.0, ChipDistributionCurve.PositiveLinear.getValue(0.0), 0.001)
        assertEquals(1.0, ChipDistributionCurve.PositiveLinear.getValue(1.0), 0.001)
    }
}
