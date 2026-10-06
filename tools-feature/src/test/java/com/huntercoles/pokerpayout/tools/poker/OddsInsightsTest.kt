package com.huntercoles.pokerpayout.tools.poker

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Hand labels, outs groups, "By the river" and run-it-out dealing (PP-028). */
class OddsInsightsTest {

    /** A description in a compact form: "KIND ranks [suited] + DRAW + DRAW". Ranks as letters. */
    private fun describe(hole: String, board: String): String {
        val d = OddsInsights.describe(Cards.parseAll(hole), Cards.parseAll(board))
        val ranks = d.made.ranks.joinToString("") { Cards.RANK_CHARS[it].toString() }
        val made = listOfNotNull(d.made.kind.name, ranks.ifEmpty { null }, if (d.made.suited) "suited" else null)
            .joinToString(" ")
        return (listOf(made) + d.draws.map { it.name }).joinToString(" + ")
    }

    /**
     * The golden table. Each row: hole cards, board, expected description. The first two rows are
     * the mockups' labels ("Nut flush draw + gutshot", "Overpair, queens").
     */
    @ParameterizedTest(name = "{0} on [{1}] is {2}")
    @CsvSource(
        delimiter = '|',
        value = [
            "As Ks | Js Ts 2c       | HIGH_CARD A + NUT_FLUSH_DRAW + GUTSHOT",
            "Qh Qd | Js Ts 2c       | OVERPAIR Q",
            // before the flop
            "As Ks |                | UNPAIRED AK suited",
            "Ah Kd |                | UNPAIRED AK",
            "7c 7d |                | POCKET_PAIR 7",
            "Qh 4c | Qd 2s          | UNPAIRED Q4",
            // pairs
            "6c 6d | Js Ts 2c       | POCKET_PAIR 6",
            "Ah Jd | Jc 7d 2s       | TOP_PAIR J",
            "Kh 7c | Jc 7d 2s       | SECOND_PAIR 7",
            "Ah 2c | Jc 7d 2s       | BOTTOM_PAIR 2",
            "Ah 5c | Jc 7d 5s 2h    | MIDDLE_PAIR 5",
            "Ah Kc | Jc Jd 2s       | BOARD_PAIR J",
            // two pair and trips
            "Kh Jc | Kc Jd 2s       | TWO_PAIR KJ",
            "Qh Qd | Qc 7d 2s       | SET Q",
            "Ah Qd | Qc Qs 2s       | TRIPS Q",
            "Ah Kd | Qc Qs Qd       | BOARD_TRIPS Q",
            // straights and better (no draws named once a flush is made)
            "9h 8d | 7c 6s 5d       | STRAIGHT 9",
            "Ah 2h | 3c 4s 5d       | STRAIGHT 5",
            "Ah 3h | Kh 9h 2h       | FLUSH A",
            "Qh Qd | Qc 7d 7s       | FULL_HOUSE Q7",
            "7h 7c | 7d 7s 2s       | QUADS 7",
            "9h 8h | 7h 6h 5h       | STRAIGHT_FLUSH 9",
            "Ah Kh | Qh Jh Th       | ROYAL_FLUSH",
            // draws
            "9h 8d | 7c 6s 2d       | HIGH_CARD 9 + OPEN_ENDED",
            "2h 3d | 4c 5s Kh       | HIGH_CARD K + OPEN_ENDED",
            "9h 7d | 6c 5s Jd       | HIGH_CARD J + GUTSHOT",
            "9h 7d | 6c 5s 3d       | HIGH_CARD 9 + DOUBLE_GUTSHOT",
            "Ah 2d | 3c 4s 9h       | HIGH_CARD A + GUTSHOT",
            "Ah Kh | Qh 7h 2c       | HIGH_CARD A + NUT_FLUSH_DRAW",
            "Kh 9h | Ah 7h 2c       | HIGH_CARD A + NUT_FLUSH_DRAW",
            "Jh 9h | Ah 7h 2c       | HIGH_CARD A + FLUSH_DRAW",
            "Kd Jd | Jc Td 9h       | TOP_PAIR J + GUTSHOT",
            "Ah Th | Kh 9h 2c Qd    | HIGH_CARD A + NUT_FLUSH_DRAW + GUTSHOT",
            "8s 7s | 6h 5d 4s       | STRAIGHT 8",
            // a draw on the board alone isn't the player's draw
            "Ah 2c | 9s 8d 7c 6h    | HIGH_CARD A",
            "As Ks | Js Ts 2c 7h    | HIGH_CARD A + NUT_FLUSH_DRAW + GUTSHOT",
            // no draws on the river
            "As Ks | Js Ts 2c 7h 3d | HIGH_CARD A",
        ],
    )
    fun `describe golden table`(hole: String, board: String?, expected: String) {
        assertEquals(expected, describe(hole, board ?: ""))
    }

    @Test
    fun `the flush draw's suit is reported for the outs`() {
        val d = OddsInsights.describe(Cards.parseAll("As Ks"), Cards.parseAll("Js Ts 2c"))
        assertEquals(Cards.SUIT_CHARS.indexOf('s'), d.flushDrawSuit)
        assertNull(OddsInsights.describe(Cards.parseAll("Qh Qd"), Cards.parseAll("Js Ts 2c")).flushDrawSuit)
    }

    @Test
    fun `describe rejects bad input`() {
        assertThrows(IllegalArgumentException::class.java) { OddsInsights.describe(Cards.parseAll("As"), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            OddsInsights.describe(Cards.parseAll("As Ks"), Cards.parseAll("As 2c 3d"))
        }
    }

    @Test
    fun `made leader is the best hand right now, or nobody`() {
        fun leader(vararg seats: String, board: String) =
            OddsInsights.madeLeader(OddsRequest(seats.map { Seat.of(it) }, Cards.parseAll(board)))
        assertEquals(1, leader("As Ks", "Qh Qd", board = "Js Ts 2c"))
        assertEquals(0, leader("As Ks", "Qh Qd", board = "Js Ts 2c Ac"))
        assertNull(leader("As Ks", "Qh Qd", board = ""), "nothing is made before the flop")
        assertNull(leader("As Ks", "", board = "Js Ts 2c"), "a random hand can't be compared")
        assertNull(leader("2c 3d", "2h 3s", board = "As Ks Qd"), "the same hand")
    }

    @Test
    fun `outs are grouped the way people say them`() {
        val outs = Cards.parseAll("Qs 9s 8s 7s 6s 5s 4s 3s 2s Ah Ad Ac Kh Kd Kc Qc").shuffled(kotlin.random.Random(1))
        val groups = OddsInsights.groupOuts(outs, flushSuit = Cards.SUIT_CHARS.indexOf('s'))
        // "9 spades, 3 aces, 3 kings and the Q♣"
        assertEquals(
            listOf(
                OutGroup.Suit(Cards.SUIT_CHARS.indexOf('s'), 9),
                OutGroup.Rank(Cards.ACE, 3),
                OutGroup.Rank(Cards.ACE - 1, 3),
                OutGroup.Single(Cards.parse("Qc")),
            ),
            groups,
        )
        // No flush draw: the spades are just cards of their ranks.
        val noFlush = OddsInsights.groupOuts(Cards.parseAll("9s 9h 2c"), flushSuit = null)
        assertEquals(listOf(OutGroup.Rank(7, 2), OutGroup.Single(Cards.parse("2c"))), noFlush)
    }

    @Test
    fun `by the river for the mockup hand`() {
        val result = runBlocking { OddsEngine().finalResult(runRequest) }

        val p1 = OddsInsights.riverOutlook(result.players[0].handCategoryPct)
        assertEquals(listOf("FLUSH+", "PAIR", "HIGH_CARD", "TWO_PAIR"), p1.map { it.label() })
        assertEquals(listOf(36.4, 31.8, 19.1, 7.9), p1.map { round1(it.pct) })

        val p2 = OddsInsights.riverOutlook(result.players[1].handCategoryPct)
        assertEquals(listOf("PAIR", "TWO_PAIR", "TRIPS", "STRAIGHT+"), p2.map { it.label() })
        assertEquals(listOf(48.1, 38.5, 6.9, 6.6), p2.map { round1(it.pct) })
    }

    @Test
    fun `by the river keeps four rows or fewer and never drops a common category`() {
        val few = OddsInsights.riverOutlook(pct(HandCategory.PAIR to 70.0, HandCategory.TWO_PAIR to 30.0))
        assertEquals(listOf("PAIR", "TWO_PAIR"), few.map { it.label() })

        // Five categories: the rare strong ones merge until the bucket reaches 5%.
        val five = OddsInsights.riverOutlook(
            pct(
                HandCategory.HIGH_CARD to 45.0, HandCategory.PAIR to 35.0, HandCategory.TWO_PAIR to 10.0,
                HandCategory.STRAIGHT to 6.0, HandCategory.FLUSH to 4.0,
            ),
        )
        assertEquals(listOf("HIGH_CARD", "PAIR", "STRAIGHT+", "TWO_PAIR"), five.map { it.label() })
        assertEquals(10.0, five[2].pct, 1e-9)

        // Tiny numbers (Monte Carlo noise) don't get a row.
        val noisy = OddsInsights.riverOutlook(pct(HandCategory.PAIR to 99.98, HandCategory.QUADS to 0.02))
        assertEquals(listOf("PAIR"), noisy.map { it.label() })
        assertEquals(emptyList<OutlookRow>(), OddsInsights.riverOutlook(emptyList()))
    }

    // ------------------------------------------------------------------ run it out

    private val runRequest = OddsRequest(listOf(Seat.of("As Ks"), Seat.of("Qh Qd")), Cards.parseAll("Js Ts 2c"))

    @Test
    fun `the same seed and run deal the same board`() {
        val a = RunOuts.deal(runRequest, seed = 42, run = 0)
        val b = RunOuts.deal(runRequest, seed = 42, run = 0)
        assertEquals(a, b)
        assertEquals(1, a.size)
        assertEquals(2, a[0].size, "a flop needs the turn and the river")
        val known = Cards.parseAll("As Ks Qh Qd Js Ts 2c")
        assertTrue(a[0].none { it in known })

        // Another run, or another seed, gives other cards (checked over many runs).
        val runs = (0 until 20).map { RunOuts.deal(runRequest, seed = 42, run = it)[0] }
        assertTrue(runs.toSet().size >= 19, "runs should differ: $runs")
        assertNotEquals((0 until 20).map { RunOuts.deal(runRequest, seed = 7, run = it)[0] }, runs)
    }

    @Test
    fun `the deal is pinned for seed 42`() {
        // Pins the shuffle so a change to it (which would change every replayed run) is noticed.
        assertEquals(PINNED_SEED_42, Cards.format(RunOuts.deal(runRequest, seed = 42, run = 0)[0]))
    }

    @Test
    fun `run it twice deals two boards from one deck`() {
        val (one, two) = RunOuts.deal(runRequest, seed = 3, run = 5, count = 2)
        assertEquals(2, one.size)
        assertEquals(2, two.size)
        assertTrue(one.intersect(two.toSet()).isEmpty(), "no card comes twice")
        val preflop = RunOuts.deal(OddsRequest(listOf(Seat.of("As Ks"), Seat.of("Qh Qd"))), seed = 3, run = 0, count = 2)
        assertEquals(listOf(5, 5), preflop.map { it.size })
        assertEquals(10, preflop.flatten().toSet().size)
    }

    @Test
    fun `run it out needs every hand known`() {
        val e = assertThrows(OddsInputException::class.java) {
            RunOuts.deal(OddsRequest(listOf(Seat.of("As Ks"), Seat()), emptyList()), seed = 1, run = 0)
        }
        assertEquals("Every hand must be known to run it out.", e.message)
    }

    private fun OutlookRow.label(): String = if (isRange) "${weakest.name}+" else weakest.name

    private fun round1(x: Double) = Math.round(x * 10) / 10.0

    private fun pct(vararg values: Pair<HandCategory, Double>): List<Double> {
        val map = values.toMap()
        return HandCategory.entries.map { map[it] ?: 0.0 }
    }

    private companion object {
        const val PINNED_SEED_42 = "7c 4d"
    }
}
