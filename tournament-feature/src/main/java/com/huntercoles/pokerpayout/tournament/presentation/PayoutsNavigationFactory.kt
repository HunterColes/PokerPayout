package com.huntercoles.pokerpayout.tournament.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsScreen
import javax.inject.Inject

/** The Payouts tab ([PayoutsScreen]): the tournament's payouts, so it lives with the Tournament tab. */
class PayoutsNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Payouts> {
            PayoutsScreen()
        }
    }
}
