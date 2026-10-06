package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.design.ChipDenominations
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Picks chip denominations and per-player counts for a starting stack.
 *
 * The answer is exact: counts × denominations always add up to the starting stack. Inputs that
 * have no exact answer come back as a [ChipDistributionOutcome.Failure] that says why. Nothing
 * here throws for any Int input.
 *
 * How it works:
 * 1. The smallest chip snaps to the nearest standard chip value (ties go to the smaller chip).
 * 2. The candidate denominations are the smallest chip plus every standard chip above it that is
 *    at most a third of the stack.
 * 3. Every set of `denominationCount` candidates that contains the smallest chip is tried. If no
 *    set of that size has an exact answer, smaller sets are tried.
 * 4. For each set, the curve gives an ideal real-valued count per denomination, scaled so the
 *    ideal counts are worth exactly the stack. An exact integer search ([ExactCountSearch]) then
 *    finds the counts closest to that ideal (least squares), with at least one chip of each
 *    denomination, and non-increasing counts for the linear curves.
 * 5. The set with the best fit score wins, where a total of 40–80 chips doubles the score.
 *
 * Cost: at most C(11, 5) = 462 denomination sets. Each set's search is branch-and-bound with an
 * exact feasibility table, so it never explores a dead end. A per-call node budget caps the time
 * spent improving answers that are already exact. It can trim optimality on extreme inputs
 * (stacks in the millions with a 1-chip), but never exactness.
 */
object ChipDistributionOptimizer {

    /** Standard chip values, ascending. */
    val STANDARD_DENOMINATIONS: List<Int> = ChipDenominations.ALL_CHIPS.map { it.value }.sorted()

    private const val TARGET_TOTAL_CHIPS_FOR_CALCULATION = 60.0
    private val PREFERRED_CHIP_RANGE = 40..80
    private const val PREFERRED_RANGE_FIT_BOOST = 2.0

    /**
     * Search nodes one [optimize] call may spend improving answers that are already exact.
     * Finding the first exact answer for each set doesn't count against it.
     */
    private const val NODE_BUDGET_PER_CALL = 200_000

    /**
     * Find the chip distribution for one player's starting stack.
     *
     * @param targetValue Starting stack (total chip value per player), at least 1.
     * @param smallestChip Smallest chip in play. Non-standard values snap to the nearest standard chip.
     * @param denominationCount How many chip denominations to use. Fewer come back when the stack
     *   can't support that many.
     * @param curve The shape the counts should follow.
     */
    @Suppress("ReturnCount") // one early return per typed failure, in the order they are checked
    fun optimize(
        targetValue: Int,
        smallestChip: Int,
        denominationCount: Int = 5,
        curve: ChipDistributionCurve
    ): ChipDistributionOutcome {
        if (targetValue < 1) {
            return ChipDistributionOutcome.InvalidInput(ChipDistributionOutcome.Field.STARTING_CHIPS, targetValue)
        }
        if (smallestChip < 1) {
            return ChipDistributionOutcome.InvalidInput(ChipDistributionOutcome.Field.SMALLEST_CHIP, smallestChip)
        }
        if (denominationCount < 1) {
            return ChipDistributionOutcome.InvalidInput(ChipDistributionOutcome.Field.DENOMINATION_COUNT, denominationCount)
        }

        val smallest = snapToStandardDenomination(smallestChip)
        if (targetValue < smallest) {
            return ChipDistributionOutcome.StackSmallerThanSmallestChip(targetValue, smallestChip, smallest)
        }

        // The largest chip should be no more than a third of the stack, but the smallest chip is
        // always allowed (e.g. 100 with a 50 smallest chip is 2 × 50).
        val maxDenomination = targetValue / 3
        val available = STANDARD_DENOMINATIONS.filter { it == smallest || (it in (smallest + 1)..maxDenomination) }

        val unit = available.fold(0) { acc, d -> gcd(acc, d) }
        if (targetValue % unit != 0) {
            return ChipDistributionOutcome.StackNotReachable(targetValue, smallestChip, smallest, unit)
        }

        val budget = SearchBudget(NODE_BUDGET_PER_CALL)
        val larger = available.drop(1)
        val maxCount = minOf(denominationCount, available.size)
        for (count in maxCount downTo 1) {
            var best: ChipDistributionResult? = null
            var bestScore = -1.0
            forEachCombination(larger, count - 1) { combo ->
                val denominations = IntArray(count)
                denominations[0] = smallest
                for (i in combo.indices) denominations[i + 1] = combo[i]
                val result = solveForDenominations(targetValue, denominations, curve, budget)
                    ?: return@forEachCombination
                val score = if (result.totalChips in PREFERRED_CHIP_RANGE) {
                    result.fitScore * PREFERRED_RANGE_FIT_BOOST
                } else {
                    result.fitScore
                }
                if (score > bestScore) {
                    best = result
                    bestScore = score
                }
            }
            best?.let { return ChipDistributionOutcome.Success(it, requestedSmallestChip = smallestChip) }
        }
        return ChipDistributionOutcome.NoExactBreakdown(targetValue, smallestChip, smallest, curve)
    }

    /** Nearest standard chip value to [chip]. Ties go to the smaller chip. */
    fun snapToStandardDenomination(chip: Int): Int =
        STANDARD_DENOMINATIONS.minWith(compareBy<Int> { abs(it.toLong() - chip.toLong()) }.thenBy { it })

    /**
     * Calculate fit score for an existing distribution: 1.0 = counts (relative to the largest
     * count) exactly follow the curve, 0.0 = as far from it as possible.
     */
    fun calculateFitScoreForDistribution(
        denominations: List<Int>,
        quantities: List<Int>,
        curve: ChipDistributionCurve
    ): Double {
        require(denominations.size == quantities.size)

        val sortedPairs = denominations.zip(quantities).sortedBy { it.first }
        val sortedDenoms = sortedPairs.map { it.first }.toIntArray()
        val sortedQtys = sortedPairs.map { it.second }

        val idealY = idealCurveValues(sortedDenoms, curve)
        val maxQty = sortedQtys.maxOrNull()?.toDouble() ?: 1.0
        val normalizedQty = if (maxQty > 0.0) {
            DoubleArray(sortedQtys.size) { sortedQtys[it] / maxQty }
        } else {
            DoubleArray(sortedQtys.size)
        }
        return calculateFitScore(idealY, normalizedQty)
    }

    /**
     * Exact counts for one ascending denomination set, or null when the set can't make
     * [targetValue] exactly under the constraints.
     */
    private fun solveForDenominations(
        targetValue: Int,
        denominations: IntArray,
        curve: ChipDistributionCurve,
        budget: SearchBudget
    ): ChipDistributionResult? {
        val idealY = idealCurveValues(denominations, curve)
        val ideal = idealQuantities(targetValue, denominations, idealY)
        val quantities = ExactCountSearch(denominations, ideal, isMonotone(curve), budget)
            .solve(targetValue.toLong())
            ?: return null

        val maxQty = quantities.max().toDouble()
        return ChipDistributionResult(
            denominations = denominations.toList(),
            quantities = quantities.toList(),
            fitScore = calculateFitScore(idealY, DoubleArray(quantities.size) { quantities[it] / maxQty }),
            totalChips = quantities.sum(),
            totalValue = targetValue,
            curveUsed = curve
        )
    }

    // ------------------------------------------------------------ chips you own (PP-033)

    /**
     * Most smallest-chip units a capped stack may be worth (the stack divided by the chips' common
     * divisor). The reachability tables are bitsets this long; a home game never comes close
     * (5,000 in 25s is 200 units).
     */
    const val MAX_CAPPED_UNITS = 200_000

    /** Nodes one monotone search under caps may visit before the shape is relaxed instead. */
    private const val DEAD_END_BUDGET_PER_SET = 20_000

    /**
     * One player's stack from the chips you own: like [optimize], but the candidate chips are
     * [chips] (ascending, distinct; `chips[0]` is the smallest chip in play and always in the
     * stack) and a stack may hold at most `caps[i]` of `chips[i]` (what you own, shared between the
     * stacks you need). Chips with a cap of 0 are left out.
     *
     * The choice follows [optimize]'s rules: sets of up to [denominationCount] chips, the counts
     * closest to [curve]'s ideal, the best fit score winning (40–80 chips doubling it). Two hard
     * rules come first: the stack adds up exactly and stays under the caps. So when the caps leave
     * no stack that keeps a linear curve's never-rising counts, the shape is relaxed
     * ([CappedStack.shapeRelaxed]); and when no stack of up to [denominationCount] chips fits, more
     * chips are used ([CappedStack.moreChipsThanAsked]).
     *
     * @return null when no exact stack fits under the caps at all (see [canMakeWithinCaps]), or
     *   when the stack is worth more than [MAX_CAPPED_UNITS] units.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod") // one early return per impossible input, then one per pass
    fun optimizeWithinCaps(
        targetValue: Int,
        chips: IntArray,
        caps: IntArray,
        denominationCount: Int,
        curve: ChipDistributionCurve
    ): CappedStack? {
        val usable = usableChips(targetValue, chips, caps) ?: return null
        val budget = SearchBudget(NODE_BUDGET_PER_CALL)
        val linear = isMonotone(curve)
        val wanted = denominationCount.coerceAtLeast(1)
        val larger = usable.drop(1)
        fun bestOfSize(count: Int, monotone: Boolean): ChipDistributionResult? {
            var best: ChipDistributionResult? = null
            var bestScore = -1.0
            forEachCombination(larger, count - 1) { combo ->
                val denominations = IntArray(count)
                val subsetCaps = IntArray(count)
                denominations[0] = chips[0]
                subsetCaps[0] = caps[0]
                for (j in combo.indices) {
                    denominations[j + 1] = chips[combo[j]]
                    subsetCaps[j + 1] = caps[combo[j]]
                }
                val result = solveWithinCaps(targetValue, denominations, subsetCaps, curve, monotone, budget)
                    ?: return@forEachCombination
                val score = if (result.totalChips in PREFERRED_CHIP_RANGE) {
                    result.fitScore * PREFERRED_RANGE_FIT_BOOST
                } else {
                    result.fitScore
                }
                if (score > bestScore) {
                    best = result
                    bestScore = score
                }
            }
            return best
        }

        for (monotone in if (linear) listOf(true, false) else listOf(false)) {
            for (count in minOf(wanted, usable.size) downTo 1) {
                bestOfSize(count, monotone)?.let { return CappedStack(it, shapeRelaxed = linear && !it.isNonIncreasing()) }
            }
        }
        for (count in wanted + 1..usable.size) {
            bestOfSize(count, monotone = false)?.let {
                return CappedStack(it, shapeRelaxed = linear && !it.isNonIncreasing(), moreChipsThanAsked = true)
            }
        }
        return null
    }

    /**
     * Whether any exact stack worth [targetValue] fits under [caps]: at least one `chips[0]`, and up
     * to `caps[i]` of each `chips[i]`. Exactly when [optimizeWithinCaps] has an answer (for stacks
     * up to [MAX_CAPPED_UNITS] units), but much cheaper: one bounded-knapsack bitset pass.
     */
    fun canMakeWithinCaps(targetValue: Int, chips: IntArray, caps: IntArray): Boolean {
        val usable = usableChips(targetValue, chips, caps) ?: return false
        val unit = usable.fold(0) { acc, i -> gcd(acc, chips[i]) }
        val reach = CappedReach(
            d = IntArray(usable.size) { chips[usable[it]] },
            caps = IntArray(usable.size) { caps[usable[it]] },
            unit = unit.toLong(),
            maxAmount = targetValue.toLong(),
            atLeastOne = BooleanArray(usable.size) { it == 0 }
        )
        return reach.canMake(usable.size, targetValue.toLong())
    }

    /**
     * Indexes of the chips a stack may use (a cap of at least 1), or null when [targetValue] can't
     * be planned at all: bad input, no smallest chip, not a multiple of the chips' common divisor,
     * or more than [MAX_CAPPED_UNITS] of it.
     */
    private fun usableChips(targetValue: Int, chips: IntArray, caps: IntArray): List<Int>? {
        val wellFormed = targetValue >= 1 && chips.isNotEmpty() && chips.size == caps.size
        if (!wellFormed || caps[0] < 1) return null
        val usable = chips.indices.filter { caps[it] >= 1 }
        val unit = usable.fold(0) { acc, i -> gcd(acc, chips[i]) }
        return usable.takeIf { targetValue % unit == 0 && targetValue / unit <= MAX_CAPPED_UNITS }
    }

    private fun ChipDistributionResult.isNonIncreasing(): Boolean = quantities.zipWithNext().all { (a, b) -> a >= b }

    /** [solveForDenominations] under per-chip caps. */
    @Suppress("LongParameterList") // the search's inputs, passed through
    private fun solveWithinCaps(
        targetValue: Int,
        denominations: IntArray,
        caps: IntArray,
        curve: ChipDistributionCurve,
        monotone: Boolean,
        budget: SearchBudget
    ): ChipDistributionResult? {
        // An unreachable target fails the reachability check at once (solve() returns null)
        val unit = denominations.fold(0) { acc, d -> gcd(acc, d) }
        val idealY = idealCurveValues(denominations, curve)
        val ideal = idealQuantities(targetValue, denominations, idealY)
        val reach = CappedReach(
            d = denominations,
            caps = caps,
            unit = unit.toLong(),
            maxAmount = targetValue.toLong(),
            atLeastOne = BooleanArray(denominations.size) { true }
        )
        val limits = SearchLimits(caps, reach, deadEnds = if (monotone) SearchBudget(DEAD_END_BUDGET_PER_SET) else null)
        val quantities = ExactCountSearch(denominations, ideal, monotone, budget, limits).solve(targetValue.toLong())
            ?: return null

        val maxQty = quantities.max().toDouble()
        return ChipDistributionResult(
            denominations = denominations.toList(),
            quantities = quantities.toList(),
            fitScore = calculateFitScore(idealY, DoubleArray(quantities.size) { quantities[it] / maxQty }),
            totalChips = quantities.sum(),
            totalValue = targetValue,
            curveUsed = curve
        )
    }

    /**
     * Exact reachability under caps, as bitsets over amounts in [unit]s: table k holds every amount
     * the first k chips make with 1..caps[j] of chip j (0..caps[j] where [atLeastOne] is false).
     * Built with binary splitting, so a cap of c costs about log2(c) shifted ORs per table.
     */
    private class CappedReach(
        d: IntArray,
        caps: IntArray,
        private val unit: Long,
        maxAmount: Long,
        atLeastOne: BooleanArray
    ) {
        private val bits = maxAmount / unit + 1
        private val words = ((bits + WORD_BITS - 1) / WORD_BITS).toInt()
        private val tables = arrayOfNulls<LongArray>(d.size + 1)

        init {
            var table = LongArray(words).also { it[0] = 1L }
            tables[0] = table
            for (k in d.indices) {
                val step = d[k] / unit
                var optional = caps[k].toLong()
                if (atLeastOne[k]) {
                    table = shifted(table, step)
                    optional -= 1
                }
                var chunk = 1L
                while (optional > 0) {
                    val take = minOf(chunk, optional)
                    val shift = take * step
                    if (shift < bits) table = orShifted(table, shift)
                    optional -= take
                    chunk *= 2
                }
                tables[k + 1] = table
            }
        }

        /** Whether the first [k] chips make [amount] exactly. */
        fun canMake(k: Int, amount: Long): Boolean {
            val index = amount / unit
            val inTable = amount >= 0 && amount % unit == 0L && index < bits
            val table = tables[k]
            return inTable && table != null && (table[(index / WORD_BITS).toInt()] ushr (index % WORD_BITS).toInt()) and 1L == 1L
        }

        private fun shifted(source: LongArray, shift: Long): LongArray {
            val result = LongArray(words)
            if (shift >= bits) return result
            val wordShift = (shift / WORD_BITS).toInt()
            val bitShift = (shift % WORD_BITS).toInt()
            for (w in words - 1 downTo wordShift) {
                val from = w - wordShift
                var value = source[from] shl bitShift
                if (bitShift != 0 && from > 0) value = value or (source[from - 1] ushr (WORD_BITS.toInt() - bitShift))
                result[w] = value
            }
            return result
        }

        private fun orShifted(source: LongArray, shift: Long): LongArray {
            val moved = shifted(source, shift)
            for (w in moved.indices) moved[w] = moved[w] or source[w]
            return moved
        }

        private companion object {
            const val WORD_BITS = 64L
        }
    }

    private fun idealCurveValues(denominations: IntArray, curve: ChipDistributionCurve): DoubleArray {
        val min = denominations.first().toDouble()
        val max = denominations.last().toDouble()
        return DoubleArray(denominations.size) { i ->
            val x = if (min == max) 0.5 else (denominations[i] - min) / (max - min)
            curve.getValue(x).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Ideal real-valued count per denomination: proportional to the curve, scaled so the counts
     * are worth exactly [targetValue].
     */
    private fun idealQuantities(targetValue: Int, denominations: IntArray, idealY: DoubleArray): DoubleArray {
        val ySum = idealY.sum()
        val chipCounts = DoubleArray(idealY.size) { idealY[it] / ySum * TARGET_TOTAL_CHIPS_FOR_CALCULATION }
        var value = 0.0
        for (i in denominations.indices) value += denominations[i] * chipCounts[i]
        val scale = targetValue / value
        return DoubleArray(chipCounts.size) { chipCounts[it] * scale }
    }

    /** The linear curves require counts that never rise with chip value. */
    private fun isMonotone(curve: ChipDistributionCurve): Boolean =
        curve == ChipDistributionCurve.LinearSteep || curve == ChipDistributionCurve.LinearModerate

    /** RMS distance between normalized counts and the curve, mapped so 1.0 = perfect. */
    private fun calculateFitScore(idealY: DoubleArray, normalizedQty: DoubleArray): Double {
        if (idealY.isEmpty()) return 0.0
        var sumSqDist = 0.0
        for (i in idealY.indices) {
            val diff = idealY[i] - normalizedQty[i]
            sumSqDist += diff * diff
        }
        val rms = sqrt(sumSqDist / idealY.size)
        // Maximum possible RMS distance is sqrt(2).
        return (1.0 - rms / sqrt(2.0)).coerceIn(0.0, 1.0)
    }

    private class SearchBudget(var remaining: Int)

    /**
     * A capped search's limits: at most [caps] of each chip, [reach] (exact reachability of each
     * free prefix under them), and [deadEnds], the nodes it may visit at all, dead ends included
     * (null for no limit). Only the monotone search under caps can walk into dead ends, because
     * [reach] ignores the ordering.
     */
    private class SearchLimits(val caps: IntArray, val reach: CappedReach, val deadEnds: SearchBudget?)

    /**
     * Least-squares exact count search for one denomination set.
     *
     * Variables are q[0..n-1] for ascending denominations d. Constraints: Σ d[i]·q[i] = target,
     * every q[i] ≥ 1, and q[0] ≥ q[1] ≥ … when [monotone]. Objective: Σ (q[i] − ideal[i])².
     *
     * The search fixes q[n-1], q[n-2], …, q[1] in that order. q[0] then follows from the target.
     *
     * Feasibility of the still-free prefix q[0..k-1] is exact and O(1). With lower bound L on each
     * of them (L = 1, or L = q[k] when monotone), the prefix must make W = rem − L·P[k-1], where
     * P[j] = d[0] + … + d[j], from non-negative "steps":
     * - Non-monotone: one more chip of d[j] is a step of d[j].
     * - Monotone: q[j] = L + m[j] + … + m[k-1], so each step is a prefix sum P[j].
     * The step set always contains d[0]. So W is reachable iff W ≥ table[W mod d[0]], where the
     * table holds the smallest reachable amount in each residue class. The table is built with
     * the round-robin shortest-path algorithm in O(n·d[0]).
     *
     * Pruning uses two lower bounds on the cost of the free prefix:
     * - The continuous relaxation, which is convex in q[k]. It decides when to stop walking
     *   outward from the best real value of q[k].
     * - A tighter bound that keeps q[0] integral within its residue class. Σ_{j≥1} d[j]·q[j] is a
     *   multiple of gcd(d[1..k-1]), so d[0]·q[0] ≡ W modulo that gcd. This skips subtrees.
     */
    private class ExactCountSearch(
        private val d: IntArray,
        private val ideal: DoubleArray,
        private val monotone: Boolean,
        private val budget: SearchBudget,
        /** Per-chip limits ([optimizeWithinCaps]); null for none. */
        private val limits: SearchLimits? = null
    ) {
        private val caps = limits?.caps
        private val cappedReach = limits?.reach
        private val deadEnds = limits?.deadEnds
        private val n = d.size
        private val base = d[0].toLong()

        /** P[k] = d[0] + … + d[k], E[k] = Σ_{j≤k} d[j]·ideal[j], S[k] = Σ_{j≤k} d[j]². */
        private val prefixValue = LongArray(n)
        private val prefixIdealValue = DoubleArray(n)
        private val prefixSquares = DoubleArray(n)

        /** reach[k] = residue table for the first k free variables (k = 1..n). */
        private val reach = arrayOfNulls<LongArray>(n + 1)

        /**
         * With k free variables (k ≥ 2), q[0] ≡ residue (mod q0Period[k]) where
         * residue = (W / q0Divisor[k]) · q0Inverse[k]. A period of 1 means no restriction.
         */
        private val q0Period = LongArray(n + 1) { 1L }
        private val q0Divisor = LongArray(n + 1) { 1L }
        private val q0Inverse = LongArray(n + 1)

        /** Σ_{j=1}^{k-1} d[j]·ideal[j] and Σ_{j=1}^{k-1} d[j]², for the tighter bound. */
        private val othersIdealValue = DoubleArray(n + 1)
        private val othersSquares = DoubleArray(n + 1)

        private val current = LongArray(n)
        private var best: LongArray? = null
        private var bestCost = Double.POSITIVE_INFINITY
        private var budgetExhausted = false

        init {
            var p = 0L
            var e = 0.0
            var s = 0.0
            for (k in 0 until n) {
                p += d[k]
                e += d[k] * ideal[k]
                s += d[k].toDouble() * d[k]
                prefixValue[k] = p
                prefixIdealValue[k] = e
                prefixSquares[k] = s
            }

            var table = LongArray(d[0]) { if (it == 0) 0L else UNREACHABLE }
            reach[1] = table
            for (k in 1 until n) {
                table = addStep(table, if (monotone) prefixValue[k] else d[k].toLong())
                reach[k + 1] = table
            }

            var othersGcd = 0
            for (k in 2..n) {
                val j = k - 1
                othersGcd = gcd(othersGcd, d[j])
                othersIdealValue[k] = othersIdealValue[k - 1] + d[j] * ideal[j]
                othersSquares[k] = othersSquares[k - 1] + d[j].toDouble() * d[j]
                val h = gcd(d[0], othersGcd)
                val period = othersGcd / h
                q0Divisor[k] = h.toLong()
                q0Period[k] = period.toLong()
                if (period > 1) q0Inverse[k] = modInverse((d[0] / h) % period, period).toLong()
            }
        }

        fun solve(target: Long): IntArray? {
            // All n variables free, each at least 1.
            val capped = cappedReach?.canMake(n, target) ?: true
            if (!capped || !canMake(n, target - prefixValue[n - 1])) return null
            search(n - 1, target, 0.0, 1L)
            return best?.let { q -> IntArray(n) { q[it].toInt() } }
        }

        private fun canMake(freeVariables: Int, amount: Long): Boolean {
            if (amount < 0) return false
            return reach[freeVariables]!![(amount % base).toInt()] <= amount
        }

        // Branch and bound: every return and continue below is a pruning rule.
        @Suppress("ReturnCount", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
        private fun search(i: Int, remaining: Long, costSoFar: Double, lowerBound: Long) {
            if (i == 0) {
                // canMake() one level up guarantees divisibility and q[0] ≥ lowerBound.
                val q0 = remaining / base
                if (caps != null && q0 > caps[0]) return
                val cost = costSoFar + square(q0 - ideal[0])
                if (cost < bestCost) {
                    bestCost = cost
                    current[0] = q0
                    best = current.copyOf()
                }
                return
            }

            val di = d[i].toLong()
            val belowValue = prefixValue[i - 1]
            val uncapped = if (monotone) remaining / prefixValue[i] else (remaining - belowValue) / di
            val high = if (caps != null) minOf(uncapped, caps[i].toLong()) else uncapped
            val low = lowerBound
            if (high < low) return

            // Walk outward from the real optimum of q[i]. The relaxed bound is a convex quadratic
            // in q[i] centred there, so each direction stops at the first value that can't win.
            val center = ideal[i] + d[i] * (remaining - prefixIdealValue[i]) / prefixSquares[i]
            val start = clampRound(center, low, high)
            var down = start
            var up = start + 1
            while (down >= low || up <= high) {
                val downBound = if (down >= low) relaxedBound(costSoFar, i, down, remaining) else Double.POSITIVE_INFINITY
                val upBound = if (up <= high) relaxedBound(costSoFar, i, up, remaining) else Double.POSITIVE_INFINITY
                if (minOf(downBound, upBound) >= bestCost) return
                val qi = if (downBound <= upBound) down-- else up++

                if (best != null && --budget.remaining < 0) budgetExhausted = true
                if (deadEnds != null && --deadEnds.remaining < 0) budgetExhausted = true
                if (budgetExhausted) return

                val nextRemaining = remaining - di * qi
                val nextLowerBound = if (monotone) qi else 1L
                if (!canMake(i, nextRemaining - nextLowerBound * belowValue)) continue
                if (cappedReach != null && !cappedReach.canMake(i, nextRemaining)) continue
                val cost = costSoFar + square(qi - ideal[i])
                if (cost + residueBound(i, nextRemaining, nextLowerBound) >= bestCost) continue
                current[i] = qi
                search(i - 1, nextRemaining, cost, nextLowerBound)
                if (budgetExhausted) return
            }
        }

        /** Cost so far + q[i]'s term + continuous relaxation of q[0..i-1] making the rest. */
        private fun relaxedBound(costSoFar: Double, i: Int, qi: Long, remaining: Long): Double {
            val rest = (remaining - d[i] * qi) - prefixIdealValue[i - 1]
            return costSoFar + square(qi - ideal[i]) + rest * rest / prefixSquares[i - 1]
        }

        /**
         * Lower bound on the cost of k free variables q[0..k-1] making exactly [w], with
         * q[0] ≥ [low]. q[0] is kept integral within its residue class, and q[1..k-1] are relaxed
         * to real values.
         */
        @Suppress("ReturnCount") // one closed-form case per return
        private fun residueBound(k: Int, w: Long, low: Long): Double {
            if (k == 1) return square(w.toDouble() / base - ideal[0])
            val period = q0Period[k]
            if (period == 1L) return square(w - prefixIdealValue[k - 1]) / prefixSquares[k - 1]
            val divisor = q0Divisor[k]
            if (w < 0 || w % divisor != 0L) return Double.POSITIVE_INFINITY
            val residue = Math.floorMod((w / divisor) % period * q0Inverse[k], period)

            // f(q) = (q − ideal[0])² + (w − d[0]·q − E')² / S', minimized at qc over the reals.
            val sOthers = othersSquares[k]
            val eOthers = othersIdealValue[k]
            val qc = (ideal[0] * sOthers + base * (w - eOthers)) / (sOthers + base.toDouble() * base)
            var lower = residue + period * floor((qc - residue) / period).toLong()
            if (lower < low) {
                lower = residue + period * ceil((low - residue).toDouble() / period).toLong()
                return f(lower, w, eOthers, sOthers)
            }
            return minOf(f(lower, w, eOthers, sOthers), f(lower + period, w, eOthers, sOthers))
        }

        private fun f(q0: Long, w: Long, eOthers: Double, sOthers: Double): Double {
            val rest = (w - base * q0) - eOthers
            return square(q0 - ideal[0]) + rest * rest / sOthers
        }

        private companion object {
            const val UNREACHABLE = Long.MAX_VALUE

            fun square(x: Double) = x * x

            fun clampRound(x: Double, low: Long, high: Long): Long = when {
                x.isNaN() || x <= low -> low
                x >= high -> high
                else -> x.roundToLong().coerceIn(low, high)
            }

            /**
             * Add a step to a residue table (round-robin algorithm): table[r] is the smallest
             * reachable amount congruent to r modulo table.size.
             */
            fun addStep(table: LongArray, step: Long): LongArray {
                val modulus = table.size
                val result = table.copyOf()
                val stepMod = (step % modulus).toInt()
                if (stepMod == 0) return result
                val cycles = gcd(modulus, stepMod)
                val cycleLength = modulus / cycles
                for (start in 0 until cycles) {
                    // Start each cycle at its minimum and relax once around it.
                    var minResidue = start
                    var r = start
                    repeat(cycleLength) {
                        if (result[r] < result[minResidue]) minResidue = r
                        r = (r + stepMod) % modulus
                    }
                    if (result[minResidue] == UNREACHABLE) continue
                    r = minResidue
                    repeat(cycleLength - 1) {
                        val next = (r + stepMod) % modulus
                        val candidate = result[r] + step
                        if (candidate < result[next]) result[next] = candidate
                        r = next
                    }
                }
                return result
            }

            /** x⁻¹ mod m for gcd(x, m) = 1 and m > 1. */
            fun modInverse(x: Int, m: Int): Int {
                var (oldR, r) = Math.floorMod(x, m) to m
                var (oldS, s) = 1 to 0
                while (r != 0) {
                    val q = oldR / r
                    oldR = r.also { r = oldR - q * r }
                    oldS = s.also { s = oldS - q * s }
                }
                return Math.floorMod(oldS, m)
            }
        }
    }

    /** Calls [action] with every k-element combination of [items], in lexicographic order. */
    private inline fun forEachCombination(items: List<Int>, k: Int, action: (IntArray) -> Unit) {
        if (k > items.size) return
        val idx = IntArray(k) { it }
        val combo = IntArray(k)
        while (true) {
            for (j in 0 until k) combo[j] = items[idx[j]]
            action(combo)
            var j = k - 1
            while (j >= 0 && idx[j] == items.size - k + j) j--
            if (j < 0) return
            idx[j]++
            for (m in j + 1 until k) idx[m] = idx[m - 1] + 1
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }
}
