package com.huntercoles.pokerpayout.tools.poker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Preflop heads-up sanity checks, through whatever path the app uses for preflop odds.
 *
 * Truth values: exhaustive enumeration of all 1,712,304 boards with an independent Python
 * evaluator. They agree with the commonly published figures (AA vs KK ~82/18, 88 vs AKo
 * ~55/45, AKs vs QQ ~46/54). The tolerance is wide enough for a Monte Carlo estimate
 * (a few standard errors at 20k samples) and far too narrow for the kicker bug, which
 * moved these numbers by 2 to 12 points.
 */
class PreflopEquitySanityTest {

    private class Matchup(
        val a: String,
        val b: String,
        val aWin: Double,
        val bWin: Double,
        val tie: Double,
        val aEquity: Double,
    )

    private val tolerance = 1.0

    private val matchups = listOf(
        // As Ah vs Ks Kd: 1,399,204 / 305,177 / 7,923 ties of 1,712,304.
        Matchup("As Ah", "Ks Kd", aWin = 81.71, bWin = 17.82, tie = 0.46, aEquity = 81.95),
        // 8c 8d vs Ah Ks: 948,978 / 758,262 / 5,064.
        Matchup("8c 8d", "Ah Ks", aWin = 55.42, bWin = 44.28, tie = 0.30, aEquity = 55.57),
        // As Ks vs Qh Qd: 787,966 / 917,606 / 6,732.
        Matchup("As Ks", "Qh Qd", aWin = 46.02, bWin = 53.59, tie = 0.39, aEquity = 46.21),
        // Ah Ad vs Ac As: 37,210 each / 1,637,884 ties. A control: only flushes decide it.
        Matchup("Ah Ad", "Ac As", aWin = 2.17, bWin = 2.17, tie = 95.65, aEquity = 50.0),
    )

    @TestFactory
    fun `heads-up preflop equities`(): List<DynamicTest> = matchups.map { m ->
        dynamicTest("${m.a} vs ${m.b}") {
            val s = OddsSubject.preflop(m.a, m.b)
            val label = "${m.a} vs ${m.b}"
            assertEquals(m.aWin, s.winPct(0), tolerance, "$label: win % for ${m.a}")
            assertEquals(m.bWin, s.winPct(1), tolerance, "$label: win % for ${m.b}")
            assertEquals(m.tie, s.tiePct(0), tolerance, "$label: tie %")
            assertEquals(m.aEquity, s.equityPct(0), tolerance, "$label: equity % for ${m.a}")
        }
    }
}
