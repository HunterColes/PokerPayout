package com.huntercoles.pokerpayout.tournament.domain.model

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults

/**
 * Configuration for a poker tournament
 */
data class TournamentConfig(
    val numPlayers: Int = TournamentDefaults.PLAYER_COUNT,
    val buyIn: Double = TournamentDefaults.BUY_IN,
    val foodPerPlayer: Double = TournamentDefaults.FOOD_PER_PLAYER,
    val bountyPerPlayer: Double = TournamentDefaults.BOUNTY_PER_PLAYER,
    val rebuyPerPlayer: Double = TournamentDefaults.REBUY_PER_PLAYER,
    val addOnPerPlayer: Double = TournamentDefaults.ADDON_PER_PLAYER,
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