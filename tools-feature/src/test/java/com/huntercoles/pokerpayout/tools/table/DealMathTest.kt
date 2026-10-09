package com.huntercoles.pokerpayout.tools.table

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The deal maker's maths. ICM against values worked out by hand (and against a brute force over
 * every finishing order), the chip chop against its definition, and on 3,000 random deals from a
 * fixed seed: both splits add up to the money shared to the cent, nobody gets less than the
 * smallest prize (nor more than 1st by ICM), and a bigger stack never takes less.
 */
class DealMathTest {

    @Test
    fun `heads-up ICM is each player's chance of 1st times the gap, plus 2nd`() {
        // 3,000 v 1,000 for $100 and $50: 3/4 of 100 + 1/4 of 50 = $87.50, and $62.50.
        val ev = Icm.expectedPrizes(listOf(3_000, 1_000), listOf(10_000, 5_000))
        assertEquals(8_750.0, ev[0], EPSILON)
        assertEquals(6_250.0, ev[1], EPSILON)
        assertEquals(listOf(8_750L, 6_250L), DealMath.deal(listOf(3_000, 1_000), listOf(10_000, 5_000)).icm)
    }

    @Test
    fun `three-handed ICM matches the hand-worked example`() {
        // Stacks 5,000 / 3,000 / 2,000 for $50 / $30 / $20. By hand, in exact fractions:
        // A: 1/2 x 50 + 19/56 x 30 + 9/56 x 20  = $38.392857...
        // B: 3/10 x 50 + 3/8 x 30 + 13/40 x 20  = $32.75
        // C: 1/5 x 50 + 2/7 x 30 + 18/35 x 20   = $28.857142...
        val ev = Icm.expectedPrizes(listOf(5_000, 3_000, 2_000), listOf(5_000, 3_000, 2_000))
        assertEquals(5_000.0 / 2 + 3_000.0 * 19 / 56 + 2_000.0 * 9 / 56, ev[0], EPSILON)
        assertEquals(3_275.0, ev[1], EPSILON)
        assertEquals(1_000.0 + 3_000.0 * 2 / 7 + 2_000.0 * 18 / 35, ev[2], EPSILON)
        // In cents, rounded down (3,839 + 3,275 + 2,885 = 9,999), the cent left to the largest remainder (C's .71).
        assertEquals(listOf(3_839L, 3_275L, 2_886L), DealMath.deal(listOf(5_000, 3_000, 2_000), listOf(5_000, 3_000, 2_000)).icm)
    }

    @Test
    fun `ICM agrees with every finishing order worked out one by one`() {
        val random = Random(SEED)
        repeat(BRUTE_FORCE_DEALS) {
            val players = random.nextInt(DealMath.MIN_PLAYERS, BRUTE_FORCE_MAX + 1)
            val stacks = List(players) { random.nextLong(1, 20_000) }
            val prizes = descendingPrizes(random, players)
            val ev = Icm.expectedPrizes(stacks, prizes)
            val brute = bruteForce(stacks, prizes)
            stacks.indices.forEach { assertEquals(brute[it], ev[it], 1e-6, "$stacks $prizes, player $it") }
        }
    }

    @Test
    fun `winner takes all makes ICM a chip chop`() {
        val deal = DealMath.deal(listOf(5_000, 3_000, 2_000), listOf(10_000, 0, 0))
        assertEquals(listOf(5_000L, 3_000L, 2_000L), deal.icm)
        assertEquals(deal.icm, deal.chipChop)
    }

    @Test
    fun `the chip chop gives everyone the smallest prize, then the rest by chips`() {
        // $20 each, then the $40 left at 50 / 30 / 20 per cent.
        val deal = DealMath.deal(listOf(5_000, 3_000, 2_000), listOf(5_000, 3_000, 2_000))
        assertEquals(2_000L, deal.floorCents)
        assertEquals(listOf(4_000L, 3_200L, 2_800L), deal.chipChop)
    }

    @Test
    fun `equal stacks split evenly, the odd cent to the first listed`() {
        val deal = DealMath.deal(listOf(1_000, 1_000, 1_000), listOf(5_000, 3_000, 2_000))
        assertEquals(listOf(3_334L, 3_333L, 3_333L), deal.icm)
        assertEquals(listOf(3_334L, 3_333L, 3_333L), deal.chipChop)
    }

    @Test
    fun `saving some for the winner takes it off 1st and shares the rest`() {
        val deal = DealMath.deal(listOf(5_000, 3_000, 2_000), listOf(5_000, 3_000, 2_000), forWinnerCents = 1_000)
        assertEquals(9_000L, deal.splitCents)
        assertEquals(1_000L, deal.forWinnerCents)
        assertEquals(9_000L, deal.icm.sum())
        // $20 each, then $30 at 50 / 30 / 20.
        assertEquals(listOf(3_500L, 2_900L, 2_600L), deal.chipChop)
        val ev = Icm.expectedPrizes(listOf(5_000, 3_000, 2_000), listOf(4_000, 3_000, 2_000))
        deal.icm.forEachIndexed { player, cents ->
            assertTrue(kotlin.math.abs(cents - ev[player]) < 1.0, "$cents v ${ev[player]}")
        }
    }

    @Test
    fun `a big enough chip leader gets more than 1st by chip chop, never by ICM`() {
        // 98% of the chips: $20 + 98% of the $40 over the floors is $59.20, more than 1st's $50.
        val deal = DealMath.deal(listOf(9_800, 100, 100), listOf(5_000, 3_000, 2_000))
        assertEquals(5_920L, deal.chipChop[0])
        assertTrue(deal.icm[0] < 5_000L, "ICM: ${deal.icm}")
    }

    @Test
    fun `places past the prizes pay nothing`() {
        // Four left, three paid: the 4th place's prize is 0, so the chip chop has no floor.
        val deal = DealMath.deal(listOf(4_000, 3_000, 2_000, 1_000), listOf(5_000, 3_000, 2_000))
        assertEquals(0L, deal.floorCents)
        assertEquals(listOf(4_000L, 3_000L, 2_000L, 1_000L), deal.chipChop)
        assertEquals(10_000L, deal.icm.sum())
    }

    @Test
    fun `what stops a deal`() {
        val prizes = listOf(5_000L, 3_000L, 2_000L)
        assertEquals(DealProblem.MissingChips, DealMath.problem(listOf(5_000, null, 2_000), prizes, 0))
        assertEquals(DealProblem.MissingChips, DealMath.problem(listOf(5_000, 0, 2_000), prizes, 0))
        assertEquals(DealProblem.NoPrizes, DealMath.problem(listOf(1L, 2L, 3L), listOf(0, 0, 0), 0))
        assertEquals(DealProblem.PrizesGoUp(place = 3), DealMath.problem(listOf(1L, 2L, 3L), listOf(5_000, 2_000, 3_000), 0))
        assertEquals(DealProblem.TooMuchForWinner(maxCents = 2_000), DealMath.problem(listOf(1L, 2L, 3L), prizes, 2_001))
        assertNull(DealMath.problem(listOf(1L, 2L, 3L), prizes, 2_000))
        assertEquals(2_000L, DealMath.maxForWinner(prizes))
    }

    @Test
    fun `every deal adds up to the cent, stays between the smallest prize and 1st, and follows the stacks`() {
        val random = Random(SEED)
        repeat(RANDOM_DEALS) {
            val players = random.nextInt(DealMath.MIN_PLAYERS, DealMath.MAX_PLAYERS + 1)
            val stacks = List(players) { random.nextLong(1, 2_000_000) }
            val prizes = descendingPrizes(random, players)
            val forWinner = random.nextLong(0, DealMath.maxForWinner(prizes) + 1)
            val deal = DealMath.deal(stacks, prizes, forWinner)
            val shared = listOf(prizes[0] - forWinner) + prizes.drop(1)
            val where = "$stacks $prizes, $forWinner for the winner"
            assertEquals(shared.sum(), deal.splitCents, where)
            assertEquals(deal.splitCents, deal.icm.sum(), "ICM: $where")
            assertEquals(deal.splitCents, deal.chipChop.sum(), "chip chop: $where")
            deal.icm.forEach { assertTrue(it in shared.min()..shared.max(), "ICM ${deal.icm}: $where") }
            deal.chipChop.forEach { assertTrue(it >= shared.min(), "chip chop ${deal.chipChop}: $where") }
            listOf(deal.icm, deal.chipChop).forEach { split ->
                stacks.indices.forEach { a ->
                    stacks.indices.filter { stacks[it] < stacks[a] }.forEach { b ->
                        assertTrue(split[a] >= split[b], "$split: a bigger stack took less, $where")
                    }
                }
            }
        }
    }

    /** One prize per player, 1st first, never going up; the lower places may pay nothing. */
    private fun descendingPrizes(random: Random, players: Int): List<Long> =
        List(players) { random.nextLong(0, 100_000) }.sortedDescending().let { prizes ->
            if (prizes.sum() == 0L) listOf(1L) + prizes.drop(1) else prizes
        }

    /** Malmuth-Harville the slow way: every finishing order, its chance, its prizes. */
    private fun bruteForce(stacks: List<Long>, prizes: List<Long>): DoubleArray {
        val ev = DoubleArray(stacks.size)
        fun walk(order: List<Int>, chance: Double) {
            if (order.size == stacks.size) {
                order.forEachIndexed { place, player -> ev[player] += chance * prizes.getOrElse(place) { 0L } }
                return
            }
            val left = stacks.indices.filterNot { it in order }
            val chips = left.sumOf { stacks[it] }.toDouble()
            left.forEach { walk(order + it, chance * stacks[it] / chips) }
        }
        walk(emptyList(), 1.0)
        return ev
    }

    private companion object {
        const val SEED = 20261008L
        const val EPSILON = 1e-9
        const val BRUTE_FORCE_DEALS = 300
        const val BRUTE_FORCE_MAX = 6
        const val RANDOM_DEALS = 3_000
    }
}
