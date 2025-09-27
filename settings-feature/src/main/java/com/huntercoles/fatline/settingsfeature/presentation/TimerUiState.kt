package com.huntercoles.fatline.settingsfeature.presentation

import android.os.Parcelable
import com.huntercoles.fatline.core.utils.BlindLevel
import kotlinx.parcelize.Parcelize

enum class TimerDirection {
    COUNTDOWN, COUNTUP
}

@Parcelize
data class TimerUiState(
    val gameDurationMinutes: Int = 180, // Default 3 hours (kept for compatibility)
    val currentTimeSeconds: Int = 180 * 60, // Start with full time
    val timerDirection: TimerDirection = TimerDirection.COUNTDOWN,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val blindConfiguration: BlindConfiguration = BlindConfiguration(),
    val isBlindConfigCollapsed: Boolean = false, // Start auto open
    val hasTimerStarted: Boolean = false, // Track if timer has ever been started (stays true until reset)
    val playerCount: Int = 9,
    val blindLevels: List<BlindLevel> = emptyList(),
    val currentBlindLevelIndex: Int = 0
) : Parcelable {

    // Convert minutes to hours for UI display
    val gameDurationHours: Int
        get() = gameDurationMinutes / 60
    
    val totalDurationSeconds: Int
        get() = gameDurationMinutes * 60

    val formattedTime: String
        get() {
            val time = when (timerDirection) {
                TimerDirection.COUNTDOWN -> currentTimeSeconds.coerceAtLeast(0)
                TimerDirection.COUNTUP -> currentTimeSeconds
            }

            val hours = time / 3600
            val minutes = (time % 3600) / 60
            val seconds = time % 60
            return String.format("%d:%02d:%02d", hours, minutes, seconds)
        }

    val progress: Float
        get() = when (timerDirection) {
            TimerDirection.COUNTDOWN -> {
                if (totalDurationSeconds > 0) {
                    1f - (currentTimeSeconds.toFloat() / totalDurationSeconds)
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