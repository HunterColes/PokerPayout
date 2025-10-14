package com.huntercoles.pokerpayout.core.navigation

import kotlinx.serialization.Serializable

sealed class NavigationDestination {
    @Serializable
    data object Tournament : NavigationDestination()

    @Serializable
    data object Bank : NavigationDestination()

    @Serializable
    data object Tools : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
