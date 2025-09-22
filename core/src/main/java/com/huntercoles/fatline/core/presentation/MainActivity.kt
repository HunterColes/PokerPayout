package com.huntercoles.fatline.core.presentation

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import com.huntercoles.fatline.core.R
import com.huntercoles.fatline.core.design.AndroidStarterTheme
import com.huntercoles.fatline.core.design.PokerColors
import com.huntercoles.fatline.core.navigation.BottomNavigationItem
import com.huntercoles.fatline.core.navigation.NavigationDestination
import com.huntercoles.fatline.core.navigation.NavigationFactory
import com.huntercoles.fatline.core.navigation.NavigationHost
import com.huntercoles.fatline.core.navigation.NavigationManager
import com.huntercoles.fatline.core.navigation.bottomNavigationItems
import com.huntercoles.fatline.core.preferences.ThemePreferences
import com.huntercoles.fatline.core.preferences.isDarkTheme
import com.huntercoles.fatline.core.utils.collectWithLifecycle
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
            
            AndroidStarterTheme(
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
                    navigationManager.navigate(object : com.huntercoles.fatline.core.navigation.NavigationCommand {
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
