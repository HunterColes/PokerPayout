package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.presentation.findActivity
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsViewModel

// The Tournament tab's route (TournamentScreen): what leaving it does, and the champion's screen's state.

/** Leaving the tab commits what was typed, and ends a ⤢ table view (phones turn back upright). */
@Composable
internal fun OnLeavingTheTab(timerViewModel: TimerViewModel) {
    val focusManager = LocalFocusManager.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            if (activity?.isChangingConfigurations != true) timerViewModel.acceptIntent(TimerIntent.SetTableView(false))
        }
    }
}

/** PP-111: the champion's screen for the night as the Payouts tab has it; null until there is a champion. */
@Composable
internal fun rememberWinner(payoutsViewModel: PayoutsViewModel): WinnerModel? {
    val payouts by payoutsViewModel.uiState.collectAsStateWithLifecycle()
    return remember(payouts) { WinnerModel.from(payouts) }
}
