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

    /** Stable id for saving the choice; never shown, never renamed (the display name may change). */
    abstract val id: String
    
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
        override val id = "linear_steep"
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
        override val id = "linear_moderate"
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
        override val id = "bell"
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
        override val id = "positive_linear"
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
        override val id = "exponential"
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

        /** The curve saved as [id], or null for an unknown id. */
        fun fromId(id: String): ChipDistributionCurve? = getAllCurves().find { it.id == id }
    }
}

/**
 * An exact chip distribution: Σ denominations[i] × quantities[i] == totalValue.
 */
data class ChipDistributionResult(
    val denominations: List<Int>,           // Selected chip values, ascending
    val quantities: List<Int>,              // Quantity of each denomination (each ≥ 1)
    val fitScore: Double,                   // 0-1, where 1 = perfect fit to curve
    val totalChips: Int,                    // Total number of physical chips
    val totalValue: Int,                    // Total value (equals the starting stack)
    val curveUsed: ChipDistributionCurve    // Which curve was used
)

/**
 * What [ChipDistributionOptimizer.optimize] returns: an exact distribution or the reason there
 * isn't one. Every [Failure] carries a one-line, user-facing [Failure.message].
 */
sealed interface ChipDistributionOutcome {

    data class Success(
        val distribution: ChipDistributionResult,
        /** The smallest chip as asked for, before snapping to a standard chip value. */
        val requestedSmallestChip: Int
    ) : ChipDistributionOutcome {
        val usedSmallestChip: Int get() = distribution.denominations.first()
        val smallestChipAdjusted: Boolean get() = usedSmallestChip != requestedSmallestChip

        /** One-line note for the user when an input was adjusted, else null. */
        val note: String?
            get() = if (smallestChipAdjusted) {
                "$requestedSmallestChip isn't a standard chip, so the smallest chip used is $usedSmallestChip."
            } else {
                null
            }
    }

    sealed interface Failure : ChipDistributionOutcome {
        val message: String
    }

    enum class Field { STARTING_CHIPS, SMALLEST_CHIP, DENOMINATION_COUNT }

    /** A number outside its allowed range (all must be at least 1). */
    data class InvalidInput(val input: Field, val value: Int) : Failure {
        override val message: String
            get() = when (input) {
                Field.STARTING_CHIPS -> "Starting chips must be at least 1."
                Field.SMALLEST_CHIP -> "Smallest chip must be at least 1."
                Field.DENOMINATION_COUNT -> "Use at least 1 chip denomination."
            }
    }

    /** The stack is smaller than one smallest chip. */
    data class StackSmallerThanSmallestChip(
        val startingChips: Int,
        val requestedSmallestChip: Int,
        val usedSmallestChip: Int
    ) : Failure {
        override val message: String
            get() = "Starting chips ($startingChips) must be at least the smallest chip ($usedSmallestChip)."
    }

    /**
     * No mix of the allowed chips adds up to the stack, because every allowed chip is a multiple
     * of [unit] and the stack isn't.
     */
    data class StackNotReachable(
        val startingChips: Int,
        val requestedSmallestChip: Int,
        val usedSmallestChip: Int,
        val unit: Int
    ) : Failure {
        val nearestBelow: Int get() = startingChips - startingChips % unit
        val nearestAbove: Int get() = nearestBelow + unit

        override val message: String
            get() = "$startingChips can't be made from chips of $usedSmallestChip and up. " +
                "Try $nearestBelow or $nearestAbove."
    }

    /**
     * The stack is a reachable amount, but no breakdown with at least one of each chip fits the
     * curve's constraints (the linear curves need counts that never rise with chip value).
     */
    data class NoExactBreakdown(
        val startingChips: Int,
        val requestedSmallestChip: Int,
        val usedSmallestChip: Int,
        val curve: ChipDistributionCurve
    ) : Failure {
        override val message: String
            get() = "No exact breakdown of $startingChips with chips of $usedSmallestChip and up fits " +
                "${curve.displayName}. Try another curve or a multiple of $usedSmallestChip."
    }
}

/**
 * A stack from [ChipDistributionOptimizer.optimizeWithinCaps]: exact, and within what you own.
 *
 * @property shapeRelaxed the caps left no stack whose counts never rise with chip value, which the
 *   linear curves ask for, so the counts don't follow the curve's shape exactly.
 * @property moreChipsThanAsked no stack of the requested number of chip values fitted under the
 *   caps, so this one uses more.
 */
data class CappedStack(
    val distribution: ChipDistributionResult,
    val shapeRelaxed: Boolean = false,
    val moreChipsThanAsked: Boolean = false
)
