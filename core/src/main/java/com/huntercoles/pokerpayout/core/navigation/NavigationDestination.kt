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

    /** Outs as exact chances, and pot odds (S20). */
    @Serializable
    data object Outs : NavigationDestination()

    /** Main and side pots from what each player put in (S21). */
    @Serializable
    data object SidePots : NavigationDestination()

    /** The chop: ICM and chip chop for the players left (S22). */
    @Serializable
    data object DealMaker : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
