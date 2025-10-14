package com.huntercoles.pokerpayout.tournament.domain.usecase

import com.huntercoles.pokerpayout.tournament.domain.model.TournamentConfig
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import org.junit.Test
import kotlin.test.assertEquals

class CalculatePayoutsUseCaseTest {

    private val useCase = CalculatePayoutsUseCase()

    @Test
    fun `default weights should be limited by player count`() {
        // Given: 6 players with default weights (should limit to 2 positions: 6/3 = 2)
        val config = TournamentConfig(
            numPlayers = 6,
            buyIn = 20.0,
            payoutWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS
        )

        // When: calculating payouts
        val payouts = useCase(config)

        // Then: should only have 2 positions (limited by player count)
        assertEquals(2, payouts.size)
        assertEquals(1, payouts[0].position)
        assertEquals(2, payouts[1].position)
    }

    @Test
    fun `truncated default weights should match player count divisor`() {
        // Given: 18 players with default weights already truncated to players div 3 (6 positions)
        val defaultWeights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(6)
        val config = TournamentConfig(
            numPlayers = 18,
            buyIn = 20.0,
            payoutWeights = defaultWeights
        )

        // When: calculating payouts
        val payouts = useCase(config)

        // Then: should keep exactly 6 paying positions
        assertEquals(6, payouts.size)
        defaultWeights.forEachIndexed { index, weight ->
            assertEquals(index + 1, payouts[index].position)
            assertEquals(weight, payouts[index].weight)
        }
    }

    @Test
    fun `custom weights should show all positions regardless of player count`() {
        // Given: 6 players with 4 custom weight positions
        val customWeights = listOf(40, 30, 20, 10) // 4 positions
        val config = TournamentConfig(
            numPlayers = 6,
            buyIn = 20.0,
            payoutWeights = customWeights
        )

        // When: calculating payouts
        val payouts = useCase(config)

        // Then: should show all 4 positions, not be limited by player count
        assertEquals(4, payouts.size)
        assertEquals(1, payouts[0].position)
        assertEquals(2, payouts[1].position)
        assertEquals(3, payouts[2].position)
        assertEquals(4, payouts[3].position)
    }

    @Test
    fun `custom weights should calculate correct payouts and percentages`() {
        // Given: 3 players with custom weights
        val customWeights = listOf(50, 30, 20) // Total = 100
        val config = TournamentConfig(
            numPlayers = 3,
            buyIn = 100.0, // Prize pool = 300.0
            payoutWeights = customWeights
        )

        // When: calculating payouts
        val payouts = useCase(config)

        // Then: should calculate correct amounts and percentages
        assertEquals(3, payouts.size)
        
        // First place: 50/100 * 300 = 150, 50%
        assertEquals(150.0, payouts[0].payout)
        assertEquals(50.0, payouts[0].percentage)
        
        // Second place: 30/100 * 300 = 90, 30%
        assertEquals(90.0, payouts[1].payout)
        assertEquals(30.0, payouts[1].percentage)
        
        // Third place: 20/100 * 300 = 60, 20%
        assertEquals(60.0, payouts[2].payout)
        assertEquals(20.0, payouts[2].percentage)
    }

    @Test
    fun `many players with custom weights should show all weight positions`() {
        // Given: 15 players (would normally limit to 5 positions) with 8 custom weights
        val customWeights = listOf(35, 20, 15, 10, 8, 6, 4, 2) // 8 positions
        val config = TournamentConfig(
            numPlayers = 15,
            buyIn = 20.0,
            payoutWeights = customWeights
        )

        // When: calculating payouts
        val payouts = useCase(config)

        // Then: should show all 8 positions from custom weights
        assertEquals(8, payouts.size)
        for (i in 0 until 8) {
            assertEquals(i + 1, payouts[i].position)
            assertEquals(customWeights[i], payouts[i].weight)
        }
    }

    @Test
    fun `few players with many custom weights should show all custom weight positions`() {
        // Given: 3 players (would normally limit to 1 position) with 6 custom weights
        val customWeights = listOf(40, 25, 15, 10, 6, 4) // 6 positions
        val config = TournamentConfig(
            numPlayers = 3,
            buyIn = 20.0,
            payoutWeights = customWeights
        )

        // When: calculating payouts
        val payouts = useCase(config)

        // Then: should show all 6 positions from custom weights (not limited to 1)
        assertEquals(6, payouts.size)
        for (i in 0 until 6) {
            assertEquals(i + 1, payouts[i].position)
            assertEquals(customWeights[i], payouts[i].weight)
        }
    }
}