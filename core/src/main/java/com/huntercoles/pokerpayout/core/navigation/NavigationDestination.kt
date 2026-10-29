package com.huntercoles.pokerpayout.core.navigation

import kotlinx.serialization.Serializable

/**
 * Every screen of the app, as a type-safe route. The names are part of the saved navigation state,
 * so keep them even if a screen's title changes.
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

    /**
     * Cue sounds: the clock's sound screen, its switch (the bells' mute) and volume, and the cue
     * sound packs, each sound heard before it's picked (Tools > Sound and music, and the clock's
     * "Sound settings", #79).
     */
    @Serializable
    data object CueSounds : NavigationDestination()

    /** Everything the app saves, in one file: save a backup, open one and restore it. */
    @Serializable
    data object Backup : NavigationDestination()

    /** The money symbol amounts show with: $, €, £, kr, ₹, ¥ or none (PP-114). */
    @Serializable
    data object Currency : NavigationDestination()

    /** Main and side pots from what each player put in (S21). */
    @Serializable
    data object SidePots : NavigationDestination()

    /** Split the prizes: the deal the players left make, by ICM and by chip chop (S22). */
    @Serializable
    data object DealMaker : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
