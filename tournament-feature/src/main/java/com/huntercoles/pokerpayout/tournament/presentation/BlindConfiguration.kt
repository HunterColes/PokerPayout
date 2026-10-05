package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings

/** Everything that shapes the blind schedule and the clock. */
data class BlindConfiguration(
    val gameDurationMinutes: Int = TournamentDefaults.GAME_DURATION_HOURS * MINUTES_PER_HOUR,
    val roundLengthMinutes: Int = TournamentDefaults.ROUND_LENGTH_MINUTES,
    val smallestChip: Int = TournamentDefaults.SMALLEST_CHIP,
    val startingChips: Int = TournamentDefaults.STARTING_CHIPS,
    val breaks: BreakSettings = BreakSettings(),
    /** 1-based level from which the big blind antes one big blind; 0 = no ante. */
    val bigBlindAnteFromLevel: Int = 0
) {
    val gameDurationHours: Int get() = gameDurationMinutes / MINUTES_PER_HOUR

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}

/** Live table numbers for the clock, read from the Tournament and Bank settings. */
data class TableStats(
    val playerCount: Int = TournamentDefaults.PLAYER_COUNT,
    val playersLeft: Int = TournamentDefaults.PLAYER_COUNT,
    /** Chips in play / players left, counting each buy-in, rebuy and add-on as one starting stack. */
    val averageStack: Int = 0,
    val prizePool: Double = 0.0
)
