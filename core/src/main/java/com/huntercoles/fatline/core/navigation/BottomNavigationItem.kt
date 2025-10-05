package com.huntercoles.fatline.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.huntercoles.fatline.core.R

data class BottomNavigationItem(
    val destination: NavigationDestination,
    val icon: ImageVector,
    @StringRes val label: Int,
)

val bottomNavigationItems = listOf(
    BottomNavigationItem(
        destination = NavigationDestination.Calculator,
        icon = Icons.Filled.Add,
        label = R.string.navigation_calculator
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Bank,
        icon = Icons.Filled.AttachMoney,
        label = R.string.navigation_bank
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Timer,
        icon = Icons.Filled.PlayArrow,
        label = R.string.navigation_timer
    ),
    BottomNavigationItem(
        destination = NavigationDestination.Temp,
        icon = Icons.Filled.MoreHoriz,
        label = R.string.navigation_temp
    ),
)
