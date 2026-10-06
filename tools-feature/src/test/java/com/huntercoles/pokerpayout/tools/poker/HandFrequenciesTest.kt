package com.huntercoles.pokerpayout.tools.poker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Hand ranks screen's frequency column (S12) against a count worked out from first principles:
 * no evaluator, just the rank pattern and the suits of every seven-card hand. (`HandEvaluatorTest`
 * separately deals all 133,784,560 hands through the odds engine's evaluator and gets the same
 * category totals, with royal flushes inside straight flushes.)
 */
class HandFrequenciesTest {

    @Test
    fun `the table adds up to every seven-card hand`() {
        assertEquals(choose(52, 7), SevenCardFrequencies.TOTAL)
        assertEquals(SevenCardFrequencies.TOTAL, SevenCardFrequencies.counts.values.sum())
        assertEquals(HandRank.entries.toList(), SevenCardFrequencies.counts.keys.toList())
    }

    @Test
    fun `every count matches the count from rank and suit patterns`() {
        assertEquals(countFromPatterns(), SevenCardFrequencies.counts)
    }

    @Test
    fun `royal flushes are four times choosing the other two cards`() {
        assertEquals(4 * choose(47, 2), SevenCardFrequencies.count(HandRank.RoyalFlush))
    }

    @Test
    fun `the shares and 1-in figures the screen shows`() {
        assertEquals(0.00323, SevenCardFrequencies.percent(HandRank.RoyalFlush), 0.000005)
        assertEquals(30_940.0, SevenCardFrequencies.oneIn(HandRank.RoyalFlush), 0.05)
        assertEquals(43.82, SevenCardFrequencies.percent(HandRank.OnePair), 0.005)
        assertEquals(2.28, SevenCardFrequencies.oneIn(HandRank.OnePair), 0.005)
    }

    // ------------------------------------------------------------------ the independent count

    private val ranks = 13
    private val royal = 0b1_1111_0000_0000 // 10, J, Q, K, A (bit 0 = deuce)

    /**
     * Hands with five or more cards of one suit are flushes or better and can't be quads or a full
     * house (the other suits hold at most two of the seven cards): count them by the suited ranks.
     * Every other hand is counted by its rank pattern, times the suit choices that don't make five
     * of a suit.
     */
    private fun countFromPatterns(): Map<HandRank, Long> {
        val counts = HandRank.entries.associateWith { 0L }.toMutableMap()
        for (mask in 0 until (1 shl ranks)) {
            val suited = Integer.bitCount(mask)
            if (suited < 5) continue
            val others = choose(39, 7 - suited) // the rest from the other three suits
            val rank = when {
                mask and royal == royal -> HandRank.RoyalFlush
                hasStraight(mask) -> HandRank.StraightFlush
                else -> HandRank.Flush
            }
            counts[rank] = counts.getValue(rank) + 4 * others
        }
        forEachRankPattern(IntArray(ranks), 0, 7) { pattern ->
            val ways = pattern.fold(1L) { acc, m -> acc * choose(4, m) } - flushSuitings(pattern)
            if (ways > 0) {
                val rank = categoryOf(pattern)
                counts[rank] = counts.getValue(rank) + ways
            }
        }
        return HandRank.entries.associateWith { counts.getValue(it) }
    }

    private fun forEachRankPattern(pattern: IntArray, rank: Int, left: Int, action: (IntArray) -> Unit) {
        if (rank == ranks) {
            if (left == 0) action(pattern)
            return
        }
        for (m in 0..minOf(4, left)) {
            pattern[rank] = m
            forEachRankPattern(pattern, rank + 1, left - m, action)
        }
        pattern[rank] = 0
    }

    /** Suit choices for [pattern] with five or more cards of one suit (only one suit can have them). */
    private fun flushSuitings(pattern: IntArray): Long {
        val present = (0 until ranks).filter { pattern[it] > 0 }
        var total = 0L
        for (subset in 0 until (1 shl present.size)) {
            if (Integer.bitCount(subset) < 5) continue
            var ways = 1L
            present.forEachIndexed { i, r ->
                ways *= if (subset and (1 shl i) != 0) choose(3, pattern[r] - 1) else choose(3, pattern[r])
            }
            total += ways
        }
        return 4 * total
    }

    private fun categoryOf(pattern: IntArray): HandRank {
        val trips = pattern.count { it == 3 }
        val pairs = pattern.count { it == 2 }
        val mask = (0 until ranks).fold(0) { acc, r -> if (pattern[r] > 0) acc or (1 shl r) else acc }
        return when {
            pattern.any { it == 4 } -> HandRank.FourOfAKind
            trips >= 2 || (trips == 1 && pairs >= 1) -> HandRank.FullHouse
            hasStraight(mask) -> HandRank.Straight
            trips == 1 -> HandRank.ThreeOfAKind
            pairs >= 2 -> HandRank.TwoPair
            pairs == 1 -> HandRank.OnePair
            else -> HandRank.HighCard
        }
    }

    /** Five ranks in a row, or the wheel (A-2-3-4-5). */
    private fun hasStraight(mask: Int): Boolean {
        val wheel = 0b1_0000_0000_1111
        if (mask and wheel == wheel) return true
        return (0..ranks - 5).any { low -> (mask ushr low) and 0b11111 == 0b11111 }
    }

    private fun choose(n: Int, k: Int): Long {
        if (k < 0 || k > n) return 0
        var result = 1L
        for (i in 1..k) result = result * (n - k + i) / i
        return result
    }
}
