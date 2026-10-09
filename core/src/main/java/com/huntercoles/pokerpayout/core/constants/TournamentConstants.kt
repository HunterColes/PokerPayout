package com.huntercoles.pokerpayout.core.constants

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

    /** The fewest players a tournament can be set up for (the Tournament tab's stepper). */
    const val MIN_PLAYERS = 3

    /** The most players: the Tournament tab's stepper, and the Bank's seats picked from the regulars. */
    const val MAX_PLAYERS = 30
}
