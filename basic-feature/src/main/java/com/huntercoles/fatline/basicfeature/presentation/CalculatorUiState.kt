package com.huntercoles.fatline.basicfeature.presentation

import com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
import com.huntercoles.fatline.basicfeature.domain.model.TournamentConfig

/**
 * UI state for the calculator screen
 */
data class CalculatorUiState(
    val tournamentConfig: TournamentConfig = TournamentConfig(),
    val payouts: List<PayoutPosition> = emptyList(),
    val isLoading: Boolean = false,
    val isTournamentLocked: Boolean = false,
    val isConfigExpanded: Boolean = true,
    val showResetDialog: Boolean = false
)