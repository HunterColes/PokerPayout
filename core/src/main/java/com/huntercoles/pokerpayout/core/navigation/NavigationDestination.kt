package com.huntercoles.pokerpayout.core.navigation

import kotlinx.serialization.Serializable

/**
 * Every screen of the app, as a type-safe route. The names are part of the saved navigation state,
 * so keep them when a screen is renamed on screen (the Tournament tab may become "Clock", D3).
 */
sealed class NavigationDestination {
    @Serializable
    data object Tournament : NavigationDestination()

    @Serializable
    data object Bank : NavigationDestination()

    /** The payout table, its presets and the structure editor (its own tab since D1). */
    @Serializable
    data object Payouts : NavigationDestination()

    @Serializable
    data object Tools : NavigationDestination()

    @Serializable
    data object OddsCalculator : NavigationDestination()

    @Serializable
    data object HandRanks : NavigationDestination()

    @Serializable
    data object ChipCalculator : NavigationDestination()

    /** Random seats across the tables, and the high-card draw for the button (PP-036). */
    @Serializable
    data object SeatDraw : NavigationDestination()

    /** Saved nights and the season's points standings (PP-037). */
    @Serializable
    data object History : NavigationDestination()

    /** The music: a playlist of the host's songs, and playing it with the clock (Tools > Sound). */
    @Serializable
    data object Music : NavigationDestination()

    /** The cue sound packs, each sound heard before it's picked (Tools > Sound). */
    @Serializable
    data object CueSounds : NavigationDestination()

    /** Everything the app saves, in one file: save a backup, open one and restore it. */
    @Serializable
    data object Backup : NavigationDestination()

    /** The money symbol amounts show with: $, €, £, kr, ₹, ¥ or none (PP-114). */
    @Serializable
    data object Currency : NavigationDestination()

    /** A countdown for each decision, with time-bank cards for slow players. */
    @Serializable
    data object ShotClock : NavigationDestination()

    /** A wheel that picks the next game, with each game's rules. */
    @Serializable
    data object DealersChoice : NavigationDestination()

    /** A guessing game on the odds engine: who's ahead, and by how much. */
    @Serializable
    data object EquityQuiz : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
