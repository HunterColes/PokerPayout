package com.huntercoles.pokerpayout.core.utils

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * The search behind [BlindFittingAlgorithm]. All values are counted in smallest chips, so level 0 is 1
 * and the last level is the target (starting stack / smallest chip).
 *
 * A step from k may go to any whole number in ceil(1.3k)..2k. Going forward from 1, the reachable values
 * at each level form a contiguous range, as do the values that can still reach the target going backward
 * (the predecessors of m are ceil(m / 2)..floor(m / 1.3)). Their intersection is each level's window.
 */
internal object BlindLadderSearch {

    /** Mantissas (trailing zeros stripped) of the values poker players expect: 1, 1.2, 1.5, 2, 2.5 ... x 10^k. */
    private val NICE_MANTISSAS = setOf(1L, 2L, 3L, 4L, 5L, 6L, 8L, 12L, 15L, 25L, 75L)

    /** Cost of a two-digit value ending in 5 (e.g. 350, 5,500), or a lone 7 or 9. */
    private const val ROUGH_VALUE_PENALTY = 0.05

    /** Cost of any other value (e.g. 275, 1,725). Comparable to a 65% miss of the ideal curve. */
    private const val ODD_VALUE_PENALTY = 0.25

    /** Windows this narrow (in chips) are searched exhaustively. */
    private const val EXHAUSTIVE_WINDOW = 80L

    /** Off the exhaustive path, candidate values stay within this factor of the ideal curve. */
    private const val CANDIDATE_SPREAD = 1.45

    /** 1.3 = 13 / 10, kept exact. */
    private const val GROWTH_NUMERATOR = 13L
    private const val GROWTH_DENOMINATOR = 10L

    private const val DECIMAL = 10L
    private const val TWO_DIGITS = 100L
    private const val ROUND_FIVE = 5L

    /** Keeps the window arithmetic far from overflow; no realistic stack is this many chips. */
    private const val CAP = 1L shl 40

    /** Values the last of [numRounds] levels can take: the slowest (ceil 1.3x) to the fastest (2x) climb. */
    fun feasibleTargets(numRounds: Int): LongRange {
        var low = 1L
        var high = 1L
        repeat(numRounds - 1) {
            low = ceilTimes13(low).coerceAtMost(CAP)
            high = (high * 2).coerceAtMost(CAP)
        }
        return low..high
    }

    /** Per level, the values that lie on at least one in-band path from 1 to [target]. */
    fun windows(numRounds: Int, target: Long): List<LongRange> {
        val forwardLow = LongArray(numRounds) { 1L }
        val forwardHigh = LongArray(numRounds) { 1L }
        for (i in 1 until numRounds) {
            forwardLow[i] = ceilTimes13(forwardLow[i - 1]).coerceAtMost(CAP)
            forwardHigh[i] = (forwardHigh[i - 1] * 2).coerceAtMost(target)
        }
        val backwardLow = LongArray(numRounds) { target }
        val backwardHigh = LongArray(numRounds) { target }
        for (i in numRounds - 2 downTo 0) {
            backwardLow[i] = (backwardLow[i + 1] + 1) / 2
            backwardHigh[i] = backwardHigh[i + 1] * GROWTH_DENOMINATOR / GROWTH_NUMERATOR
        }
        return List(numRounds) { i ->
            maxOf(forwardLow[i], backwardLow[i])..minOf(forwardHigh[i], backwardHigh[i])
        }
    }

    /**
     * Lowest-cost in-band path: squared log distance from the ideal curve r^i plus [valuePenalty].
     * Null if the candidate values don't connect (then [greedyLadder] is used).
     */
    fun bestLadder(
        numRounds: Int,
        smallestChip: Int,
        target: Long,
        windows: List<LongRange>,
        growthRate: Double
    ): List<Long>? {
        val logRate = ln(growthRate)
        var costs = mapOf(1L to 0.0)
        val parents = ArrayList<Map<Long, Long>>(numRounds).apply { add(emptyMap()) }
        for (level in 1 until numRounds) {
            val values = if (level == numRounds - 1) {
                listOf(target)
            } else {
                candidates(level, windows[level], smallestChip, growthRate)
            }
            val nextCosts = HashMap<Long, Double>()
            val links = HashMap<Long, Long>()
            values.forEach { value ->
                val parent = cheapestParent(value, costs) ?: return@forEach
                val deviation = ln(value.toDouble()) - logRate * level
                nextCosts[value] = costs.getValue(parent) + deviation * deviation + valuePenalty(value * smallestChip)
                links[value] = parent
            }
            costs = nextCosts
            parents += links
        }
        return if (target in costs) tracePath(target, parents) else null
    }

    private fun cheapestParent(value: Long, costs: Map<Long, Double>): Long? {
        val parents = (value + 1) / 2..value * GROWTH_DENOMINATOR / GROWTH_NUMERATOR
        return costs.entries.filter { it.key in parents }.minByOrNull { it.value }?.key
    }

    private fun tracePath(target: Long, parents: List<Map<Long, Long>>): List<Long> {
        val path = ArrayList<Long>(parents.size)
        var value = target
        for (level in parents.lastIndex downTo 1) {
            path += value
            value = parents[level].getValue(value)
        }
        path += value
        return path.reversed()
    }

    /** The whole window if it's small, else nice values near the curve plus the window's ends. */
    private fun candidates(level: Int, window: LongRange, smallestChip: Int, growthRate: Double): List<Long> {
        if (window.last - window.first <= EXHAUSTIVE_WINDOW) return window.toList()
        val ideal = growthRate.pow(level)
        val result = sortedSetOf(
            window.first,
            window.last,
            floor(ideal).toLong().coerceIn(window),
            ceil(ideal).toLong().coerceIn(window)
        )
        val near = maxOf(window.first, (ideal / CANDIDATE_SPREAD).toLong())..
            minOf(window.last, (ideal * CANDIDATE_SPREAD).toLong() + 1)
        var scale = 1L
        while (scale <= near.last * smallestChip) {
            NICE_MANTISSAS.map { it * scale }
                .filter { it % smallestChip == 0L && it / smallestChip in near }
                .forEach { result += it / smallestChip }
            scale *= DECIMAL
        }
        return result.toList()
    }

    /**
     * Always succeeds on a feasible configuration: each chosen value can still reach the target (it is
     * in its window), so some in-band successor of it can too.
     */
    fun greedyLadder(
        numRounds: Int,
        smallestChip: Int,
        target: Long,
        windows: List<LongRange> = windows(numRounds, target),
        growthRate: Double = target.toDouble().pow(1.0 / (numRounds - 1))
    ): List<Long> {
        val path = arrayListOf(1L)
        for (level in 1 until numRounds) {
            val previous = path.last()
            val low = maxOf(ceilTimes13(previous), windows[level].first)
            val high = minOf(previous * 2, windows[level].last)
            check(low <= high) { "no in-band successor of $previous at level $level" }
            val ideal = growthRate.pow(level)
            path += (low..high).minBy { value -> abs(ln(value / ideal)) + valuePenalty(value * smallestChip) }
        }
        check(path.last() == target) { "greedy ladder ended at ${path.last()}, not $target" }
        return path
    }

    /** 0 for values on a normal blind sheet, more for values players would find odd. */
    private fun valuePenalty(value: Long): Double {
        var mantissa = value
        while (mantissa >= DECIMAL && mantissa % DECIMAL == 0L) mantissa /= DECIMAL
        return when {
            mantissa in NICE_MANTISSAS -> 0.0
            mantissa < DECIMAL || (mantissa < TWO_DIGITS && mantissa % ROUND_FIVE == 0L) -> ROUGH_VALUE_PENALTY
            else -> ODD_VALUE_PENALTY
        }
    }

    /** ceil(1.3 x k) in exact integer arithmetic. */
    private fun ceilTimes13(k: Long): Long = (k * GROWTH_NUMERATOR + GROWTH_DENOMINATOR - 1) / GROWTH_DENOMINATOR
}
