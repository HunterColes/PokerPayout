package com.huntercoles.pokerpayout.tools.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.tools.presentation.composable.OddsCalculatorScreen
import com.huntercoles.pokerpayout.tools.presentation.composable.ToolsHomeScreen
import com.huntercoles.pokerpayout.tools.presentation.composable.HandRanksScreen
import com.huntercoles.pokerpayout.tools.presentation.composable.ChipCalculatorScreen
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import javax.inject.Inject

class ToolsNavigationFactory @Inject constructor(
    private val navigationManager: NavigationManager
) : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        // Main tools home screen with grid
        builder.composable<NavigationDestination.Tools> {
            ToolsHomeScreen(navigationManager = navigationManager)
        }
        
        // Odds Calculator tool
        builder.composable<NavigationDestination.OddsCalculator> {
            OddsCalculatorScreen()
        }
        
        // Hand Rankings tool
        builder.composable<NavigationDestination.HandRanks> {
            HandRanksScreen()
        }
        
        // Chip Calculator tool
        builder.composable<NavigationDestination.ChipCalculator> {
            ChipCalculatorScreen()
        }
    }
}