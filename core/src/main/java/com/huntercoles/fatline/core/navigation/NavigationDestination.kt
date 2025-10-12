package com.huntercoles.fatline.core.navigation

import kotlinx.serialization.Serializable

sealed class NavigationDestination {
    @Serializable
    data object Play : NavigationDestination()

    @Serializable
    data object Bank : NavigationDestination()

    @Serializable
    data object OddsCalculator : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
