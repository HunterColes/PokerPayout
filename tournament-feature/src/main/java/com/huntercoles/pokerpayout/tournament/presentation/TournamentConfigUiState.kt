package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.tournament.domain.model.PayoutPosition
import com.huntercoles.pokerpayout.tournament.domain.model.TournamentConfig

/**
 * UI state for the tournament config screen
 */
data class TournamentConfigUiState(
    val tournamentConfig: TournamentConfig = TournamentConfig(),
    val payouts: List<PayoutPosition> = emptyList(),
    val isLoading: Boolean = false,
    val isTournamentLocked: Boolean = false,
    val isConfigExpanded: Boolean = true,
    val isBlindConfigExpanded: Boolean = false,
    val gameDurationHours: Int = TournamentDefaults.GAME_DURATION_HOURS,
    val roundLengthMinutes: Int = TournamentDefaults.ROUND_LENGTH_MINUTES,
    val smallestChip: Int = TournamentDefaults.SMALLEST_CHIP,
    val startingChips: Int = TournamentDefaults.STARTING_CHIPS,
    val showResetDialog: Boolean = false,
    val leaderboardNames: Map<Int, String> = emptyMap(),
    val rebuyPurchases: Int = 0,
    val addOnPurchases: Int = 0
)