package com.huntercoles.pokerpayout.tools.poker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exact-equity golden fixtures. Every number below was produced by exhaustive enumeration
 * of all runouts with an independent Python evaluator (and cross-checked against a second,
 * best-of-21 Python evaluator), never by the Kotlin code under test.
 *
 * The integer counts are asserted, not just the percentages: a correct engine gets them
 * exactly; a wrong one cannot hide inside a tolerance.
 */
class ExactEquityGoldenTest {

    private class Expected(val wins: Long, val ties: Long, val winPct: Double, val tiePct: Double, val equityPct: Double)

    private fun assertShowdown(
        holes: List<String>,
        board: String,
        deals: Long,
        vararg expected: Expected,
    ) {
        val s = OddsSubject.exact(holes, board)
        assertTrue(s.exact, "result for ${holes.joinToString(" vs ")} on [$board] should be marked exact")
        assertEquals(deals, s.deals, "runouts enumerated")
        val label = "${holes.joinToString(" vs ")} on [$board]"
        expected.forEachIndexed { i, e ->
            assertEquals(e.wins, s.wins[i], "$label: wins for ${holes[i]}")
            assertEquals(e.ties, s.ties[i], "$label: ties for ${holes[i]}")
            assertEquals(e.winPct, s.winPct(i), 0.005, "$label: win % for ${holes[i]}")
            assertEquals(e.tiePct, s.tiePct(i), 0.005, "$label: tie % for ${holes[i]}")
            assertEquals(e.equityPct, s.equityPct(i), 0.005, "$label: equity % for ${holes[i]}")
        }
        assertEquals(100.0, expected.indices.sumOf { s.equityPct(it) }, 1e-9, "$label: equities sum to one pot")
    }

    @Test
    fun `22 vs 98 on 9-7-5-4 turn - only a deuce saves the underpair`() = assertShowdown(
        listOf("2h 2d", "9h 8c"), "9s 7d 5c 4h", 44,
        Expected(2, 0, 4.55, 0.0, 4.55),
        Expected(42, 0, 95.45, 0.0, 95.45),
    )

    @Test
    fun `AK vs AQ on K-7-2 - kicker dominated`() = assertShowdown(
        listOf("Ah Kd", "As Qc"), "Kc 7d 2s", 990,
        Expected(971, 0, 98.08, 0.0, 98.08),
        Expected(19, 0, 1.92, 0.0, 1.92),
    )

    @Test
    fun `AJ vs KJ on J-7-2 - same pair, kicker battle with board-made splits`() = assertShowdown(
        listOf("Ah Jd", "Ks Jc"), "Jh 7d 2s", 990,
        Expected(858, 12, 86.67, 1.21, 87.27),
        Expected(120, 12, 12.12, 1.21, 12.73),
    )

    @Test
    fun `AKs vs QQ on J-T-2 two-tone - the device-tour hand (app showed 49_25 vs 50_75)`() = assertShowdown(
        listOf("As Ks", "Qh Qd"), "Js Ts 2c", 990,
        Expected(555, 0, 56.06, 0.0, 56.06),
        Expected(435, 0, 43.94, 0.0, 43.94),
    )

    @Test
    fun `78 vs 89 on 5-6-7 clubs - straights only (from the old test suite)`() = assertShowdown(
        listOf("7h 8h", "8c 9h"), "5c 6c 7c", 990,
        Expected(24, 69, 2.42, 6.97, 5.91),
        Expected(897, 69, 90.61, 6.97, 94.09),
    )

    /**
     * Turn, three players, 42 rivers. Hand-checkable: a 9 or a 4 (8 cards) puts a straight on
     * the board and all three chop; an A or K (4 cards) lets the two AK hands split;
     * the other 30 rivers go to the queens. Equity counts each player's share of split pots.
     */
    @Test
    fun `three-way turn with a three-way chop - equity counts thirds`() = assertShowdown(
        listOf("Qc Qs", "Ah Kh", "Ad Kd"), "5c 6d 7h 8s", 42,
        Expected(30, 8, 71.43, 19.05, 77.78),
        Expected(0, 12, 0.0, 28.57, 11.11),
        Expected(0, 12, 0.0, 28.57, 11.11),
    )

    @Test
    fun `three-way flop - AK vs AQ vs suited connector`() = assertShowdown(
        listOf("As Kd", "Ac Qh", "7s 6s"), "Ad 9s 4c", 903,
        Expected(688, 12, 76.19, 1.33, 76.85),
        Expected(107, 12, 11.85, 1.33, 12.51),
        Expected(96, 0, 10.63, 0.0, 10.63),
    )

    @Test
    fun `river showdown - AA beats Q2 even when the deuce pairs`() = assertShowdown(
        listOf("Ah Ad", "Qc 2s"), "Kh 9c 7d 4s 2h", 1,
        Expected(1, 0, 100.0, 0.0, 100.0),
        Expected(0, 0, 0.0, 0.0, 0.0),
    )
}
