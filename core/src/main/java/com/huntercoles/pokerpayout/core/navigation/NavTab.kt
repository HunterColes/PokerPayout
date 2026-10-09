package com.huntercoles.pokerpayout.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * The four tabs, in the order the nav bar and rail show them (D1: four is the maximum). The order
 * matches `pokerNavItems()`, which supplies the labels and icons.
 */
enum class NavTab(val destination: NavigationDestination) {
    Tournament(NavigationDestination.Tournament),
    Bank(NavigationDestination.Bank),
    Payouts(NavigationDestination.Payouts),
    Tools(NavigationDestination.Tools),
}

/**
 * The tab a screen belongs to. A tool's own screens keep Tools selected (B16: before, no tab was
 * selected inside a tool). Null for [NavigationDestination.Back], which is a command, not a screen.
 */
val NavigationDestination.tab: NavTab?
    get() = when (this) {
        NavigationDestination.Tournament -> NavTab.Tournament
        NavigationDestination.Bank -> NavTab.Bank
        NavigationDestination.Payouts -> NavTab.Payouts
        NavigationDestination.Tools,
        NavigationDestination.OddsCalculator,
        NavigationDestination.HandRanks,
        NavigationDestination.ChipCalculator,
        NavigationDestination.SeatDraw,
        NavigationDestination.History,
        NavigationDestination.Outs,
        NavigationDestination.SidePots,
        NavigationDestination.DealMaker,
        -> NavTab.Tools
        NavigationDestination.Back -> null
    }

/** Every screen, for finding out which one a back-stack entry shows. Add new screens here too. */
val ScreenDestinations: List<NavigationDestination> = listOf(
    NavigationDestination.Tournament,
    NavigationDestination.Bank,
    NavigationDestination.Payouts,
    NavigationDestination.Tools,
    NavigationDestination.OddsCalculator,
    NavigationDestination.HandRanks,
    NavigationDestination.ChipCalculator,
    NavigationDestination.SeatDraw,
    NavigationDestination.History,
    NavigationDestination.Outs,
    NavigationDestination.SidePots,
    NavigationDestination.DealMaker,
)

/** The tab to show as selected while this destination is on top, or null when it isn't a screen. */
fun NavDestination?.selectedTab(): NavTab? {
    val destination = this ?: return null
    return ScreenDestinations.firstOrNull { destination.hasRoute(it::class) }?.tab
}

/**
 * Opens [tab] the way Material navigation does: everything above the first tab is popped, then the
 * tab goes on top once. Back from any tab returns to Tournament, and Back again leaves the app,
 * instead of walking through every tab tapped (B16). A tab opens on its own first screen, as before.
 */
fun NavController.navigateToTab(tab: NavTab) {
    navigate(tab.destination) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
