package com.huntercoles.fatline.basicfeature.domain.model

import com.huntercoles.fatline.core.constants.TournamentConstants

/**
 * Configuration for a poker tournament
 */
data class TournamentConfig(
    val numPlayers: Int = 9,
    val buyIn: Double = 20.0,
    val foodPerPlayer: Double = 5.0,
    val bountyPerPlayer: Double = 2.0,
    val rebuyPerPlayer: Double = 0.0,
    val addOnPerPlayer: Double = 0.0,
    val payoutWeights: List<Int> = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS
) {
    val totalPerPlayer: Double
        get() = buyIn + foodPerPlayer + bountyPerPlayer + addOnPerPlayer

    // Totals that exclude add-on (used for display in Pool Summary per user request)
    val totalPerPlayerWithoutAddOn: Double
        get() = buyIn + foodPerPlayer + bountyPerPlayer
        
    val prizePool: Double
        get() = numPlayers * buyIn
        
    val foodPool: Double
        get() = numPlayers * foodPerPlayer
        
    val bountyPool: Double
        get() = numPlayers * bountyPerPlayer
        
    val addOnPool: Double
        get() = numPlayers * addOnPerPlayer
        
    val totalPool: Double
        get() = prizePool + foodPool + bountyPool + addOnPool

    val totalPoolWithoutAddOn: Double
        get() = prizePool + foodPool + bountyPool
}