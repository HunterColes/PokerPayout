package com.huntercoles.fatline.basicfeature.domain.model

/**
 * Configuration for a poker tournament
 */
data class TournamentConfig(
    val numPlayers: Int = 9,
    val buyIn: Double = 20.0,
    val foodPerPlayer: Double = 5.0,
    val bountyPerPlayer: Double = 2.0,
    val payoutWeights: List<Int> = listOf(35, 20, 15, 10, 8, 6, 3, 2, 1) // Default weights for top 9 positions
) {
    val totalPerPlayer: Double
        get() = buyIn + foodPerPlayer + bountyPerPlayer
        
    val prizePool: Double
        get() = numPlayers * buyIn
        
    val foodPool: Double
        get() = numPlayers * foodPerPlayer
        
    val bountyPool: Double
        get() = numPlayers * bountyPerPlayer
        
    val totalPool: Double
        get() = prizePool + foodPool + bountyPool
}