package com.huntercoles.fatline.basicfeature.poker

import org.junit.Assert.*
import org.junit.Test

class TexasHoldemOddsTest {

    @Test
    fun `evaluate five card categories basic`() {
        // Straight flush (A K Q J T hearts) vs Four of a kind
        val sf = listOf('A','K','Q','J','T').map { Card(it,'h') }
        val fk = listOf('A','A','A','A','K').map { Card(it,'d') }
        val vSf = evaluateBest(sf)
        val vFk = evaluateBest(fk)
        assertTrue("Straight flush should outrank four of a kind", vSf > vFk)
    }

    @Test
    fun `AA vs KK preflop equity sanity`() {
        val holes = listOf(
            listOf(Card('A','h'), Card('A','d')),
            listOf(Card('K','c'), Card('K','s'))
        )
        val results = simulateEquity(holes, emptyList(), iterations = 1500)
        val aa = results[0].winPct
        val kk = results[1].winPct
        assertTrue("AA should have significantly higher win % than KK", aa > kk + 10)
    }

    @Test
    fun `identical pocket aces equity distribution`() {
        val holes = listOf(
            listOf(Card('A','h'), Card('A','d')),
            listOf(Card('A','c'), Card('A','s'))
        )
        val iterations = 10000 // larger sample for tighter convergence
        val r = simulateEquity(holes, emptyList(), iterations = iterations)
        val targetWin = 2.17
        val targetTie = 95.65
        // Allow a reasonable Monte Carlo tolerance (sqrt(p*(1-p)/n) * 3 ~ 1% absolute here)
        assertEquals("Player1 win%", targetWin, r[0].winPct, 1.2)
        assertEquals("Player2 win%", targetWin, r[1].winPct, 1.2)
        assertEquals("Player1 tie%", targetTie, r[0].tiePct, 1.5)
        assertEquals("Player2 tie%", targetTie, r[1].tiePct, 1.5)
        // Symmetry check
        assertEquals(r[0].winPct, r[1].winPct, 1.0)
        assertEquals(r[0].tiePct, r[1].tiePct, 1.0)
    }

    @Test
    fun `post flop deterministic when board complete`() {
        val holes = listOf(
            listOf(Card('A','h'), Card('K','d')),
            listOf(Card('Q','c'), Card('J','s'))
        )
        val board = listOf(Card('2','h'), Card('3','d'), Card('4','c'), Card('5','s'), Card('9','h'))
        val r1 = simulateEquity(holes, board, iterations = 100)
        val r2 = simulateEquity(holes, board, iterations = 200)
        // Deterministic => identical results
        assertEquals(r1[0].winPct, r2[0].winPct, 0.0001)
        assertEquals(r1[1].winPct, r2[1].winPct, 0.0001)
    }
}
