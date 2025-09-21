package com.huntercoles.fatline.core.navigation

import kotlinx.serialization.Serializable

sealed class NavigationDestination {
    @Serializable
    data object Search : NavigationDestination()

    @Serializable 
    data object Portfolio : NavigationDestination()

    @Serializable
    data object Settings : NavigationDestination()

    @Serializable
    data object Back : NavigationDestination()
}
