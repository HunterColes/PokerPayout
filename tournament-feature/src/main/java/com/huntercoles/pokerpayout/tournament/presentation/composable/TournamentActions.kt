package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Immutable
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi

/** What the Tournament tab can do: its two ViewModels, its own UI state, and the tabs it links to. */
@Immutable
class TournamentActions(
    val onSetupIntent: (TournamentConfigIntent) -> Unit = {},
    val onTimerIntent: (TimerIntent) -> Unit = {},
    val updateUi: ((TournamentUi) -> TournamentUi) -> Unit = {},
    val openBank: () -> Unit = {},
    val openPayouts: () -> Unit = {},
    val openSound: () -> Unit = {},
)
