package com.huntercoles.pokerpayout.tools.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.tools.presentation.composable.BackupRoute
import com.huntercoles.pokerpayout.tools.presentation.composable.ChipSetRoute
import com.huntercoles.pokerpayout.tools.presentation.composable.HandRanksScreen
import com.huntercoles.pokerpayout.tools.presentation.composable.HistoryRoute
import com.huntercoles.pokerpayout.tools.presentation.composable.OddsCalculatorScreen
import com.huntercoles.pokerpayout.tools.presentation.composable.SeatDrawRoute
import com.huntercoles.pokerpayout.tools.presentation.composable.ToolsHomeScreen
import javax.inject.Inject

class ToolsNavigationFactory @Inject constructor(
    private val navigationManager: NavigationManager
) : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        // The Tools tab (S7): the tools, then Sound
        builder.composable<NavigationDestination.Tools> {
            ToolsHomeScreen(navigationManager = navigationManager)
        }

        // Odds. Keep the whole screen (keypad and run-it-out included) clear of the status bar now
        // that the app draws edge to edge. Its PokerTopBar pads for the bar itself; this padding
        // consumes the inset, so the two never add up.
        builder.composable<NavigationDestination.OddsCalculator> {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                OddsCalculatorScreen(onBack = navigationManager::navigateBack)
            }
        }

        builder.composable<NavigationDestination.HandRanks> {
            HandRanksScreen(onBack = navigationManager::navigateBack)
        }

        builder.composable<NavigationDestination.ChipCalculator> {
            ChipSetRoute(onBack = navigationManager::navigateBack)
        }

        builder.composable<NavigationDestination.SeatDraw> {
            SeatDrawRoute(onBack = navigationManager::navigateBack)
        }

        // Saved nights and the season's points (PP-037)
        builder.composable<NavigationDestination.History> {
            HistoryRoute(onBack = navigationManager::navigateBack)
        }

        // Everything the app saves, in one file: save it, open it and restore it
        builder.composable<NavigationDestination.Backup> {
            BackupRoute(onBack = navigationManager::navigateBack)
        }
    }
}
