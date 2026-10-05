package com.huntercoles.pokerpayout.tools.poker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Golden hand-ranking table. Every row is a known poker fact, written by hand.
 *
 * Kicker rows are built so the winner wins at the named position while the loser holds
 * HIGHER cards in every later position. An evaluator that compares kickers in the wrong
 * order (bug B1 / PP-011) therefore picks the wrong winner on those rows.
 */
class HandRankingGoldenTest {

    private class Beats(val winner: String, val loser: String, val why: String)
    private class Splits(val a: String, val b: String, val why: String)

    private val fiveCardBeats = listOf(
        // High card: each kicker position.
        Beats("Ah Jd 9c 7s 5h", "Kc Qd Js 9h 8c", "high card: 1st card A > K"),
        Beats("Ah Qd 4c 3s 2h", "Ac Jd Ts 9h 7c", "high card: 2nd card Q > J"),
        Beats("Ah Kd 9c 3s 2h", "Ac Kc 8d 7h 6s", "high card: 3rd card 9 > 8"),
        Beats("Ah Kd 9c 6s 2h", "Ac Ks 9d 5h 4c", "high card: 4th card 6 > 5"),
        Beats("Ah Kd 9c 6s 3h", "Ac Ks 9d 6h 2c", "high card: 5th card 3 > 2"),
        // One pair: pair rank, then three kickers.
        Beats("Ac Ah 4d 3c 2h", "2s 2h Ad Kc Qh", "pair: AA-432 > 22-AKQ"),
        Beats("Ks Kh Ad 3c 2h", "Kc Kd Qh Js Th", "pair: 1st kicker A > Q"),
        Beats("9s 9h Ad Qc 2h", "9c 9d Ah Js Th", "pair: 2nd kicker Q > J"),
        Beats("5s 5h Ad Kc 4h", "5c 5d Ah Ks 3h", "pair: 3rd kicker 4 > 3"),
        // Two pair: high pair, low pair, kicker.
        Beats("Ks Kh 3d 3c 2h", "Qc Qd Jh Js Ah", "two pair: KK33 > QQJJ"),
        Beats("Ac Ad Kh Ks 4c", "3c 3d 2h 2s Ah", "two pair: AAKK > 3322"),
        Beats("As Ah 4d 4c 2h", "Ac Ad 3h 3s Kh", "two pair: low pair 44 > 33"),
        Beats("Js Jh 8d 8c 5h", "Jc Jd 8h 8s 4c", "two pair: kicker 5 > 4"),
        // Trips: trip rank, then two kickers.
        Beats("3s 3h 3d 4c 2h", "2c 2d 2s Ah Kh", "trips: 333 > 222-AK"),
        Beats("7s 7h 7d Ac 2h", "7c 7h 7d Kc Qs", "trips: 1st kicker A > K"),
        Beats("7s 7h 7d Ac 4h", "7c 7h 7d Ah 3s", "trips: 2nd kicker 4 > 3"),
        // Straights.
        Beats("6s 5h 4d 3c 2h", "5s 4h 3d 2c Ah", "straight: 6-high > wheel"),
        Beats("Ts 9h 8d 7c 6h", "9s 8h 7d 6c 5h", "straight: T-high > 9-high"),
        Beats("As Kh Qd Jc Th", "Ks Qh Jd Tc 9h", "straight: broadway > K-high"),
        // Flush: all five cards, in order.
        Beats("Ah 9h 7h 5h 3h", "Kd Qd Jd 9d 8d", "flush: 1st card A > K"),
        Beats("As Ks 4s 3s 2s", "Ad Qd Jd Td 8d", "flush: 2nd card K > Q"),
        Beats("Ac Kc Qc 4c 2c", "Ah Kh Jh Th 9h", "flush: 3rd card Q > J"),
        Beats("Ac Kc Qc 7c 2c", "Ah Kh Qh 6h 5h", "flush: 4th card 7 > 6"),
        Beats("Ac Kc Qc 7c 4c", "Ah Kh Qh 7h 3h", "flush: 5th card 4 > 3"),
        Beats("Ad Kd Qd 7d 4d", "As Ks 7s 6s 5s", "flush: AKQ74 > AK765"),
        // Full house: trips first, then the pair.
        Beats("3s 3h 3d 2c 2h", "2c 2d 2s Ah Ad", "full house: 33322 > 222AA"),
        Beats("As Ah Ad Kc Kh", "2s 2h 2d Ac Ah", "full house: AAAKK > 222AA"),
        Beats("Qs Qh Qd 2c 2h", "Js Jh Jd Ac Ah", "full house: QQQ22 > JJJAA"),
        Beats("Ks Kh Kd 4c 4h", "Kc Ks Kh 3d 3s", "full house: pair 44 > 33"),
        // Quads: quad rank, then kicker.
        Beats("3s 3h 3d 3c 2h", "2s 2h 2d 2c Ah", "quads: 3333 > 2222-A"),
        Beats("Ks Kh Kd Kc 3h", "2s 2h 2d 2c Ah", "quads: KKKK-3 > 2222-A"),
        Beats("9s 9h 9d 9c 3h", "9s 9h 9d 9c 2h", "quads: kicker 3 > 2"),
        // Straight flushes.
        Beats("6h 5h 4h 3h 2h", "5d 4d 3d 2d Ad", "straight flush: 6-high > steel wheel"),
        Beats("Ah Kh Qh Jh Th", "Kc Qc Jc Tc 9c", "straight flush: royal > K-high"),
        // Category boundaries: the worst hand of each category beats the best of the next.
        Beats("5d 4d 3d 2d Ad", "As Ah Ad Ac Kh", "steel wheel > best quads"),
        Beats("2s 2h 2d 2c 3h", "As Ah Ad Kc Kh", "worst quads > best full house"),
        Beats("2s 2h 2d 3c 3h", "Ah Kh Qh Jh 9h", "worst full house > best flush"),
        Beats("7c 5c 4c 3c 2c", "As Kh Qd Jc Th", "worst flush > best straight"),
        Beats("5s 4h 3d 2c Ah", "As Ah Ad Kc Qh", "wheel > best trips"),
        Beats("2s 2h 2d 4c 3h", "As Ah Kd Kc Qh", "worst trips > best two pair"),
        Beats("3s 3h 2d 2c 4h", "As Ah Kd Qc Jh", "worst two pair > best pair"),
        Beats("2s 2h 5d 4c 3h", "As Kh Qd Jc 9h", "worst pair > best high card"),
    )

    private val sevenCardBeats = listOf(
        Beats("As Ah Kd Qc Jh 3s 2d", "Ac Ad Kh Qs Td 9c 8h", "7 cards: pair, 3rd kicker J > T; 9-8 vs 3-2 do not play"),
        Beats("Ks Kh 8d 8c 6h 6s 2d", "Kc Kd 8h 8s 5c 4d 3h", "7 cards: three pairs, the third pair is the kicker (6 > 5)"),
        Beats("Ah Kh 9h 7h 5h 3h 2c", "Ad Kd 9d 7d 4d 3d 2d", "6-card flush AK975 > 7-card flush AK974"),
        Beats("Kc 3d 9s 9h 9d 9c 2c", "Qc Jd 9s 9h 9d 9c 2c", "quads on board: hole kicker K > Q"),
        Beats("Ks Kh Kd 2c 2h 2s 3d", "Qs Qh Qd Ac Ah 4c 5d", "two trips: KKK22 > QQQAA"),
        Beats("9h 8h 7h 6h 5h Ts Jd", "As Ad Ah Ac Kd Qd Jc", "7 cards: 9-high straight flush > quads"),
    )

    private val splits = listOf(
        Splits("As Kd 9c 5h 3s", "Ah Kc 9d 5s 3h", "same high-card ranks, different suits"),
        Splits("Ah 9h 7h 5h 3h", "As 9s 7s 5s 3s", "flush suit does not matter"),
        Splits("5s 4h 3d 2c Ah", "5h 4d 3c 2s Ad", "wheel vs wheel"),
        Splits("2c 3d Ah Kh Qd Js Tc", "4c 5d Ah Kh Qd Js Tc", "board plays: broadway on board"),
        Splits("2c 3d 9s 9h 9d 9c Ac", "Kc Qd 9s 9h 9d 9c Ac", "board plays: quads with the ace kicker on board"),
        Splits("As Ah Ks Kh 2s 2h Qd", "Ac Ad Kc Kd Qs 3c 4d", "both play AAKK-Q"),
    )

    /** Best-of-7 must equal the strength of the named best five cards. */
    private val sevenCardBestFive = listOf(
        "Ks Kh Kd Qc Qh Qs 2d" to "Ks Kh Kd Qc Qh",  // two trips make a full house, higher trips on top
        "Js Jh Jd 8c 8h 8s Ad" to "Js Jh Jd 8c 8h",  // two trips beat the ace kicker
        "9s 9h 9d 5c 5h 4s 4d" to "9s 9h 9d 5c 5h",  // trips + two pairs: higher pair fills
        "As Ah Ks Kh Qs Qh 2d" to "As Ah Ks Kh Qs",  // three pairs: third pair is the kicker
        "As Ah Ks Kh 2s 2h Qd" to "As Ah Ks Kh Qd",  // three pairs: a higher single beats the third pair
        "Ah Kh 9h 7h 5h 3h 2c" to "Ah Kh 9h 7h 5h",  // 6-card flush: top five
        "Ac Jc 9c 7c 5c 3c 2c" to "Ac Jc 9c 7c 5c",  // 7-card flush: top five
        "9h 8h 7h 6h 5h Ts Jd" to "9h 8h 7h 6h 5h",  // straight flush beats a higher plain straight
        "Ah 9h 7h 5h 2h 6c 8d" to "Ah 9h 7h 5h 2h",  // flush beats a straight
        "Ah 2c 3d 4s 5h Kd Kc" to "5h 4s 3d 2c Ah",  // wheel beats a pair
        "Ah 2c 3d 4s 5h 6c Kd" to "6c 5h 4s 3d 2c",  // 6-high straight beats the wheel
        "Ah 2h 3h 4h 5h 6c 7d" to "5h 4h 3h 2h Ah",  // steel wheel beats a 7-high straight
        "Ts 9h 8d 7c 6s 5h 4d" to "Ts 9h 8d 7c 6s",  // 7-card run: top straight
        "7s 7h 7d 7c 9s 9h 9d" to "7s 7h 7d 7c 9s",  // quads take the best kicker from trips
        "4s 4h 4d 4c Ks Kh Ad" to "4s 4h 4d 4c Ad",  // quads: ace kicker beats a pair
        "As Ah Kd Qc Jh 3s 2d" to "As Ah Kd Qc Jh",  // pair: low cards do not play
    )

    private val categories = listOf(
        "As Kd 9c 5h 3s" to GoldenCategory.HIGH_CARD,
        "2s 2h Ad Kc Qh" to GoldenCategory.PAIR,
        "Ks Kh 3d 3c 2h" to GoldenCategory.TWO_PAIR,
        "7s 7h 7d Ac 2h" to GoldenCategory.TRIPS,
        "5s 4h 3d 2c Ah" to GoldenCategory.STRAIGHT,
        "Ah 9h 7h 5h 3h" to GoldenCategory.FLUSH,
        "Ks Kh Kd 4c 4h" to GoldenCategory.FULL_HOUSE,
        "9s 9h 9d 9c 3h" to GoldenCategory.QUADS,
        "5d 4d 3d 2d Ad" to GoldenCategory.STRAIGHT_FLUSH,
        "Ah Kh Qh Jh Th" to GoldenCategory.STRAIGHT_FLUSH,
        "As Kd Qc 9h 7s 4d 2c" to GoldenCategory.HIGH_CARD,
        "Ks Kh Kd Qc Qh Qs 2d" to GoldenCategory.FULL_HOUSE,
        "As Ah Ks Kh Qs Qh 2d" to GoldenCategory.TWO_PAIR,
        "Ah 9h 7h 5h 2h 6c 8d" to GoldenCategory.FLUSH,
        "Ah 2c 3d 4s 5h Kd Kc" to GoldenCategory.STRAIGHT,
        "Ah 2h 3h 4h 5h 6c 7d" to GoldenCategory.STRAIGHT_FLUSH,
        "7s 7h 7d 7c 9s 9h 9d" to GoldenCategory.QUADS,
    )

    @TestFactory
    fun `five-card hands rank in the right order`(): List<DynamicTest> = beatsTests(fiveCardBeats)

    @TestFactory
    fun `seven-card hands rank in the right order`(): List<DynamicTest> = beatsTests(sevenCardBeats)

    @TestFactory
    fun `equal hands split`(): List<DynamicTest> = splits.map { row ->
        dynamicTest(row.why) {
            assertEquals(
                OddsSubject.strength(row.a), OddsSubject.strength(row.b),
                "${row.a} and ${row.b} should split (${row.why})",
            )
        }
    }

    @TestFactory
    fun `best five of seven is the named hand`(): List<DynamicTest> = sevenCardBestFive.map { (seven, five) ->
        dynamicTest("$seven -> $five") {
            assertEquals(OddsSubject.strength(five), OddsSubject.strength(seven), "best five of $seven should be $five")
        }
    }

    @TestFactory
    fun `hand categories`(): List<DynamicTest> = categories.map { (hand, category) ->
        dynamicTest("$hand is $category") { assertEquals(category, OddsSubject.category(hand)) }
    }

    /**
     * Every 5-card hand from a 52-card deck, checked against the published counts: 2,598,960
     * hands falling into 7,462 distinct ranks (10 straight-flush ranks, 156 quads, 156 full
     * houses, 1,277 flushes, 10 straights, 858 trips, 858 two pair, 2,860 pairs and 1,277
     * high-card ranks).
     */
    @Test
    fun `all 2,598,960 five-card hands match the published category and rank counts`() {
        val hands = LongArray(GoldenCategory.entries.size)
        val distinct = Array(GoldenCategory.entries.size) { HashSet<Int>() }
        val codes = IntArray(5)
        for (a in 0 until 52) for (b in a + 1 until 52) for (c in b + 1 until 52)
            for (d in c + 1 until 52) for (e in d + 1 until 52) {
                codes[0] = a; codes[1] = b; codes[2] = c; codes[3] = d; codes[4] = e
                val s = OddsSubject.strengthOfDeckIndices(codes)
                val cat = OddsSubject.categoryOfStrength(s).ordinal
                hands[cat]++
                distinct[cat].add(s)
            }
        assertEquals(
            listOf(1_302_540L, 1_098_240L, 123_552L, 54_912L, 10_200L, 5_108L, 3_744L, 624L, 40L),
            hands.toList(),
            "hands per category, HIGH_CARD..STRAIGHT_FLUSH",
        )
        assertEquals(
            listOf(1277, 2860, 858, 858, 10, 1277, 156, 156, 10),
            distinct.map { it.size },
            "distinct ranks per category",
        )
        assertEquals(7462, distinct.sumOf { it.size })
    }

    private fun beatsTests(rows: List<Beats>): List<DynamicTest> = rows.map { row ->
        dynamicTest(row.why) {
            val w = OddsSubject.strength(row.winner)
            val l = OddsSubject.strength(row.loser)
            assertTrue(w > l, "${row.winner} should beat ${row.loser} (${row.why})")
        }
    }
}
