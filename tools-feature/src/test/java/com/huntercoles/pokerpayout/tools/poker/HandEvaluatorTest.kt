package com.huntercoles.pokerpayout.tools.poker

import java.util.BitSet
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Direct tests of [HandEvaluator] and [Cards]. The hand-ranking goldens live in
 * [HandRankingGoldenTest]; this file adds the exhaustive and randomized checks.
 */
class HandEvaluatorTest {

    /**
     * Every one of the 133,784,560 seven-card hands, against the published frequencies
     * (Wikipedia, "Poker probability", 7-card table): hands per category and the number of
     * distinct hand values per category, 4,824 in total.
     */
    @Test
    @Suppress("NestedBlockDepth") // one loop per card of an exhaustive enumeration
    fun `all 133,784,560 seven-card hands match the published category and rank counts`() {
        val hands = LongArray(9)
        val seen = BitSet(9 shl 20)
        val bit = LongArray(52) { Cards.bit(it) }
        for (a in 0 until 52) {
            val ma = bit[a]
            for (b in a + 1 until 52) {
                val mb = ma or bit[b]
                for (c in b + 1 until 52) {
                    val mc = mb or bit[c]
                    for (d in c + 1 until 52) {
                        val md = mc or bit[d]
                        for (e in d + 1 until 52) {
                            val me = md or bit[e]
                            for (f in e + 1 until 52) {
                                val mf = me or bit[f]
                                for (g in f + 1 until 52) {
                                    val s = HandEvaluator.evaluate(mf or bit[g])
                                    hands[s ushr 20]++
                                    seen.set(s)
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(
            listOf(23_294_460L, 58_627_800L, 31_433_400L, 6_461_620L, 6_180_020L, 4_047_644L, 3_473_184L, 224_848L, 41_584L),
            hands.toList(),
            "seven-card hands per category, HIGH_CARD..STRAIGHT_FLUSH",
        )
        val distinct = (0 until 9).map { cat -> seen.get(cat shl 20, (cat + 1) shl 20).cardinality() }
        assertEquals(listOf(407, 1470, 763, 575, 10, 1277, 156, 156, 10), distinct, "distinct seven-card values per category")
        assertEquals(4824, distinct.sum())
    }

    /** 200,000 random 7-card hands (fixed seed) agree with the brute-force best-of-21 reference. */
    @Test
    fun `random seven-card hands agree with the brute-force reference`() =
        agreesWithReference(cards = 7, hands = 200_000, seed = 7_2026)

    @Test
    fun `random six-card hands agree with the brute-force reference`() =
        agreesWithReference(cards = 6, hands = 50_000, seed = 6_2026)

    @Test
    fun `random five-card hands agree with the brute-force reference`() =
        agreesWithReference(cards = 5, hands = 50_000, seed = 5_2026)

    /**
     * For each hand, the strength must decode to exactly the reference key (category and
     * every tie-break rank), and each consecutive pair must compare the same way under both.
     */
    private fun agreesWithReference(cards: Int, hands: Int, seed: Int) {
        val rnd = Random(seed)
        val deck = IntArray(52) { it }
        var previous: Pair<Int, List<Int>>? = null
        var orderChecks = 0
        repeat(hands) { i ->
            for (j in 0 until cards) {
                val r = j + rnd.nextInt(52 - j)
                val t = deck[j]; deck[j] = deck[r]; deck[r] = t
            }
            val hand = deck.take(cards)
            val s = HandEvaluator.evaluate(hand)
            val ref = ReferenceEvaluator.best(hand)
            val decoded = listOf(HandEvaluator.category(s).ordinal) + HandEvaluator.ranks(s)
            assertEquals(ref, decoded, "hand #$i ${Cards.format(hand)}")
            previous?.let { (ps, pref) ->
                val expected = Integer.signum(ReferenceEvaluator.compareKeys(ref, pref))
                assertEquals(expected, Integer.signum(s.compareTo(ps)), "order of ${Cards.format(hand)} vs previous hand")
                orderChecks++
            }
            previous = s to ref
        }
        assertEquals(hands - 1, orderChecks)
    }

    @Test
    fun `describe names the made hand`() {
        fun d(hand: String) = HandEvaluator.describe(HandEvaluator.evaluate(Cards.parseAll(hand)))
        assertEquals("Royal flush", d("Ah Kh Qh Jh Th 2c 3d"))
        assertEquals("Straight flush, five high", d("Ad 2d 3d 4d 5d Kc Kh"))
        assertEquals("Four nines", d("9s 9h 9d 9c 3h"))
        assertEquals("Full house, kings full of queens", d("Ks Kh Kd Qc Qh Qs 2d"))
        assertEquals("Flush, ace high", d("Ah 9h 7h 5h 3h"))
        assertEquals("Straight, six high", d("Ah 2c 3d 4s 5h 6c Kd"))
        assertEquals("Three sevens", d("7s 7h 7d Ac 2h"))
        assertEquals("Two pair, aces and kings", d("As Ah Ks Kh Qs Qh 2d"))
        assertEquals("Pair of deuces", d("2s 2h Ad Kc Qh"))
        assertEquals("Ace high", d("As Kd 9c 5h 3s"))
    }

    @Test
    fun `evaluate rejects duplicates and wrong hand sizes`() {
        assertThrows(IllegalArgumentException::class.java) { HandEvaluator.evaluate(Cards.parseAll("As As Kd Qc Jh")) }
        assertThrows(IllegalArgumentException::class.java) { HandEvaluator.evaluate(Cards.parseAll("As Kd Qc Jh")) }
        assertThrows(IllegalArgumentException::class.java) { HandEvaluator.evaluate(Cards.parseAll("As Kd Qc Jh Th 9h 8h 7h")) }
    }

    @Test
    fun `cards parse and format`() {
        assertEquals(0, Cards.parse("2c"))
        assertEquals(51, Cards.parse("As"))
        assertEquals(Cards.of(rank = 8, suit = 2), Cards.parse("Th"))
        assertEquals(Cards.parse("Th"), Cards.parse("10h"))
        assertEquals(Cards.parse("Qd"), Cards.parse("qD"))
        assertEquals(listOf("As", "Kd", "Th"), Cards.parseAll("As Kd,Th").map(Cards::format))
        assertEquals(listOf("As", "Kd", "Th"), Cards.parseAll("AsKdTh").map(Cards::format))
        assertEquals(emptyList<Int>(), Cards.parseAll("  "))
        assertEquals((0 until 52).toList(), (0 until 52).map { Cards.parse(Cards.format(it)) })
        assertEquals(52, Cards.count((0 until 52).fold(0L) { m, c -> m or Cards.bit(c) }))
        assertThrows(IllegalArgumentException::class.java) { Cards.parse("1s") }
        assertThrows(IllegalArgumentException::class.java) { Cards.parse("Ax") }
        assertThrows(IllegalArgumentException::class.java) { Cards.parse("") }
    }
}
