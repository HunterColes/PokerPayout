package com.huntercoles.pokerpayout.core.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.rememberNavController
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.core.navigation.PokerNavigationShell
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.utils.collectWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var navigationFactories: @JvmSuppressWildcards Set<NavigationFactory>

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var timerPreferences: TimerPreferences

    @Inject
    lateinit var snackbarController: SnackbarController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Portrait on phones, free on tablets (PP-079, PP-088), from the first frame of a fresh start.
        // A recreated activity (a rotation) keeps whatever its screen asked for.
        if (savedInstanceState == null) {
            requestedOrientation = OrientationPolicy.base(resources.configuration.smallestScreenWidthDp)
        }
        // The shell and each screen's top bar pad for the bars.
        drawBehindDarkSystemBars()
        setContent {
            KeepScreenOnWhileClockRuns(timerPreferences)

            PokerTheme {
                AppOrientation {
                    val navController = rememberNavController()

                    PokerNavigationShell(
                        navController = navController,
                        factories = navigationFactories,
                        snackbarHostState = snackbarController.hostState,
                    )

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
}

/** PP-015: the blind clock must not sleep while it runs, on any tab. */
@Composable
private fun KeepScreenOnWhileClockRuns(timerPreferences: TimerPreferences) {
    val running by timerPreferences.timerRunning.collectAsState(initial = timerPreferences.getTimerRunning())
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
}
