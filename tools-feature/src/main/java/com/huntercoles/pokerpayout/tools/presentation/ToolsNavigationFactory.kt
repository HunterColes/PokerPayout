package com.huntercoles.pokerpayout.tools.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.tools.presentation.composable.OddsCalculatorScreen
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import javax.inject.Inject

class ToolsNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Tools> {
            OddsCalculatorScreen()
        }
    }
}