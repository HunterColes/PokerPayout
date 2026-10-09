package com.huntercoles.pokerpayout.core.domain.settle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fewest payments (1.4): the exact search against a brute force on small cases, and over seeded
 * random balances that every result squares everyone to the cent, never moves money the wrong way,
 * and never takes more payments than the greedy pass it replaced.
 */
class MinimumPaymentsTest {

    private fun Transfer.text() = "$fromId pays $toId ${amountCents / 100}"

    private fun balances(vararg dollars: Long): Map<Int, Long> =
        dollars.withIndex().associateTo(LinkedHashMap()) { (index, amount) -> index + 1 to amount * 100L }

    // Cases you can check by hand -------------------------------------------------------------------

    @Test
    fun `the greedy pass's counterexample takes 3, not 4`() {
        // +5 +4 −4 −3 −2: the 4 pays the 4, and the 3 and the 2 pay the 5. Greedy pays the 4 to the 5
        // first and needs 4.
        val nets = balances(5, 4, -4, -3, -2)
        assertEquals(4, MinimumPayments.greedy(nets).size)
        val fewest = MinimumPayments.of(nets)
        assertEquals(listOf("3 pays 2 4", "4 pays 1 3", "5 pays 1 2"), fewest.map { it.text() })
    }

    @Test
    fun `pairs that cancel pay each other`() {
        val nets = balances(10, -7, 7, -10)
        assertEquals(listOf("4 pays 1 10", "2 pays 3 7"), MinimumPayments.of(nets).map { it.text() })
    }

    @Test
    fun `one winner is paid by every loser, once each`() {
        assertEquals(
            listOf("4 pays 3 40", "1 pays 3 20", "2 pays 3 15"),
            MinimumPayments.of(balances(-20, -15, 75, -40)).map { it.text() },
        )
    }

    @Test
    fun `everyone square pays nobody, and amounts keep their cents`() {
        assertEquals(emptyList(), MinimumPayments.of(mapOf(1 to 0L, 2 to 0L)))
        assertEquals(emptyList(), MinimumPayments.of(emptyMap()))
        assertEquals(
            listOf(Transfer(2, 1, 1_283L), Transfer(2, 3, 715L)),
            MinimumPayments.of(mapOf(1 to 1_283L, 2 to -1_998L, 3 to 715L)),
        )
    }

    @Test
    fun `balances that don't add up to zero are refused`() {
        assertThrows<IllegalArgumentException> { MinimumPayments.of(mapOf(1 to 100L, 2 to -99L)) }
    }

    @Test
    fun `the same balances always give the same list`() {
        val nets = balances(30, -10, -10, -10, 25, -25, 5, -5)
        assertEquals(MinimumPayments.of(nets), MinimumPayments.of(LinkedHashMap(nets)))
        // Eight parties in three groups: {+30 −10 −10 −10}, {+25 −25}, {+5 −5}
        assertEquals(5, MinimumPayments.of(nets).size)
    }

    // Against a brute force -------------------------------------------------------------------------

    @Test
    fun `small cases take exactly the fewest payments a brute force finds`() {
        val random = Random(SEED)
        repeat(SMALL_RUNS) { run ->
            val nets = randomBalances(random, parties = random.nextInt(2, BRUTE_FORCE_MAX + 1))
            val fewest = MinimumPayments.of(nets)
            checkSquares("run $run $nets", nets, fewest)
            assertEquals(bruteForce(nets.values.filter { it != 0L }), fewest.size, "run $run $nets")
        }
    }

    @Test
    fun `cases built from groups that each add up to zero find every group`() {
        // k parties in g zero-sum groups need k − g; random balances rarely have groups, so build them
        val random = Random(SEED + 1)
        var beatGreedy = 0
        repeat(SMALL_RUNS) { run ->
            val groups = List(random.nextInt(1, 4)) { randomGroup(random, size = random.nextInt(2, 4)) }
            val nets = groups.flatten().shuffled(random).withIndex().associate { (index, cents) -> index + 1 to cents }
            val fewest = MinimumPayments.of(nets)
            checkSquares("run $run $nets", nets, fewest)
            val expected = bruteForce(nets.values.toList())
            assertEquals(expected, fewest.size, "run $run $nets")
            assertTrue(fewest.size <= nets.size - groups.size, "run $run $nets")
            if (fewest.size < MinimumPayments.greedy(nets).size) beatGreedy++
        }
        // The point of the search: on these, the greedy pass often needs more
        assertTrue(beatGreedy > SMALL_RUNS / 10, "only $beatGreedy of $SMALL_RUNS beat the greedy pass")
    }

    // Over random balances, any size ---------------------------------------------------------------

    @Test
    fun `random balances square everyone in no more payments than the greedy pass`() {
        val random = Random(SEED + 2)
        repeat(RUNS) { run ->
            val nets = randomBalances(random, parties = random.nextInt(1, MAX_PARTIES + 1))
            val fewest = MinimumPayments.of(nets)
            val greedy = MinimumPayments.greedy(nets)
            checkSquares("run $run", nets, fewest)
            checkSquares("run $run, greedy", nets, greedy)
            assertTrue(fewest.size <= greedy.size, "run $run: ${fewest.size} > greedy ${greedy.size}")
            val moving = nets.values.count { it != 0L }
            assertTrue(fewest.size <= (moving - 1).coerceAtLeast(0), "run $run")
            if (moving <= MinimumPayments.EXACT_MAX_PARTIES) {
                assertEquals(MinimumPayments.exact(nets), fewest, "run $run")
            } else {
                assertEquals(greedy, fewest, "run $run: above the exact limit, the greedy pass")
            }
            assertEquals(fewest, MinimumPayments.of(nets), "run $run: the same every time")
        }
    }

    @Test
    fun `the exact search at its limit, with round home-game amounts`() {
        // Eleven parties (ten players and the Bank) in $5 steps: plenty of zero-sum groups to find
        val random = Random(SEED + 3)
        repeat(LIMIT_RUNS) { run ->
            val nets = randomBalances(random, parties = MinimumPayments.EXACT_MAX_PARTIES, step = 500L, maxSteps = 12)
            val fewest = MinimumPayments.of(nets)
            checkSquares("run $run", nets, fewest)
            assertTrue(fewest.size <= MinimumPayments.greedy(nets).size, "run $run")
            val moving = nets.values.filter { it != 0L }
            assertEquals(moving.size - mostZeroSumGroups(moving), fewest.size, "run $run")
        }
    }

    /**
     * Every party ends at 0, to the cent: only parties owed money are paid, only parties owing pay,
     * and nobody is ever paid more than owed or pays more than owes.
     */
    private fun checkSquares(what: String, nets: Map<Int, Long>, payments: List<Transfer>) {
        val received = mutableMapOf<Int, Long>()
        val paid = mutableMapOf<Int, Long>()
        payments.forEach { transfer ->
            assertTrue(transfer.amountCents > 0L, "$what: $transfer")
            assertTrue(nets.getValue(transfer.fromId) < 0L, "$what: only who owes pays ($transfer)")
            assertTrue(nets.getValue(transfer.toId) > 0L, "$what: only who is owed is paid ($transfer)")
            received.merge(transfer.toId, transfer.amountCents, Long::plus)
            paid.merge(transfer.fromId, transfer.amountCents, Long::plus)
            assertTrue(received.getValue(transfer.toId) <= nets.getValue(transfer.toId), "$what: $transfer overpays")
            assertTrue(paid.getValue(transfer.fromId) <= -nets.getValue(transfer.fromId), "$what: $transfer overcharges")
        }
        nets.forEach { (id, net) -> assertEquals(net, (received[id] ?: 0L) - (paid[id] ?: 0L), "$what: $id isn't square") }
    }

    /**
     * The fewest payments by search over the payments themselves, independent of the subset search:
     * settle the first open balance against each later one of the other sign in turn, and keep the
     * shortest way to square everyone.
     */
    private fun bruteForce(balances: List<Long>): Int {
        val open = balances.toLongArray()
        fun search(from: Int): Int {
            var first = from
            while (first < open.size && open[first] == 0L) first++
            if (first == open.size) return 0
            var best = Int.MAX_VALUE
            for (other in first + 1 until open.size) {
                if (open[other] == 0L || (open[other] > 0L) == (open[first] > 0L)) continue
                val before = open[other]
                open[other] += open[first]
                val moved = open[first]
                open[first] = 0L
                best = minOf(best, 1 + search(first + 1))
                open[first] = moved
                open[other] = before
            }
            return best
        }
        return search(0)
    }

    /**
     * The most zero-sum groups, by trying every way to split the parties (a second, slower oracle):
     * for each zero-sum set, every zero-sum subset holding its lowest party, plus the best split of
     * the rest.
     */
    private fun mostZeroSumGroups(balances: List<Long>): Int {
        val all = (1 shl balances.size) - 1
        val sums = LongArray(all + 1) { mask -> balances.indices.sumOf { if (mask and (1 shl it) != 0) balances[it] else 0L } }
        // best[mask]: the most groups mask splits into, or −1 if it doesn't add up to 0
        val best = IntArray(all + 1)
        for (mask in 1..all) best[mask] = if (sums[mask] == 0L) bestSplit(mask, sums, best) else -1
        return best[all]
    }

    private fun bestSplit(mask: Int, sums: LongArray, best: IntArray): Int {
        val lowest = mask and -mask
        var most = 1
        var sub = (mask - 1) and mask
        while (sub > 0) {
            val rest = mask xor sub
            if (sub and lowest != 0 && sums[sub] == 0L && best[rest] > 0) most = maxOf(most, 1 + best[rest])
            sub = (sub - 1) and mask
        }
        return most
    }

    /**
     * [parties] balances in whole cents adding up to 0: either any amount up to $200 or [step]
     * multiples, with some parties square.
     */
    private fun randomBalances(random: Random, parties: Int, step: Long = 0L, maxSteps: Int = 0): Map<Int, Long> {
        if (parties < 2) return mapOf(1 to 0L)
        val amounts = MutableList(parties - 1) {
            when {
                random.nextInt(SQUARE_ONE_IN) == 0 -> 0L
                step > 0L -> step * random.nextLong(-maxSteps.toLong(), maxSteps + 1L)
                random.nextBoolean() -> ROUND.random(random) * if (random.nextBoolean()) 1 else -1
                else -> random.nextLong(-20_000L, 20_001L)
            }
        }
        amounts += -amounts.sum()
        return amounts.shuffled(random).withIndex().associateTo(LinkedHashMap()) { (index, cents) -> index + 1 to cents }
    }

    /** [size] non-zero balances adding up to 0. */
    private fun randomGroup(random: Random, size: Int): List<Long> {
        while (true) {
            val amounts = List(size - 1) { random.nextLong(-5_000L, 5_001L) }
            val last = -amounts.sum()
            if (last != 0L && amounts.none { it == 0L }) return amounts + last
        }
    }

    private companion object {
        const val SEED = 20_261_008L
        const val SMALL_RUNS = 1_500
        const val RUNS = 2_000
        const val LIMIT_RUNS = 200
        const val BRUTE_FORCE_MAX = 8
        const val MAX_PARTIES = 16
        const val SQUARE_ONE_IN = 6
        val ROUND = listOf(500L, 1_000L, 2_000L, 2_500L, 4_000L, 5_000L)
    }
}
