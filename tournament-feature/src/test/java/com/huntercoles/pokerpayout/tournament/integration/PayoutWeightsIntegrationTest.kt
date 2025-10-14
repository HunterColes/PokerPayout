package com.huntercoles.pokerpayout.tournament.integration

import com.huntercoles.pokerpayout.tournament.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.tournament.domain.model.TournamentConfig
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Integration test to verify the complete payout weight functionality
 */
class PayoutWeightsIntegrationTest {

    private val calculatePayoutsUseCase = CalculatePayoutsUseCase()

    @Test
    fun `scenario - user customizes weights then resets should show proper positions`() {
        // Scenario: User has 9 players, starts with defaults (shows 3 positions: 9/3=3)
        val defaultConfig = TournamentConfig(
            numPlayers = 9,
            buyIn = 20.0,
            payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS
        )
        
        val defaultPayouts = calculatePayoutsUseCase(defaultConfig)
        assertEquals(3, defaultPayouts.size, "Default should show 3 positions for 9 players")
        
        // User customizes to 6 positions
        val customWeights = listOf(35, 25, 20, 10, 6, 4) // 6 positions
        val customConfig = defaultConfig.copy(payoutWeights = customWeights)
        
        val customPayouts = calculatePayoutsUseCase(customConfig)
        assertEquals(6, customPayouts.size, "Custom weights should show all 6 positions")
        
        // User hits reset - should be back to 3 positions
        val resetConfig = defaultConfig.copy(payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS)
        
        val resetPayouts = calculatePayoutsUseCase(resetConfig)
        assertEquals(3, resetPayouts.size, "After reset should show 3 positions again")
        assertEquals(TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(3), resetPayouts.map { it.weight })
    }

    @Test
    fun `scenario - few players with many custom positions should work`() {
        // Scenario: User has 3 players but wants 6 payout positions
        val customWeights = listOf(40, 25, 15, 10, 6, 4) // 6 positions
        val config = TournamentConfig(
            numPlayers = 3, // Would normally limit to 1 position (3/3 = 1)
            buyIn = 50.0,
            payoutWeights = customWeights
        )
        
        val payouts = calculatePayoutsUseCase(config)
        assertEquals(6, payouts.size, "Should show all 6 custom positions even with only 3 players")
        
        // Verify all positions are created
        for (i in 0 until 6) {
            assertEquals(i + 1, payouts[i].position)
            assertEquals(customWeights[i], payouts[i].weight)
        }
    }

    @Test
    fun `scenario - default weights respect player limits`() {
        // Verify that default weights still respect the traditional player count limits
        val configs = listOf(
            TournamentConfig(numPlayers = 3, payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS), // 3/3 = 1
            TournamentConfig(numPlayers = 6, payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS), // 6/3 = 2  
            TournamentConfig(numPlayers = 9, payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS), // 9/3 = 3
            TournamentConfig(numPlayers = 15, payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS), // 15/3 = 5
            TournamentConfig(numPlayers = 30, payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS)  // 30/3 = 10, but DEFAULT_PAYOUT_WEIGHTS has 9, so 9
        )
        
        val expectedPositions = listOf(1, 2, 3, 5, 9)
        
        configs.forEachIndexed { index, config ->
            val payouts = calculatePayoutsUseCase(config)
            assertEquals(
                expectedPositions[index], 
                payouts.size, 
                "For ${config.numPlayers} players with default weights, expected ${expectedPositions[index]} positions"
            )
        }
    }

    @Test
    fun `custom weights should calculate correct percentages`() {
        // Test that percentages add up to 100% with custom weights
        val customWeights = listOf(50, 30, 15, 5) // Total = 100
        val config = TournamentConfig(
            numPlayers = 10,
            buyIn = 100.0, // Prize pool = 1000
            payoutWeights = customWeights
        )
        
        val payouts = calculatePayoutsUseCase(config)
        assertEquals(4, payouts.size)
        
        // Check percentages
        assertEquals(50.0, payouts[0].percentage)
        assertEquals(30.0, payouts[1].percentage) 
        assertEquals(15.0, payouts[2].percentage)
        assertEquals(5.0, payouts[3].percentage)
        
        // Check that percentages add up to 100%
        val totalPercentage = payouts.sumOf { it.percentage }
        assertEquals(100.0, totalPercentage, 0.001)
        
        // Check payouts
        assertEquals(500.0, payouts[0].payout) // 50% of 1000
        assertEquals(300.0, payouts[1].payout) // 30% of 1000
        assertEquals(150.0, payouts[2].payout) // 15% of 1000
        assertEquals(50.0, payouts[3].payout)  // 5% of 1000
    }
}