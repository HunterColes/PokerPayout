package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
import java.util.Locale
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

/** Why no in-band ladder exists for a configuration. */
enum class LadderProblem {
    /** Blinds would have to more than double at some level: too few rounds or too big a stack. */
    TOO_STEEP,

    /** Blinds would have to rise by less than 1.3x (or repeat) somewhere: too many rounds or too small a stack. */
    TOO_FLAT,

    /** The starting stack isn't a whole number of smallest chips. */
    STACK_NOT_MULTIPLE_OF_CHIP
}

/**
 * Thrown when no ladder satisfies the documented rules. The message says which way to adjust
 * ("Try more rounds or smaller starting chips" / "Try fewer rounds or larger starting chips").
 */
class BlindLadderException(val problem: LadderProblem, message: String) : IllegalArgumentException(message)

/**
 * Builds a small-blind ladder from the smallest chip to the starting stack.
 *
 * Every ladder it returns satisfies the documented rules exactly (PP-020):
 * - the first level is the smallest chip and the last is the starting stack;
 * - every level is a multiple of the smallest chip;
 * - every step grows by at least [BlindStructureConstants.MIN_BLIND_GROWTH_RATE] (1.3x) and at most
 *   [BlindStructureConstants.MAX_BLIND_GROWTH_RATE] (2.0x), so levels also strictly increase.
 *
 * Which configurations have such a ladder is exact integer arithmetic (see [BlindLadderSearch]); the
 * rest are rejected with a [BlindLadderException]. Among the valid ladders it picks the one closest (in
 * log space) to the ideal geometric curve smallestChip x r^i, r = (startingChips / smallestChip)^(1 / (n - 1)),
 * preferring values players expect on a blind sheet (300 and 1,500 over 275 and 1,725).
 */
object BlindFittingAlgorithm {

    /**
     * Fits blinds to the band and returns fitted values with quality score.
     *
     * @throws BlindLadderException if no in-band ladder exists, saying which way to adjust
     * @throws IllegalArgumentException for impossible inputs (fewer than 2 rounds, non-positive chip,
     *   stack below the smallest chip)
     */
    fun fitBlinds(
        numRounds: Int,
        smallestChip: Int,
        startingChips: Int
    ): BlindFittingResult {
        require(numRounds >= 2) { "Must have at least 2 rounds" }
        require(smallestChip > 0) { "Smallest chip must be positive" }
        require(startingChips >= smallestChip) { "Starting chips must be >= smallest chip" }

        val growthRate = calculateGrowthRate(numRounds, smallestChip, startingChips)
        problemWith(numRounds, smallestChip, startingChips, growthRate)?.let { throw it }

        val target = (startingChips / smallestChip).toLong()
        val windows = BlindLadderSearch.windows(numRounds, target)
        val ladder = BlindLadderSearch.bestLadder(numRounds, smallestChip, target, windows, growthRate)
            ?: BlindLadderSearch.greedyLadder(numRounds, smallestChip, target, windows, growthRate)
        val blinds = ladder.map { (it * smallestChip).toInt() }

        return BlindFittingResult(
            blinds = blinds,
            fitScore = calculateFitScore(blinds, growthRate),
            calculatedGrowthRate = growthRate
        )
    }

    /** True when [fitBlinds] would return a ladder for this configuration. */
    fun isFeasible(numRounds: Int, smallestChip: Int, startingChips: Int): Boolean =
        numRounds >= 2 && smallestChip > 0 && startingChips >= smallestChip &&
            startingChips % smallestChip == 0 &&
            (startingChips / smallestChip).toLong() in BlindLadderSearch.feasibleTargets(numRounds)

    /**
     * Starting stacks that give an in-band ladder of [numRounds] levels from [smallestChip], or null if
     * none fit in an Int. Every multiple of [smallestChip] in the range is feasible.
     */
    fun feasibleStackRange(numRounds: Int, smallestChip: Int): LongRange? {
        val targets = if (numRounds >= 2 && smallestChip > 0) BlindLadderSearch.feasibleTargets(numRounds) else null
        val low = targets?.let { it.first * smallestChip }
        return if (targets == null || low == null || low > Int.MAX_VALUE) {
            null
        } else {
            low..minOf(targets.last, Int.MAX_VALUE.toLong() / smallestChip) * smallestChip
        }
    }

    private fun problemWith(
        numRounds: Int,
        smallestChip: Int,
        startingChips: Int,
        growthRate: Double
    ): BlindLadderException? {
        val targets = BlindLadderSearch.feasibleTargets(numRounds)
        val target = (startingChips / smallestChip).toLong()
        val minRate = BlindStructureConstants.MIN_BLIND_GROWTH_RATE
        val maxRate = BlindStructureConstants.MAX_BLIND_GROWTH_RATE
        return when {
            startingChips % smallestChip != 0 -> {
                val below = startingChips / smallestChip * smallestChip
                BlindLadderException(
                    LadderProblem.STACK_NOT_MULTIPLE_OF_CHIP,
                    "Starting chips %d aren't a multiple of the smallest chip %d. Try %d or %d."
                        .format(Locale.ROOT, startingChips, smallestChip, below, below + smallestChip)
                )
            }
            target > targets.last -> BlindLadderException(
                LadderProblem.TOO_STEEP,
                "Calculated growth rate %.3f is too high (max: %.2f). Try more rounds or smaller starting chips."
                    .format(Locale.ROOT, growthRate, maxRate)
            )
            growthRate < minRate -> BlindLadderException(
                LadderProblem.TOO_FLAT,
                "Calculated growth rate %.3f is too low (min: %.2f). Try fewer rounds or larger starting chips."
                    .format(Locale.ROOT, growthRate, minRate)
            )
            target < targets.first -> BlindLadderException(
                LadderProblem.TOO_FLAT,
                (
                    "Calculated growth rate %.3f is too low (min: %.2f) to climb in whole %d-chip steps " +
                        "without a level rising by less than %.1fx. Try fewer rounds or larger starting chips."
                    ).format(Locale.ROOT, growthRate, minRate, smallestChip, minRate)
            )
            else -> null
        }
    }

    /**
     * Calculates the required exponential growth rate to go from smallest chip
     * to starting chips over the given number of rounds.
     *
     * Formula: r = (startingChips / smallestChip)^(1 / (numRounds - 1))
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
     * Calculates fit quality score using root mean square error in log space.
     * Score = 1 - RMSE, where RMSE is computed on log-transformed values.
     * Higher scores (closer to 1.0) indicate better fit to exponential curve.
     */
    private fun calculateFitScore(
        blinds: List<Int>,
        calculatedGrowthRate: Double
    ): Double {
        if (blinds.size < 2) return 1.0

        val firstBlind = blinds.first().toDouble()
        var sumSquaredError = 0.0
        for (i in blinds.indices) {
            val expectedLog = ln(firstBlind * calculatedGrowthRate.pow(i.toDouble()))
            val error = ln(blinds[i].toDouble()) - expectedLog
            sumSquaredError += error * error
        }
        val rmse = sqrt(sumSquaredError / blinds.size)
        return (1.0 - rmse).coerceIn(0.0, 1.0)
    }
}
