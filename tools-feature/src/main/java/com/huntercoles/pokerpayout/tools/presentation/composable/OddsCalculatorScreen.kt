package com.huntercoles.pokerpayout.tools.presentation.composable

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorViewModel

/**
 * The odds route (S8, S9) and run it out (S10), which is a full-screen state of the same route:
 * back leaves run it out first. The header's ⤢ in run it out turns the phone to landscape until
 * it's pressed again or run it out closes.
 */
@Composable
fun OddsCalculatorScreen(
    onBack: () -> Unit,
    viewModel: OddsCalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PokerTheme {
        val runOut = state.runOut
        BackHandler(enabled = runOut != null) { viewModel.acceptIntent(OddsCalculatorIntent.ExitRunItOut) }
        ForcedLandscape(enabled = runOut?.landscape == true)
        if (runOut != null) {
            RunItOutContent(runOut, state.fourColourDeck, viewModel::acceptIntent)
        } else {
            OddsCalculatorContent(state, viewModel::acceptIntent, onBack = onBack)
        }
    }
}

/**
 * Holds the activity in landscape while [enabled] (the run-it-out ⤢ for a propped-up phone) and
 * restores the manifest's orientation afterwards. A rotation recreates the activity, so the
 * orientation is only restored when the screen really goes away.
 */
@Composable
private fun ForcedLandscape(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        if (enabled) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (enabled && activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = activity.manifestOrientation()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION") // the flags overload needs API 33
private fun Activity.manifestOrientation(): Int =
    runCatching { packageManager.getActivityInfo(componentName, 0).screenOrientation }
        .getOrDefault(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
