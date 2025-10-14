package com.huntercoles.pokerpayout.portfoliofeature.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.portfoliofeature.presentation.composable.BankRoute
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination.Bank
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import javax.inject.Inject

class BankNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<Bank> {
            BankRoute()
        }
    }
}