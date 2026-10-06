package com.huntercoles.pokerpayout.core.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.huntercoles.pokerpayout.core.design.components.PokerAppShell
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems

/**
 * The app's navigation: every feature's screens ([factories]) inside [PokerAppShell]. The selected
 * tab follows the screen on top, so a tool's screens keep Tools selected (B16), and a tab tap goes
 * through [navigateToTab]. Leaving a tab first takes the focus out of any field, which commits what
 * was typed (money and names commit on blur).
 */
@Composable
fun PokerNavigationShell(
    navController: NavHostController,
    factories: Set<NavigationFactory>,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val entry by navController.currentBackStackEntryAsState()
    val selected = entry?.destination.selectedTab()
    val focusManager = LocalFocusManager.current
    PokerAppShell(
        items = pokerNavItems(),
        selectedIndex = selected?.ordinal ?: -1,
        onSelect = { index ->
            focusManager.clearFocus()
            navController.navigateToTab(NavTab.entries[index])
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        NavigationHost(navController = navController, factories = factories)
    }
}
