package com.huntercoles.pokerpayout.core.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerPayoutTheme
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.navigation.BottomNavigationItem
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationHost
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.core.navigation.bottomNavigationItems
import com.huntercoles.pokerpayout.core.preferences.ThemePreferences
import com.huntercoles.pokerpayout.core.preferences.isDarkTheme
import com.huntercoles.pokerpayout.core.utils.collectWithLifecycle
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationFactories: @JvmSuppressWildcards Set<NavigationFactory>

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme by themePreferences.darkModeEnabled.collectAsState(initial = themePreferences.getDarkModePreference())
            
            PokerPayoutTheme(
                darkTheme = isDarkTheme
            ) {
                val navController = rememberNavController()

                Scaffold(
                    topBar = { MainTopAppBar() },
                    bottomBar = { 
                        MainBottomNavigationBar(
                            navController = navController,
                            navigationManager = navigationManager
                        )
                    },
                ) {
                    NavigationHost(
                        modifier = Modifier
                            .padding(it),
                        navController = navController,
                        factories = navigationFactories,
                    )
                }

                navigationManager
                    .navigationEvent
                    .collectWithLifecycle(
                        key = navController,
                    ) {
                        when (it.destination) {
                            NavigationDestination.Back -> navController.navigateUp()
                            else -> navController.navigate(it.destination, it.configuration)
                        }
                    }
            }
        }
    }
}

@Composable
private fun MainTopAppBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.app_name),
                fontWeight = FontWeight.Medium,
                color = PokerColors.PokerGold
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = PokerColors.FeltGreen,
            titleContentColor = PokerColors.PokerGold,
        ),
    )
}

@Composable
private fun MainBottomNavigationBar(
    navController: androidx.navigation.NavHostController,
    navigationManager: NavigationManager,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val focusManager = LocalFocusManager.current

    NavigationBar(
        containerColor = PokerColors.DarkGreen,
        contentColor = PokerColors.CardWhite
    ) {
        bottomNavigationItems.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.label),
                        tint = if (currentDestination?.hasRoute(item.destination::class) == true) 
                            PokerColors.PokerGold else PokerColors.CardWhite
                    )
                },
                label = { 
                    Text(
                        text = stringResource(item.label),
                        color = if (currentDestination?.hasRoute(item.destination::class) == true) 
                            PokerColors.PokerGold else PokerColors.CardWhite
                    )
                },
                selected = currentDestination?.hasRoute(item.destination::class) == true,
                onClick = {
                    focusManager.clearFocus()
                    navigationManager.navigate(object : com.huntercoles.pokerpayout.core.navigation.NavigationCommand {
                        override val destination = item.destination
                    })
                },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = PokerColors.PokerGold,
                    selectedTextColor = PokerColors.PokerGold,
                    indicatorColor = PokerColors.AccentGreen,
                    unselectedIconColor = PokerColors.CardWhite,
                    unselectedTextColor = PokerColors.CardWhite
                )
            )
        }
    }
}
