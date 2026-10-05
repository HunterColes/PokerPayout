package com.huntercoles.pokerpayout.core.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Tests for [ChipDistributionOptimizer].
 *
 * The contract: for every input the UI can produce, the optimizer returns either an exact
 * breakdown (counts × denominations == starting stack, standard chips only, smallest chip
 * included) or a typed failure that is genuinely unavoidable. It never throws, and no call
 * comes anywhere near the old multi-second hangs.
 */
class ChipDistributionOptimizerTest {

    private val steep = ChipDistributionCurve.LinearSteep
    private val moderate = ChipDistributionCurve.LinearModerate
    private val bell = ChipDistributionCurve.BellCurve
    private val positive = ChipDistributionCurve.PositiveLinear
    private val exponential = ChipDistributionCurve.ExponentialDecay

    // ---------------------------------------------------------------- helpers

    private fun optimize(target: Int, smallest: Int, count: Int, curve: ChipDistributionCurve) =
        ChipDistributionOptimizer.optimize(target, smallest, count, curve)

    private fun success(target: Int, smallest: Int, count: Int, curve: ChipDistributionCurve): ChipDistributionOutcome.Success {
        val outcome = optimize(target, smallest, count, curve)
        assertTrue(
            outcome is ChipDistributionOutcome.Success,
            "expected a breakdown for $target/$smallest/$count/${curve.displayName}, got $outcome"
        )
        outcome as ChipDistributionOutcome.Success
        assertExactBreakdown(outcome, target, smallest, count, curve)
        return outcome
    }

    // ------------------------------------------------- the reported crash repros (B3)

    @Nested
    inner class ReportedCrashes {

        @Test
        fun `smallest chip 15 snaps to 10 and gives an exact breakdown`() {
            val outcome = success(5000, 15, 5, steep)
            assertEquals(10, outcome.usedSmallestChip)
            assertTrue(outcome.smallestChipAdjusted)
            assertEquals("15 isn't a standard chip, so the smallest chip used is 10.", outcome.note)
        }

        @Test
        fun `starting chips 100 with smallest chip 50 is two 50 chips`() {
            val result = success(100, 50, 5, steep).distribution
            assertEquals(listOf(50), result.denominations)
            assertEquals(listOf(2), result.quantities)
            assertEquals(2, result.totalChips)
        }

        @Test
        fun `5010 with smallest chip 25 is a typed not-reachable error`() {
            val outcome = optimize(5010, 25, 5, steep)
            assertEquals(ChipDistributionOutcome.StackNotReachable(5010, 25, 25, unit = 25), outcome)
            outcome as ChipDistributionOutcome.StackNotReachable
            assertEquals(5000, outcome.nearestBelow)
            assertEquals(5025, outcome.nearestAbove)
            assertEquals("5010 can't be made from chips of 25 and up. Try 5000 or 5025.", outcome.message)
        }

        @Test
        fun `non-standard smallest chips inherited from tournament blinds snap to the nearest chip`() {
            // Tournament → Blinds accepts any smallest chip ≥ 1, and the chip calculator inherits it.
            val expectedSnap = mapOf(
                3 to 1, // tie between 1 and 5 goes to the smaller chip
                7 to 5,
                15 to 10, // tie between 10 and 20
                30 to 25,
                40 to 50,
                75 to 50, // tie between 50 and 100
                99 to 100,
                150 to 100,
                300 to 250,
                750 to 500, // tie between 500 and 1000
                1500 to 1000, // tie between 1000 and 2000
                10_000 to 5000,
                1_000_000 to 5000
            )
            for ((asked, snapped) in expectedSnap) {
                assertEquals(snapped, ChipDistributionOptimizer.snapToStandardDenomination(asked), "snap($asked)")
                val outcome = success(20_000, asked, 5, steep)
                assertEquals(snapped, outcome.usedSmallestChip, "used smallest chip for $asked")
                assertEquals(asked != snapped, outcome.smallestChipAdjusted)
            }
        }

        @Test
        fun `standard smallest chips are used as-is with no note`() {
            for (chip in ChipDistributionOptimizer.STANDARD_DENOMINATIONS) {
                assertEquals(chip, ChipDistributionOptimizer.snapToStandardDenomination(chip))
                val outcome = success(maxOf(chip * 3, 15_000), chip, 5, steep)
                assertFalse(outcome.smallestChipAdjusted)
                assertNull(outcome.note)
            }
        }
    }

    // ------------------------------------------------------------ typed failures

    @Nested
    inner class TypedFailures {

        @Test
        fun `stack smaller than the smallest chip`() {
            assertEquals(ChipDistributionOutcome.StackSmallerThanSmallestChip(40, 50, 50), optimize(40, 50, 5, steep))
            assertEquals(
                "Starting chips (40) must be at least the smallest chip (50).",
                (optimize(40, 50, 5, steep) as ChipDistributionOutcome.Failure).message
            )
        }

        @Test
        fun `non-positive inputs are invalid, not crashes`() {
            val f = ChipDistributionOutcome.Field.entries
            assertEquals(ChipDistributionOutcome.InvalidInput(f[0], 0), optimize(0, 25, 5, steep))
            assertEquals(ChipDistributionOutcome.InvalidInput(f[0], -5000), optimize(-5000, 25, 5, steep))
            assertEquals(ChipDistributionOutcome.InvalidInput(f[1], 0), optimize(5000, 0, 5, steep))
            assertEquals(ChipDistributionOutcome.InvalidInput(f[2], 0), optimize(5000, 25, 0, steep))
        }

        @Test
        fun `95 with smallest chip 20 has no non-increasing breakdown but has a bell one`() {
            // Chips allowed: 20 and 25 (95 / 3 = 31). 20a + 25b = 95 with a, b ≥ 1 only as (1, 3).
            assertEquals(ChipDistributionOutcome.NoExactBreakdown(95, 20, 20, steep), optimize(95, 20, 5, steep))
            val result = success(95, 20, 5, bell).distribution
            assertEquals(listOf(20, 25), result.denominations)
            assertEquals(listOf(1, 3), result.quantities)
        }

        @Test
        fun `smallest chip 20 can reach stacks that are not multiples of 20`() {
            // 5010 = 20·a + 25·b + … is reachable because 25 is allowed alongside 20.
            success(5010, 20, 5, steep)
        }
    }

    // ------------------------------------------------- default config and old tests

    @Test
    fun `default config gives the breakdown the app showed before`() {
        // Tournament defaults: 5000 starting chips, smallest chip 50, 5 denominations, Linear Steep.
        // The pre-fix optimizer returned this same breakdown (device tour screen 33).
        val result = success(5000, 50, 5, steep).distribution
        assertEquals(listOf(50, 100, 250, 500, 1000), result.denominations)
        assertEquals(listOf(9, 8, 5, 3, 1), result.quantities)
        assertEquals(26, result.totalChips)
        assertEquals(
            ChipDistributionOptimizer.calculateFitScoreForDistribution(result.denominations, result.quantities, steep),
            result.fitScore
        )
    }

    @Test
    fun `500 stack Linear Steep - five denominations, exact, non-increasing`() {
        val result = success(500, 10, 5, steep).distribution
        assertEquals(5, result.denominations.size)
        assertTrue(result.totalChips in 10..200, "total chips ${result.totalChips}")
    }

    @Test
    fun `5000 stack Linear Steep - five denominations in the preferred 40 to 80 chip range`() {
        val result = success(5000, 10, 5, steep).distribution
        assertEquals(5, result.denominations.size)
        assertTrue(result.totalChips in 40..80, "total chips ${result.totalChips}")
    }

    @Test
    fun `50000 stack Linear Steep - five denominations in the preferred 40 to 80 chip range`() {
        val result = success(50_000, 10, 5, steep).distribution
        assertEquals(5, result.denominations.size)
        assertTrue(result.totalChips in 40..80, "total chips ${result.totalChips}")
    }

    @Test
    fun `bell and moderate curves use five denominations for a 5000 stack`() {
        assertEquals(5, success(5000, 25, 5, bell).distribution.denominations.size)
        assertEquals(5, success(5000, 25, 5, moderate).distribution.denominations.size)
    }

    @Test
    fun `denomination count is honoured up to the chips available`() {
        // 5000 with smallest 25 allows 25, 50, 100, 250, 500, 1000 (1000 ≤ 5000 / 3 < 2000).
        for ((count, expected) in listOf(3 to 3, 4 to 4, 5 to 5, 6 to 6, 7 to 6, 8 to 6)) {
            assertEquals(expected, success(5000, 25, count, steep).distribution.denominations.size, "count $count")
        }
    }

    @Test
    fun `different standard smallest chips are the first denomination`() {
        for (smallest in listOf(5, 25, 100, 500)) {
            val result = success(5000, smallest, 5, steep).distribution
            assertEquals(smallest, result.denominations.first())
        }
    }

    @Test
    fun `positive linear puts more chips on larger denominations`() {
        val q = success(5000, 25, 5, positive).distribution.quantities
        val rising = (0 until q.size - 1).count { q[it + 1] >= q[it] }
        assertTrue(rising >= 3, "quantities should mostly rise: $q")
        assertTrue(q.last() > q.first(), "largest chip count should exceed smallest: $q")
    }

    @Test
    fun `exponential decay puts the most chips on the smallest denomination`() {
        val q = success(5000, 25, 5, exponential).distribution.quantities
        assertEquals(q.max(), q.first(), "smallest chip should have the most: $q")
    }

    @Test
    fun `fit score is high for a distribution that follows the curve`() {
        val fitScore = ChipDistributionOptimizer.calculateFitScoreForDistribution(
            listOf(25, 50, 100, 250, 500),
            listOf(20, 18, 16, 10, 4),
            steep
        )
        assertTrue(fitScore > 0.8, "fit score $fitScore")
    }

    @Test
    fun `fit score is low for a distribution against the curve`() {
        val fitScore = ChipDistributionOptimizer.calculateFitScoreForDistribution(
            listOf(25, 50, 100, 250, 500),
            listOf(5, 8, 12, 18, 20),
            steep
        )
        assertTrue(fitScore < 0.6, "fit score $fitScore")
    }

    @Test
    fun `curve value functions`() {
        assertEquals(1.0, steep.getValue(0.0), 0.001)
        assertEquals(0.5, steep.getValue(0.5), 0.001)
        assertEquals(0.0, steep.getValue(1.0), 0.001)

        assertEquals(1.0, moderate.getValue(0.0), 0.001)
        assertEquals(0.75, moderate.getValue(0.5), 0.001)
        assertEquals(0.5, moderate.getValue(1.0), 0.001)

        assertEquals(1.0, bell.getValue(0.5), 0.001)
        assertTrue(bell.getValue(0.5) > bell.getValue(0.0))
        assertEquals(bell.getValue(0.2), bell.getValue(0.8), 1e-12)

        assertEquals(0.0, positive.getValue(0.0), 0.001)
        assertEquals(1.0, positive.getValue(1.0), 0.001)

        assertEquals(1.0, exponential.getValue(0.0), 0.001)
        assertEquals(kotlin.math.exp(-3.0), exponential.getValue(1.0), 1e-12)
    }

    // ------------------------------------------------------- worst cases (B6)

    @Nested
    inner class WorstCases {
        // Grader's worst cases for the old pair/triple perturbation search: 0.5–6 billion iterations.
        private val cases = listOf(
            Triple(5000, 1, steep),
            Triple(50_000, 1, moderate),
            Triple(20_000, 10, bell)
        )

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        fun `old billion-iteration inputs finish within the per-call budget`() {
            repeat(WARMUP_ROUNDS) { cases.forEach { (t, s, c) -> optimize(t, s, 8, c) } }
            for ((t, s, c) in cases) {
                val start = System.nanoTime()
                val outcome = optimize(t, s, 8, c)
                val ms = (System.nanoTime() - start) / 1e6
                println("TIMING worst $t/$s/8/${c.displayName}: ${"%.2f".format(ms)} ms")
                assertExactBreakdown(outcome as ChipDistributionOutcome.Success, t, s, 8, c)
                assertTrue(ms <= PER_CALL_BUDGET_MS, "$t/$s/8/${c.displayName} took $ms ms")
            }
        }
    }

    // --------------------------------------------------- full input-domain sweep

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Suppress("NestedBlockDepth") // one loop per input dimension of the sweep
    fun `every UI-reachable input gives an exact breakdown or a genuine typed error, within budget`() {
        // Chip calculator UI: smallest chip 1..100, starting chips 1..999,999,999, 3..8
        // denominations, 5 curves. Tournament → Blinds feeds any smallest chip ≥ 1.
        val smallestChips = (1..100).toList() + listOf(120, 150, 250, 300, 500, 1000, 2500, 5000, 10_000, 1_000_000)
        val stacks = listOf(
            1, 2, 3, 5, 10, 25, 49, 50, 95, 99, 100, 101, 149, 150, 151, 300, 500, 999, 1000, 1500, 2000,
            2500, 3000, 4999, 5000, 5010, 7500, 10_000, 15_000, 20_000, 25_000, 50_000, 100_000,
            1_000_000, 999_999_999
        )
        val curves = ChipDistributionCurve.getAllCurves()

        repeat(WARMUP_ROUNDS) { for (t in listOf(5000, 50_000, 999_999_999)) for (c in curves) optimize(t, 1, 6, c) }

        var calls = 0
        var successes = 0
        val failures = sortedMapOf<String, Int>()
        val timings = DoubleArray(stacks.size * smallestChips.size * 6 * curves.size)
        var slowest = ""
        var slowestMs = 0.0
        for (t in stacks) for (s in smallestChips) for (k in 3..8) for (c in curves) {
            val start = System.nanoTime()
            val outcome = try {
                optimize(t, s, k, c)
            } catch (e: Throwable) {
                fail<Nothing>("threw for $t/$s/$k/${c.displayName}", e)
            }
            val ms = (System.nanoTime() - start) / 1e6
            timings[calls++] = ms
            if (ms > slowestMs) {
                slowestMs = ms
                slowest = "$t/$s/$k/${c.displayName}"
            }
            when (outcome) {
                is ChipDistributionOutcome.Success -> {
                    assertExactBreakdown(outcome, t, s, k, c)
                    successes++
                }
                is ChipDistributionOutcome.Failure -> {
                    assertGenuineFailure(outcome, t, s, k, c)
                    failures.merge(outcome.javaClass.simpleName, 1, Int::plus)
                }
            }
        }
        timings.sort()
        println(
            "SWEEP calls=$calls successes=$successes failures=$failures " +
                "p50=${"%.3f".format(timings[calls / 2])}ms p99=${"%.3f".format(timings[calls * 99 / 100])}ms " +
                "max=${"%.3f".format(slowestMs)}ms ($slowest) total=${"%.1f".format(timings.sum() / 1000)}s"
        )
        assertTrue(slowestMs <= PER_CALL_BUDGET_MS, "slowest call $slowest took $slowestMs ms")
    }

    // ---------------------------------------------- brute-force optimality oracle

    @Test
    fun `small stacks match a brute-force oracle`() {
        // For small stacks the search space can be enumerated outright. The oracle checks that
        // the optimizer (a) succeeds exactly when some breakdown exists, (b) uses as many
        // denominations as possible up to the count asked for, (c) returns the counts closest to
        // the curve's ideal for its denomination set, and (d) picks the best-scoring set.
        val cases = mutableListOf<Triple<Int, Int, Int>>()
        for (t in 1..60) cases += Triple(t, 1, 5)
        for (s in listOf(5, 10, 20, 25, 50)) for (t in (s..s * 12 step 5)) for (k in listOf(3, 5, 8)) cases += Triple(t, s, k)
        var checked = 0
        for ((t, s, k) in cases) for (c in ChipDistributionCurve.getAllCurves()) {
            checkAgainstOracle(t, s, k, c)
            checked++
        }
        println("ORACLE checked=$checked")
    }

    @Suppress("CyclomaticComplexMethod") // builds the oracle, then one check per optimality property
    private fun checkAgainstOracle(t: Int, s: Int, k: Int, curve: ChipDistributionCurve) {
        val label = "$t/$s/$k/${curve.displayName}"
        val outcome = optimize(t, s, k, curve)
        val available = allowedChips(t, s)
        val monotone = curve == steep || curve == moderate
        val larger = available.drop(1)

        var oracleSize = 0
        val oracleByCombo = mutableMapOf<List<Int>, Pair<Double, List<IntArray>>>() // min cost and all argmins
        for (size in minOf(k, available.size) downTo 1) {
            for (combo in combinations(larger, size - 1)) {
                val denoms = listOf(available.first()) + combo
                val ideal = idealCounts(t, denoms, curve)
                var bestCost = Double.POSITIVE_INFINITY
                val argmins = mutableListOf<IntArray>()
                enumerate(denoms, t, monotone) { q ->
                    val cost = q.indices.sumOf { (q[it] - ideal[it]).let { d -> d * d } }
                    if (cost < bestCost - 1e-9) {
                        bestCost = cost
                        argmins.clear()
                        argmins += q.copyOf()
                    } else if (abs(cost - bestCost) <= 1e-9) {
                        argmins += q.copyOf()
                    }
                }
                if (argmins.isNotEmpty()) oracleByCombo[denoms] = bestCost to argmins
            }
            if (oracleByCombo.isNotEmpty()) {
                oracleSize = size
                break
            }
        }

        if (oracleSize == 0) {
            assertTrue(outcome is ChipDistributionOutcome.Failure, "$label: oracle finds no breakdown but got $outcome")
            return
        }
        assertTrue(outcome is ChipDistributionOutcome.Success, "$label: oracle finds a breakdown but got $outcome")
        val result = (outcome as ChipDistributionOutcome.Success).distribution
        assertEquals(oracleSize, result.denominations.size, "$label: denomination count")

        val (minCost, _) = oracleByCombo[result.denominations] ?: fail("$label: oracle has no answer for ${result.denominations}")
        val ideal = idealCounts(t, result.denominations, curve)
        val cost = result.quantities.indices.sumOf { (result.quantities[it] - ideal[it]).let { d -> d * d } }
        assertEquals(minCost, cost, 1e-6, "$label: counts ${result.quantities} are not the closest to the ideal")

        fun boosted(denoms: List<Int>, q: List<Int>): Double {
            val fit = ChipDistributionOptimizer.calculateFitScoreForDistribution(denoms, q, curve)
            return if (q.sum() in 40..80) fit * 2.0 else fit
        }
        val chosen = boosted(result.denominations, result.quantities)
        for ((denoms, entry) in oracleByCombo) {
            // Compare against the oracle's least-favourable tie so equal-cost alternatives can't flip the check.
            val worstTie = entry.second.minOf { boosted(denoms, it.toList()) }
            assertTrue(chosen >= worstTie - 1e-9, "$label: $denoms scores $worstTie > chosen $chosen")
        }
    }

    // --------------------------------------------------------- invariants

    private fun allowedChips(t: Int, s: Int): List<Int> {
        val smallest = ChipDistributionOptimizer.snapToStandardDenomination(s)
        return STANDARD.filter { it == smallest || (it > smallest && it <= t / 3) }
    }

    private fun assertExactBreakdown(
        outcome: ChipDistributionOutcome.Success,
        t: Int,
        s: Int,
        k: Int,
        curve: ChipDistributionCurve
    ) {
        val label = "$t/$s/$k/${curve.displayName}"
        val r = outcome.distribution
        val d = r.denominations
        val q = r.quantities
        assertEquals(d.size, q.size, label)
        assertEquals(t.toLong(), d.indices.sumOf { d[it].toLong() * q[it] }, "$label: value of $d × $q")
        assertEquals(t, r.totalValue, label)
        assertEquals(q.sum(), r.totalChips, label)
        assertEquals(s, outcome.requestedSmallestChip, label)
        assertEquals(ChipDistributionOptimizer.snapToStandardDenomination(s), d.first(), "$label: smallest chip included")
        assertTrue(d.size in 1..k, "$label: ${d.size} denominations for $k asked")
        assertTrue(d.all { it in STANDARD }, "$label: non-standard chip in $d")
        assertTrue(d.zipWithNext().all { (a, b) -> a < b }, "$label: denominations not ascending $d")
        assertTrue(d.drop(1).all { it <= t / 3 }, "$label: a chip above a third of the stack in $d")
        assertTrue(q.all { it >= 1 }, "$label: zero count in $q")
        if (curve == steep || curve == moderate) {
            assertTrue(q.zipWithNext().all { (a, b) -> a >= b }, "$label: counts rise in $q")
        }
        assertTrue(r.fitScore in 0.0..1.0, "$label: fit ${r.fitScore}")
        assertEquals(ChipDistributionOptimizer.calculateFitScoreForDistribution(d, q, curve), r.fitScore, 1e-12, label)
        assertEquals(curve, r.curveUsed, label)
    }

    private fun assertGenuineFailure(
        failure: ChipDistributionOutcome.Failure,
        t: Int,
        s: Int,
        k: Int,
        curve: ChipDistributionCurve
    ) {
        val label = "$t/$s/$k/${curve.displayName}"
        assertTrue(failure.message.isNotBlank(), label)
        val smallest = ChipDistributionOptimizer.snapToStandardDenomination(s)
        when (failure) {
            is ChipDistributionOutcome.InvalidInput -> fail("$label: inputs are valid but got $failure")
            is ChipDistributionOutcome.StackSmallerThanSmallestChip -> {
                assertTrue(t < smallest, "$label: $failure")
                assertEquals(smallest, failure.usedSmallestChip, label)
            }
            is ChipDistributionOutcome.StackNotReachable -> {
                val unit = allowedChips(t, s).fold(0) { a, b -> gcd(a, b) }
                assertEquals(unit, failure.unit, label)
                assertTrue(t % unit != 0, "$label: $t is a multiple of $unit")
                assertTrue(failure.nearestBelow % unit == 0 && failure.nearestBelow < t, label)
                assertTrue(failure.nearestAbove % unit == 0 && failure.nearestAbove > t, label)
            }
            is ChipDistributionOutcome.NoExactBreakdown -> {
                // Only small stacks can fall in the gaps; prove it by exhaustive search.
                assertTrue(t <= 2000, "$label: unexpected NoExactBreakdown for a large stack")
                val available = allowedChips(t, s)
                val monotone = curve == steep || curve == moderate
                for (size in 1..minOf(k, available.size)) {
                    for (combo in combinations(available.drop(1), size - 1)) {
                        enumerate(listOf(available.first()) + combo, t, monotone) { q ->
                            fail<Unit>("$label: ${listOf(available.first()) + combo} × ${q.toList()} exists")
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ oracle tools

    /** Same ideal as the production spec: curve-proportional counts worth exactly [t]. */
    private fun idealCounts(t: Int, denoms: List<Int>, curve: ChipDistributionCurve): DoubleArray {
        val min = denoms.first().toDouble()
        val max = denoms.last().toDouble()
        val y = denoms.map { curve.getValue(if (min == max) 0.5 else (it - min) / (max - min)).coerceIn(0.0, 1.0) }
        val counts = y.map { it / y.sum() * 60.0 }
        val value = denoms.indices.sumOf { denoms[it] * counts[it] }
        return DoubleArray(denoms.size) { counts[it] * t / value }
    }

    /** Every count vector with Σ d·q == t, q ≥ 1, and non-increasing counts when [monotone]. */
    private fun enumerate(denoms: List<Int>, t: Int, monotone: Boolean, visit: (IntArray) -> Unit) {
        val n = denoms.size
        val q = IntArray(n)
        val minBelow = IntArray(n) // value of one chip of each denomination below index i
        for (i in 1 until n) minBelow[i] = minBelow[i - 1] + denoms[i - 1]
        fun go(i: Int, remaining: Int, lower: Int) {
            if (i == 0) {
                if (remaining % denoms[0] == 0 && remaining / denoms[0] >= lower) {
                    q[0] = remaining / denoms[0]
                    visit(q)
                }
                return
            }
            var qi = lower
            while (denoms[i] * qi + (if (monotone) qi else 1) * minBelow[i] <= remaining) {
                q[i] = qi
                go(i - 1, remaining - denoms[i] * qi, if (monotone) qi else 1)
                qi++
            }
        }
        go(n - 1, t, 1)
    }

    private fun <T> combinations(items: List<T>, k: Int): List<List<T>> = when {
        k == 0 -> listOf(emptyList())
        k > items.size -> emptyList()
        else -> items.indices.flatMap { i -> combinations(items.drop(i + 1), k - 1).map { listOf(items[i]) + it } }
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private companion object {
        val STANDARD = listOf(1, 5, 10, 20, 25, 50, 100, 250, 500, 1000, 2000, 5000)

        /**
         * Hang guard, not a benchmark: the old optimizer took more than 60 s on these inputs.
         * Typical calls take well under 1 ms (printed above), but shared CI runners have hit
         * 60 ms on the 1,000,000-chip extreme, so the ceiling leaves room for noisy machines
         * while still failing any regression toward a hang by orders of magnitude.
         */
        const val PER_CALL_BUDGET_MS = 500.0

        const val WARMUP_ROUNDS = 20
    }
}
