package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
import kotlin.math.abs
import kotlin.math.ln

/** A one-tap change that turns an invalid blind setup into a valid one. */
sealed interface BlindSetupFix {
    val label: String

    data class UseRoundLength(val minutes: Int, val levels: Int) : BlindSetupFix {
        override val label: String get() = "Use $minutes-min rounds ($levels levels)"
    }

    data class UseStartingChips(val chips: Int) : BlindSetupFix {
        override val label: String get() = "Use ${"%,d".format(chips)} starting chips"
    }
}

enum class BlindSetupProblemKind {
    TOO_FEW_ROUNDS,
    UNEVEN_ROUNDS,
    STACK_TOO_SMALL,
    STACK_NOT_MULTIPLE_OF_CHIP,
    TOO_STEEP,
    TOO_FLAT
}

/** Why a blind setup can't be played, in plain words, and the nearest setups that can. */
data class BlindSetupProblem(
    val kind: BlindSetupProblemKind,
    val explanation: String,
    val fixes: List<BlindSetupFix>
)

/**
 * Explains invalid blind setups (PP-020) and finds the nearest valid round length or starting stack.
 *
 * A setup is valid when the duration is a whole number of at least two rounds and
 * [BlindFittingAlgorithm] can build an in-band ladder from the smallest chip to the starting stack.
 */
object BlindSetupAdvisor {

    /** Round lengths offered as fixes: multiples of this many minutes. */
    private const val ROUND_STEP_MINUTES = 5

    /** Mantissas of the stacks offered as fixes: 1,000, 1,200, 1,500, 2,000, 2,500, 3,000 ... */
    private val NICE_STACK_MANTISSAS = listOf(10, 12, 15, 20, 25, 30, 40, 50, 60, 75, 80)

    private const val PERCENT = 100
    private const val DECIMAL = 10L
    private const val MINUTES_PER_HOUR = 60

    /** "30" for the documented 1.3x minimum step. */
    private val MIN_GROWTH_PERCENT = ((BlindStructureConstants.MIN_BLIND_GROWTH_RATE - 1) * PERCENT).toInt()

    /** Null when the setup is valid. */
    fun check(
        durationMinutes: Int,
        roundLengthMinutes: Int,
        smallestChip: Int,
        startingChips: Int
    ): BlindSetupProblem? {
        if (durationMinutes <= 0 || roundLengthMinutes <= 0 || smallestChip <= 0) {
            return BlindSetupProblem(
                BlindSetupProblemKind.TOO_FEW_ROUNDS,
                "Duration, round length and smallest chip must all be more than zero.",
                emptyList()
            )
        }
        val rounds = durationMinutes / roundLengthMinutes
        val chip = "%,d".format(smallestChip)
        val stack = "%,d".format(startingChips)
        val duration = describeDuration(durationMinutes)
        val roundFixes = { direction: Direction ->
            roundLengthFixes(durationMinutes, roundLengthMinutes, smallestChip, startingChips, direction)
        }
        return when {
            rounds < 2 -> BlindSetupProblem(
                BlindSetupProblemKind.TOO_FEW_ROUNDS,
                "$roundLengthMinutes-minute rounds leave fewer than 2 levels in $duration.",
                roundFixes(Direction.SHORTER)
            )
            durationMinutes % roundLengthMinutes != 0 -> BlindSetupProblem(
                BlindSetupProblemKind.UNEVEN_ROUNDS,
                "$duration doesn't divide into $roundLengthMinutes-minute rounds: that's $rounds rounds " +
                    "with ${durationMinutes % roundLengthMinutes} minutes left over.",
                roundFixes(Direction.EITHER)
            )
            startingChips <= smallestChip -> BlindSetupProblem(
                BlindSetupProblemKind.STACK_TOO_SMALL,
                "Starting chips ($stack) must be more than the smallest chip ($chip).",
                stackFixes(rounds, smallestChip, startingChips)
            )
            startingChips % smallestChip != 0 -> BlindSetupProblem(
                BlindSetupProblemKind.STACK_NOT_MULTIPLE_OF_CHIP,
                "Starting chips ($stack) can't be made from $chip chips. Use a multiple of $chip.",
                stackFixes(rounds, smallestChip, startingChips)
            )
            BlindFittingAlgorithm.isFeasible(rounds, smallestChip, startingChips) -> null
            isTooSteep(rounds, smallestChip, startingChips) ->
                BlindSetupProblem(
                    BlindSetupProblemKind.TOO_STEEP,
                    "$rounds levels can't climb from $chip to $stack without the blinds more than " +
                        "doubling at some level. Use more, shorter rounds or a smaller starting stack.",
                    roundFixes(Direction.SHORTER) + stackFixes(rounds, smallestChip, startingChips)
                )
            else -> BlindSetupProblem(
                BlindSetupProblemKind.TOO_FLAT,
                "$rounds levels are too many to climb from $chip to $stack: some level would rise by " +
                    "less than $MIN_GROWTH_PERCENT%. Use fewer, longer rounds or a bigger starting stack.",
                roundFixes(Direction.LONGER) + stackFixes(rounds, smallestChip, startingChips)
            )
        }
    }

    /** Above the fastest (doubling) climb; otherwise it's below the slowest, or too many levels for any. */
    private fun isTooSteep(rounds: Int, smallestChip: Int, startingChips: Int): Boolean {
        val range = BlindFittingAlgorithm.feasibleStackRange(rounds, smallestChip) ?: return false
        return startingChips > range.last
    }

    private enum class Direction { SHORTER, LONGER, EITHER }

    /** The valid round lengths nearest the current one (at most one on each side). */
    private fun roundLengthFixes(
        durationMinutes: Int,
        roundLengthMinutes: Int,
        smallestChip: Int,
        startingChips: Int,
        direction: Direction
    ): List<BlindSetupFix> {
        val valid = (ROUND_STEP_MINUTES..durationMinutes / 2 step ROUND_STEP_MINUTES).filter { minutes ->
            durationMinutes % minutes == 0 &&
                BlindFittingAlgorithm.isFeasible(durationMinutes / minutes, smallestChip, startingChips)
        }
        val shorter = valid.lastOrNull { it < roundLengthMinutes }
        val longer = valid.firstOrNull { it > roundLengthMinutes }
        val picked = when (direction) {
            Direction.SHORTER -> listOfNotNull(shorter ?: longer)
            Direction.LONGER -> listOfNotNull(longer ?: shorter)
            Direction.EITHER -> listOfNotNull(shorter, longer)
        }
        return picked.map { BlindSetupFix.UseRoundLength(it, durationMinutes / it) }
    }

    /** The valid starting stack nearest the current one, preferring round numbers. */
    private fun stackFixes(rounds: Int, smallestChip: Int, startingChips: Int): List<BlindSetupFix> {
        val range = BlindFittingAlgorithm.feasibleStackRange(rounds, smallestChip) ?: return emptyList()
        val nice = niceStacks(range.last).filter { it in range && it % smallestChip == 0L }
        val wanted = startingChips.coerceAtLeast(1).toDouble()
        val best = nice.minByOrNull { abs(ln(it / wanted)) }
            ?: listOf(range.first, range.last).minBy { abs(ln(it / wanted)) }
        return listOf(BlindSetupFix.UseStartingChips(best.toInt()))
    }

    private fun niceStacks(upTo: Long): List<Long> {
        val result = mutableListOf<Long>()
        var scale = 1L
        while (scale <= upTo) {
            NICE_STACK_MANTISSAS.forEach { result += it * scale }
            scale *= DECIMAL
        }
        return result
    }

    private fun describeDuration(minutes: Int): String = when {
        minutes % MINUTES_PER_HOUR != 0 -> "$minutes minutes"
        minutes == MINUTES_PER_HOUR -> "1 hour"
        else -> "${minutes / MINUTES_PER_HOUR} hours"
    }
}
