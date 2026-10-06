package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Immutable
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent

/**
 * What the Tournament tab can do: its ViewModels (setup, clock, presets), its own UI state, the tabs it
 * links to, and handing text to the system's share sheet.
 */
@Immutable
@Suppress("LongParameterList") // one callback per thing the tab does, each with a default for tests
class TournamentActions(
    val onSetupIntent: (TournamentConfigIntent) -> Unit = {},
    val onTimerIntent: (TimerIntent) -> Unit = {},
    val updateUi: ((TournamentUi) -> TournamentUi) -> Unit = {},
    val openBank: () -> Unit = {},
    val openPayouts: () -> Unit = {},
    val openSound: () -> Unit = {},
    val onPresetIntent: (PresetsIntent) -> Unit = {},
    val shareText: (String) -> Unit = {},
)
