package com.huntercoles.pokerpayout.tools.presentation.composable

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for Texas Hold'em poker odds calculation UI logic
 * Tests the simulateTexasHoldemOdds function that integrates with TexasHoldemOdds engine
 */
class PokerHandEvaluationTest {

    @Test
    fun `test simulate texas holdem odds - AA vs KK preflop`() = runTest {
        val players = listOf(
            Player(1, "Player 1", listOf(Card("A", "h"), Card("A", "d"))),
            Player(2, "Player 2", listOf(Card("K", "h"), Card("K", "d")))
        )
        val results = simulateTexasHoldemOdds(players, emptyList())
        
        assertEquals(2, results.size)
        // AA should generally beat KK
        assertTrue("AA should have higher or equal win percentage to KK", 
            results[0].winPercentage >= results[1].winPercentage)
        // Percentages should be valid
        assertTrue("Win percentages should be valid", 
            results.all { it.winPercentage >= 0.0 && it.winPercentage <= 100.0 })
        assertTrue("Tie percentages should be valid", 
            results.all { it.tiePercentage >= 0.0 && it.tiePercentage <= 100.0 })
    }

    @Test
    fun `test simulation with community cards - AA vs 2-3`() = runTest {
        val players = listOf(
            Player(1, "Player 1", listOf(Card("A", "h"), Card("A", "d"))),
            Player(2, "Player 2", listOf(Card("2", "h"), Card("3", "d")))
        )
        val communityCards = listOf(Card("A", "c"), Card("K", "s"), Card("Q", "h"))
        val results = simulateTexasHoldemOdds(players, communityCards)
        
        assertEquals(2, results.size)
        assertTrue("AAA should beat nothing", results[0].winPercentage > results[1].winPercentage)
    }

    @Test
    fun `test specific poker scenario for tie percentage functionality - 7h8h vs 8c9h with 5c6c7c flop`() = runTest {
        // Test case: Player 1: 7♥ 8♥, Player 2: 8♣ 9♥, Flop: 5♣ 6♣ 7♣
        // Expected from solved calculator: P1 Win 2.42% Tie 6.97%, P2 Win 90.61% Tie 6.97%

        val players = listOf(
            Player(1, "Player 1", listOf(Card("7", "h"), Card("8", "h"))),
            Player(2, "Player 2", listOf(Card("8", "c"), Card("9", "h")))
        )
        val communityCards = listOf(Card("5", "c"), Card("6", "c"), Card("7", "c"))
        val results = simulateTexasHoldemOdds(players, communityCards)

        assertEquals(2, results.size)

        // Check that results are calculated
        assertTrue("Player 1 should have valid win percentage", results[0].winPercentage >= 0.0)
        assertTrue("Player 1 should have valid tie percentage", results[0].tiePercentage >= 0.0)
        assertTrue("Player 2 should have valid win percentage", results[1].winPercentage >= 0.0)
        assertTrue("Player 2 should have valid tie percentage", results[1].tiePercentage >= 0.0)

        // Expected values from solved calculator (allowing some variance due to Monte Carlo)
        val expectedP1Win = 2.42
        val expectedP1Tie = 6.97
        val expectedP2Win = 90.61
        val expectedP2Tie = 6.97

        // Allow for some variance in Monte Carlo simulation (±3% for this scenario)
        assertEquals("Player 1 win percentage should match expected value", expectedP1Win, results[0].winPercentage, 3.0)
        assertEquals("Player 1 tie percentage should match expected value", expectedP1Tie, results[0].tiePercentage, 3.0)
        assertEquals("Player 2 win percentage should match expected value", expectedP2Win, results[1].winPercentage, 3.0)
        assertEquals("Player 2 tie percentage should match expected value", expectedP2Tie, results[1].tiePercentage, 3.0)
    }

    @Test
    fun `test identical pocket aces equity distribution`() = runTest {
        val players = listOf(
            Player(1, "Player 1", listOf(Card("A", "h"), Card("A", "d"))),
            Player(2, "Player 2", listOf(Card("A", "c"), Card("A", "s")))
        )
        val results = simulateTexasHoldemOdds(players, emptyList())
        
        assertEquals(2, results.size)
        // Should be very close to 50/50 with high tie percentage
        assertEquals("Player 1 win%", 2.17, results[0].winPercentage, 2.0)
        assertEquals("Player 2 win%", 2.17, results[1].winPercentage, 2.0)
        assertEquals("Player 1 tie%", 95.65, results[0].tiePercentage, 3.0)
        assertEquals("Player 2 tie%", 95.65, results[1].tiePercentage, 3.0)
    }
}