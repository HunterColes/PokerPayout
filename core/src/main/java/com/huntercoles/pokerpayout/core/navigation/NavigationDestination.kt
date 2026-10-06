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

    @Serializable
    data object Back : NavigationDestination()
}
