package com.huntercoles.pokerpayout.tournament.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.core.navigation.NavigationCommand
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsScreen
import javax.inject.Inject

/**
 * The Payouts tab. It lives here because the Tournament tab's ViewModel owns the payout table. Its
 * tip card (PP-112) opens Tip the dealer, a Tools page; Back comes back here.
 */
class PayoutsNavigationFactory @Inject constructor(
    private val navigationManager: NavigationManager,
) : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Payouts> {
            PayoutsScreen(onOpenTip = ::openTip)
        }
    }

    private fun openTip() {
        navigationManager.navigate(object : NavigationCommand {
            override val destination = NavigationDestination.TipDealer
            override val configuration: NavOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        })
    }
}
