package com.huntercoles.fatline.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.huntercoles.fatline.core.R

data class BottomNavigationItem(
    val destination: NavigationDestination,
    val icon: ImageVector,
    @StringRes val label: Int,
)

val bottomNavigationItems = listOf(
    BottomNavigationItem(
        destination = NavigationDestination.Search,
        icon = Icons.Default.Search,
        label = R.string.navigation_search
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Portfolio,
        icon = Icons.Default.Home,
        label = R.string.navigation_portfolio
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Settings,
        icon = Icons.Default.Settings,
        label = R.string.navigation_settings
    ),
)
