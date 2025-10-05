package com.huntercoles.fatline.core.navigation

import kotlinx.serialization.Serializable

sealed class NavigationDestination {
    @Serializable
    data object Calculator : NavigationDestination()

    @Serializable
    data object Bank : NavigationDestination()

    @Serializable
    data object Timer : NavigationDestination()

    @Serializable
    data object Temp : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
