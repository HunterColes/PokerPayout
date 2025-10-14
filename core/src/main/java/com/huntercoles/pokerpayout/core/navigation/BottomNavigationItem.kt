package com.huntercoles.pokerpayout.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.ui.graphics.vector.ImageVector
import com.huntercoles.pokerpayout.core.R

data class BottomNavigationItem(
    val destination: NavigationDestination,
    val icon: ImageVector,
    @StringRes val label: Int,
)

val bottomNavigationItems = listOf(
    BottomNavigationItem(
        destination = NavigationDestination.Tournament,
        icon = Icons.Filled.EmojiEvents,
        label = R.string.navigation_tournament
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Bank,
        icon = Icons.Filled.AttachMoney,
        label = R.string.navigation_bank
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Tools,
        icon = Icons.Filled.Build,
        label = R.string.navigation_tools
    ),
)
