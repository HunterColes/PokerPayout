package com.huntercoles.fatline.core.constants

/**
 * Constants related to tournament configuration
 */
object TournamentConstants {
    /**
     * Default payout weights for tournament positions.
     * These weights determine the relative payout distribution for each position.
     * Index 0 = 1st place, Index 1 = 2nd place, etc.
     */
    val DEFAULT_PAYOUT_WEIGHTS = listOf(35, 20, 15, 10, 8, 6, 3, 2, 1)
    
    /**
     * String representation of default payout weights for storage in preferences
     */
    val DEFAULT_PAYOUT_WEIGHTS_STRING = DEFAULT_PAYOUT_WEIGHTS.joinToString(",")
}