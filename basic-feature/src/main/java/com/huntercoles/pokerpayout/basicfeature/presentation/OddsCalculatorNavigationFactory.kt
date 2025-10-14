package com.huntercoles.pokerpayout.basicfeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.basicfeature.presentation.composable.OddsCalculatorScreen
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import javax.inject.Inject

class OddsCalculatorNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.OddsCalculator> {
            OddsCalculatorScreen()
        }
    }
}