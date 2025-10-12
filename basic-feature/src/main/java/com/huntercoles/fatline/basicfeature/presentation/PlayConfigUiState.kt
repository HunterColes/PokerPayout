package com.huntercoles.fatline.basicfeature.presentation

import com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
import com.huntercoles.fatline.basicfeature.domain.model.TournamentConfig

/**
 * UI state for the play config screen
 */
data class PlayConfigUiState(
    val tournamentConfig: TournamentConfig = TournamentConfig(),
    val payouts: List<PayoutPosition> = emptyList(),
    val isLoading: Boolean = false,
    val isTournamentLocked: Boolean = false,
    val isConfigExpanded: Boolean = true,
    val isBlindConfigExpanded: Boolean = false,
    val gameDurationHours: Int = 3,
    val roundLengthMinutes: Int = 20,
    val smallestChip: Int = 25,
    val startingChips: Int = 5000,
    val showResetDialog: Boolean = false,
    val leaderboardNames: Map<Int, String> = emptyMap(),
    val rebuyPurchases: Int = 0,
    val addOnPurchases: Int = 0
)