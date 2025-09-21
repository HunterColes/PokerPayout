package com.huntercoles.fatline.basicfeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.fatline.basicfeature.presentation.composable.CalculatorRoute
import com.huntercoles.fatline.core.navigation.NavigationDestination.Calculator
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Inject

class CalculatorNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<Calculator> {
            CalculatorRoute()
        }
    }
}