package com.huntercoles.fatline.portfoliofeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.fatline.portfoliofeature.presentation.composable.BankRoute
import com.huntercoles.fatline.core.navigation.NavigationDestination.Bank
import com.huntercoles.fatline.core.navigation.NavigationFactory
import javax.inject.Inject

class BankNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<Bank> {
            BankRoute()
        }
    }
}