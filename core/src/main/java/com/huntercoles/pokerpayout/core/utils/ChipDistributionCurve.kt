package com.huntercoles.pokerpayout.core.utils

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Represents different mathematical curves for chip distribution optimization.
 * 
 * The curve maps chip denominations to their ideal quantities:
 * - X-axis: Normalized chip value (0 = smallest, 1 = largest)
 * - Y-axis: Normalized chip count (0 = fewest, 1 = most)
 * 
 * Curves define the "philosophy" of chip distribution:
 * - Negative Linear: More small chips, fewer large chips (classic tournament)
 * - Bell Curve: Most chips in middle denominations (versatile)
 */
sealed class ChipDistributionCurve {
    
    /**
     * Get the ideal normalized quantity (0-1) for a chip at normalized value position (0-1)
     * @param x Normalized position where 0 = smallest denomination, 1 = largest
     * @return Normalized quantity where 0 = fewest chips, 1 = most chips
     */
    abstract fun getValue(x: Double): Double
    
    /**
     * Display name for UI
     */
    abstract val displayName: String
    
    /**
     * Description of the distribution philosophy
     */
    abstract val description: String
    
    /**
     * Linear Steep: y = -x + 1 (slope = -1)
     * Equation of line from (0,1) to (1,0)
     * 
     * Philosophy: Strong emphasis on small denominations for making change,
     * rapid decrease to large denominations.
     * Classic steep tournament distribution.
     */
    object LinearSteep : ChipDistributionCurve() {
        override fun getValue(x: Double): Double = -x + 1.0
        override val displayName = "Linear Steep"
        override val description = "Steep decline - strong emphasis on small chips"
    }
    
    /**
     * Linear Moderate: y = -0.5x + 1 (slope = -1/2)
     * Equation of line from (0,1) to (1,0.5)
     * 
     * Philosophy: Moderate emphasis on small denominations,
     * gentler slope provides more balanced distribution.
     * Good middle-ground for most tournaments.
     */
    object LinearModerate : ChipDistributionCurve() {
        override fun getValue(x: Double): Double = -0.5 * x + 1.0
        override val displayName = "Linear Moderate"
        override val description = "Moderate decline - balanced chip distribution"
    }
    
    /**
     * Bell Curve (Gaussian): Centered at x=0.5
     * 
     * Formula: y = exp(-((x - μ)² / (2σ²)))
     * Where μ = 0.5 (center), σ = 0.2 (spread)
     * 
     * Philosophy: Most chips in middle denominations for versatility,
     * fewer at extremes. Good for cash games and flexible betting.
     */
    object BellCurve : ChipDistributionCurve() {
        private const val MU = 0.5      // Center of bell curve
        private const val SIGMA = 0.2   // Standard deviation (controls width)
        
        override fun getValue(x: Double): Double {
            val exponent = -((x - MU).pow(2) / (2 * SIGMA.pow(2)))
            return exp(exponent)
        }
        override val displayName = "Bell Curve (Balanced)"
        override val description = "Balanced distribution - most chips in middle denominations"
    }
    
    /**
     * Positive Linear: y = x
     * Equation of line from (0,0) to (1,1)
     * 
     * Philosophy: More large chips, fewer small chips.
     * Good for late-stage tournaments with high blinds.
     */
    object PositiveLinear : ChipDistributionCurve() {
        override fun getValue(x: Double): Double = x
        override val displayName = "Linear (More Large Chips)"
        override val description = "Late-game focused - emphasizes large denominations"
    }
    
    /**
     * Exponential Decay: More extreme version of negative linear
     * y = e^(-3x)
     * 
     * Philosophy: Heavy emphasis on smallest chips, rapid dropoff.
     * Ideal for cash games requiring lots of change-making.
     */
    object ExponentialDecay : ChipDistributionCurve() {
        override fun getValue(x: Double): Double = exp(-3 * x)
        override val displayName = "Exponential (Cash Game)"
        override val description = "Heavy emphasis on small chips for cash games"
    }
    
    companion object {
        /**
         * Get all available curve types
         */
        fun getAllCurves(): List<ChipDistributionCurve> = listOf(
            LinearSteep,
            LinearModerate,
            BellCurve,
            PositiveLinear,
            ExponentialDecay
        )
        
        /**
         * Get curve by display name
         */
        fun getCurveByName(name: String): ChipDistributionCurve? {
            return getAllCurves().find { it.displayName == name }
        }
    }
}

/**
 * Result of chip distribution optimization
 */
data class ChipDistributionResult(
    val denominations: List<Int>,           // Selected chip values
    val quantities: List<Int>,              // Quantity of each denomination
    val fitScore: Double,                   // 0-1, where 1 = perfect fit to curve
    val totalChips: Int,                    // Total number of physical chips
    val totalValue: Int,                    // Total value (should match target)
    val curveUsed: ChipDistributionCurve    // Which curve was used
)

/**
 * Point on the curve representing a chip denomination
 */
data class ChipPoint(
    val value: Int,           // Denomination value
    val normalizedX: Double,  // Position on curve (0-1)
    val quantity: Int,        // Number of chips
    val normalizedY: Double   // Normalized quantity (0-1)
)
