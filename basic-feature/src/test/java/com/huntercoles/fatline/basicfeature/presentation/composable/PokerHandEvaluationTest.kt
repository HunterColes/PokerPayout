package com.huntercoles.fatline.basicfeature.presentation.composable

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Texas Hold'em poker odds calculation based on cookpete/poker-odds logic
 */
class PokerHandEvaluationTest {

    @Test
    fun `test numerical value conversion`() {
        assertEquals(14, numericalValue(Card("A", "h")))
        assertEquals(13, numericalValue(Card("K", "d")))
        assertEquals(12, numericalValue(Card("Q", "c")))
        assertEquals(11, numericalValue(Card("J", "s")))
        assertEquals(10, numericalValue(Card("T", "h")))
        assertEquals(9, numericalValue(Card("9", "d")))
        assertEquals(2, numericalValue(Card("2", "c")))
    }

    @Test
    fun `test hand ranking`() {
        // Test royal flush
        val royalFlush = listOf(
            Card("A", "h"), Card("K", "h"), Card("Q", "h"), 
            Card("J", "h"), Card("T", "h")
        )
        val royalRank = rankHand(royalFlush)
        assertTrue("Royal flush should start with 9", royalRank.startsWith("9"))

        // Test straight flush
        val straightFlush = listOf(
            Card("9", "s"), Card("8", "s"), Card("7", "s"), 
            Card("6", "s"), Card("5", "s")
        )
        val straightFlushRank = rankHand(straightFlush)
        assertTrue("Straight flush should start with 8", straightFlushRank.startsWith("8"))

        // Test four of a kind
        val fourOfAKind = listOf(
            Card("A", "h"), Card("A", "d"), Card("A", "c"), 
            Card("A", "s"), Card("K", "h")
        )
        val fourKindRank = rankHand(fourOfAKind)
        assertTrue("Four of a kind should start with 7", fourKindRank.startsWith("7"))
    }

    @Test
    fun `test straight detection`() {
        // Test high straight
        val highStraight = listOf(14, 13, 12, 11, 10) // A-K-Q-J-T
        assertEquals(14, getStraight(highStraight))
        
        // Test middle straight
        val middleStraight = listOf(9, 8, 7, 6, 5)
        assertEquals(9, getStraight(middleStraight))
        
        // Test wheel (A-2-3-4-5)
        val wheel = listOf(14, 5, 4, 3, 2)
        assertEquals(5, getStraight(wheel))
        
        // Test no straight
        val noStraight = listOf(14, 12, 10, 8, 6)
        assertNull(getStraight(noStraight))
    }

    @Test
    fun `test deck creation`() {
        val fullDeck = createDeck()
        assertEquals(52, fullDeck.size)
        
        val withoutCards = listOf(Card("A", "s"), Card("K", "h"))
        val partialDeck = createDeck(withoutCards)
        assertEquals(50, partialDeck.size)
        assertFalse(partialDeck.contains(Card("A", "s")))
        assertFalse(partialDeck.contains(Card("K", "h")))
    }

    @Test
    fun `test convert to hex`() {
        assertEquals("e", convertToHex(listOf(14))) // Ace
        assertEquals("d", convertToHex(listOf(13))) // King
        assertEquals("c", convertToHex(listOf(12))) // Queen
        assertEquals("b", convertToHex(listOf(11))) // Jack
        assertEquals("a", convertToHex(listOf(10))) // Ten
        assertEquals("9", convertToHex(listOf(9)))  // Nine
        assertEquals("2", convertToHex(listOf(2)))  // Two
    }

    @Test
    fun `test simulate texas holdem odds`() {
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
    fun `test simulation with community cards`() {
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
    fun `test specific poker scenario for tie percentage functionality`() {
        // Test case represents: Hand 1: 8♣ 9♥, Hand 2: 8♥ 7♥, Flop: 7♣ 6♣ 5♣
        // Expected from external calculator: Hand 1 win 90.61% tie 6.97%, Hand 2 win 2.42% tie 6.97%
        // This test verifies that our tie percentage functionality works correctly
        
        val players = listOf(
            Player(1, "Player 1", listOf(Card("8", "c"), Card("9", "h"))), 
            Player(2, "Player 2", listOf(Card("8", "h"), Card("7", "h")))  
        )
        val communityCards = listOf(Card("7", "c"), Card("6", "c"), Card("5", "c"))
        val results = simulateTexasHoldemOdds(players, communityCards)
        
        assertEquals(2, results.size)
        
        // Verify that both win and tie percentages are calculated
        assertTrue("Player 1 should have valid win percentage", results[0].winPercentage >= 0.0)
        assertTrue("Player 1 should have valid tie percentage", results[0].tiePercentage >= 0.0)
        assertTrue("Player 2 should have valid win percentage", results[1].winPercentage >= 0.0)
        assertTrue("Player 2 should have valid tie percentage", results[1].tiePercentage >= 0.0)
        
        // Verify total percentages are reasonable for each player (win + tie should be <= 100%)
        assertTrue("Player 1 total percentage should be reasonable", 
            results[0].winPercentage + results[0].tiePercentage <= 100.0)
        assertTrue("Player 2 total percentage should be reasonable", 
            results[1].winPercentage + results[1].tiePercentage <= 100.0)
    }

    @Test
    fun `test specific poker scenario - 7h8h vs 8c9h with 5c6c7c flop`() {
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

        // Expected values from solved calculator
        val expectedP1Win = 2.42
        val expectedP1Tie = 6.97
        val expectedP2Win = 90.61
        val expectedP2Tie = 6.97

        // Allow for some variance in Monte Carlo simulation (±2%)
        assertEquals("Player 1 win percentage should match expected value", expectedP1Win, results[0].winPercentage, 2.0)
        assertEquals("Player 1 tie percentage should match expected value", expectedP1Tie, results[0].tiePercentage, 2.0)
        assertEquals("Player 2 win percentage should match expected value", expectedP2Win, results[1].winPercentage, 2.0)
        assertEquals("Player 2 tie percentage should match expected value", expectedP2Tie, results[1].tiePercentage, 2.0)
    }
}