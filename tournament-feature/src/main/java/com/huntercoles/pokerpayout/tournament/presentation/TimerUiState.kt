package com.huntercoles.pokerpayout.tournament.presentation

import android.os.Parcelable
import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import kotlinx.parcelize.Parcelize

enum class TimerDirection {
    COUNTDOWN, COUNTUP
}

@Parcelize
data class TimerUiState(
    val gameDurationMinutes: Int = TournamentDefaults.GAME_DURATION_HOURS * 60,
    val currentTimeSeconds: Int = TournamentDefaults.GAME_DURATION_HOURS * 60 * 60,
    val timerDirection: TimerDirection = TimerDirection.COUNTDOWN,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val blindConfiguration: BlindConfiguration = BlindConfiguration(),
    val isBlindConfigCollapsed: Boolean = false, // Start auto open
    val hasTimerStarted: Boolean = false, // Track if timer has ever been started (stays true until reset)
    val playerCount: Int = TournamentDefaults.PLAYER_COUNT,
    val baseBlindLevels: List<BlindLevel> = emptyList(),
    val blindLevels: List<BlindLevel> = emptyList(),
    val currentBlindLevelIndex: Int = 0
) : Parcelable {

    // Convert minutes to hours for UI display
    val gameDurationHours: Int
        get() = gameDurationMinutes / 60
    
    val totalDurationSeconds: Int
        get() = gameDurationMinutes * 60

    val isOvertime: Boolean
        get() = timerDirection == TimerDirection.COUNTDOWN && currentTimeSeconds < 0

    val formattedTime: String
        get() {
            val rawSeconds = when (timerDirection) {
                TimerDirection.COUNTDOWN -> if (currentTimeSeconds >= 0) currentTimeSeconds else -currentTimeSeconds
                TimerDirection.COUNTUP -> currentTimeSeconds.coerceAtLeast(0)
            }

            val hours = rawSeconds / 3600
            val minutes = (rawSeconds % 3600) / 60
            val seconds = rawSeconds % 60
            val formatted = String.format("%d:%02d:%02d", hours, minutes, seconds)
            return if (isOvertime) "+$formatted" else formatted
        }

    val progress: Float
        get() = when (timerDirection) {
            TimerDirection.COUNTDOWN -> {
                if (totalDurationSeconds > 0) {
                    val remaining = currentTimeSeconds.coerceAtLeast(0)
                    (1f - (remaining.toFloat() / totalDurationSeconds)).coerceIn(0f, 1f)
                } else 0f
            }
            TimerDirection.COUNTUP -> {
                if (totalDurationSeconds > 0) {
                    (currentTimeSeconds.toFloat() / totalDurationSeconds).coerceAtMost(1f)
                } else 0f
            }
        }

    val currentBlindLevel: BlindLevel?
        get() = blindLevels.getOrNull(currentBlindLevelIndex)

    val nextBlindLevel: BlindLevel?
        get() = blindLevels.getOrNull(currentBlindLevelIndex + 1)

    val nextLevelStartsInSeconds: Int?
        get() {
            val next = nextBlindLevel ?: return null
            val targetSeconds = next.roundStartMinute * 60
            val elapsedSeconds = when (timerDirection) {
                TimerDirection.COUNTDOWN -> totalDurationSeconds - currentTimeSeconds
                TimerDirection.COUNTUP -> currentTimeSeconds
            }
            return (targetSeconds - elapsedSeconds).takeIf { it > 0 }
        }

    val isTimeLow: Boolean
        get() = when (timerDirection) {
            TimerDirection.COUNTDOWN -> currentTimeSeconds <= totalDurationSeconds * 0.25 // Last 25%
            TimerDirection.COUNTUP -> currentTimeSeconds >= totalDurationSeconds * 0.75 // Last 25%
        }

    val isTimeCritical: Boolean
        get() = when (timerDirection) {
            TimerDirection.COUNTDOWN -> currentTimeSeconds <= totalDurationSeconds * 0.1 // Last 10%
            TimerDirection.COUNTUP -> currentTimeSeconds >= totalDurationSeconds * 0.9 // Last 10%
        }
}