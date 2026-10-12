package com.huntercoles.pokerpayout.core.constants

/**
 * A new tournament's setup, used wherever the app starts one. Amounts are whole cents, as all money
 * is ([com.huntercoles.pokerpayout.core.utils.Money]): a $20 buy-in and $5 for food.
 */
object TournamentDefaults {
    const val PLAYER_COUNT = 5
    const val BUY_IN_CENTS = 2_000L
    const val FOOD_PER_PLAYER_CENTS = 500L
    const val BOUNTY_PER_PLAYER_CENTS = 0L
    const val REBUY_PER_PLAYER_CENTS = 0L
    const val ADDON_PER_PLAYER_CENTS = 0L
    const val GAME_DURATION_HOURS = 3
    const val ROUND_LENGTH_MINUTES = 20
    const val SMALLEST_CHIP = 50
    const val STARTING_CHIPS = 5000
}
