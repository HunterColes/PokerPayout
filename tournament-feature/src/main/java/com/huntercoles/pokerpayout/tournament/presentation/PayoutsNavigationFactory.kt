package com.huntercoles.pokerpayout.tournament.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsScreen
import javax.inject.Inject

/** The Payouts tab. It lives here because the Tournament tab's ViewModel owns the payout table. */
class PayoutsNavigationFactory @Inject constructor() : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Payouts> {
            PayoutsScreen()
        }
    }
}
