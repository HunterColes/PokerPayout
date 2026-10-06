package com.huntercoles.pokerpayout.core.presentation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    /** PP-081: the live clock notification shows while the app is out of sight. */
    @Inject
    lateinit var visibilityListeners: @JvmSuppressWildcards Set<AppVisibilityListener>

    override fun onStart() {
        super.onStart()
        visibilityListeners.forEach { it.onAppVisible() }
    }

    override fun onStop() {
        super.onStop()
        // A rotation stops and recreates the activity; the app never left the screen.
        if (!isChangingConfigurations) visibilityListeners.forEach { it.onAppHidden() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Portrait on phones, free on tablets (PP-079, PP-088), from the first frame of a fresh start.
        // A recreated activity (a rotation) keeps whatever its screen asked for.
        if (savedInstanceState == null) {
            requestedOrientation = OrientationPolicy.base(resources.configuration.smallestScreenWidthDp)
        }
        // Draw behind transparent system bars with light icons: the app is dark only. (The old
        // theme painted the status bar green with dark icons on it, B13.) The shell and each
        // screen's top bar pad for the bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
