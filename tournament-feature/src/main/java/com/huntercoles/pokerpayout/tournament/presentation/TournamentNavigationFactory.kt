package com.huntercoles.pokerpayout.tournament.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.huntercoles.pokerpayout.core.navigation.NavigationCommand
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.tournament.presentation.composable.TournamentScreen
import javax.inject.Inject

class TournamentNavigationFactory @Inject constructor(
    private val navigationManager: NavigationManager,
) : NavigationFactory {

    override fun create(builder: NavGraphBuilder) {
        builder.composable<NavigationDestination.Tournament> {
            TournamentScreen(
                onOpenBank = { openTab(NavigationDestination.Bank) },
                onOpenPayouts = { openTab(NavigationDestination.Payouts) },
                onOpenSound = { openTab(NavigationDestination.Tools) },
            )
        }
    }

    /**
     * A link from the Tournament tab to another tab ("Record in Bank", the payouts row, the bell's
     * long press) opens it as a tab tap does: on top of Tournament once, so Back comes home (B16).
     */
    private fun openTab(tab: NavigationDestination) {
        navigationManager.navigate(object : NavigationCommand {
            override val destination: NavigationDestination = tab
            override val configuration: NavOptions = NavOptions.Builder()
                .setPopUpTo<NavigationDestination.Tournament>(inclusive = false)
                .setLaunchSingleTop(true)
                .build()
        })
    }
}
