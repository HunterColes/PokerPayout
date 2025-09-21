package com.huntercoles.fatline.settingsfeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun acceptIntent(intent: TimerIntent) {
        when (intent) {
            is TimerIntent.GameDurationChanged -> updateGameDuration(intent.minutes)
            is TimerIntent.TimerDirectionChanged -> updateTimerDirection(intent.direction)
            is TimerIntent.ToggleTimer -> toggleTimer()
            is TimerIntent.ResetTimer -> resetTimer()
            is TimerIntent.TimerTick -> updateTimer(intent.seconds)
        }
    }

    private fun updateGameDuration(minutes: Int) {
        val validMinutes = minutes.coerceIn(1, 1440) // 1 minute to 24 hours
        _uiState.update { state ->
            val newTotalSeconds = validMinutes * 60
            val newCurrentSeconds = when (state.timerDirection) {
                TimerDirection.COUNTDOWN -> newTotalSeconds
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                gameDurationMinutes = validMinutes,
                currentTimeSeconds = newCurrentSeconds,
                isFinished = false
            )
        }
        stopTimer()
    }

    private fun updateTimerDirection(direction: TimerDirection) {
        _uiState.update { state ->
            val newCurrentSeconds = when (direction) {
                TimerDirection.COUNTDOWN -> state.totalDurationSeconds
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                timerDirection = direction,
                currentTimeSeconds = newCurrentSeconds,
                isFinished = false
            )
        }
        stopTimer()
    }

    private fun toggleTimer() {
        val currentState = _uiState.value
        if (currentState.isRunning) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return

        _uiState.update { it.copy(isRunning = true, isFinished = false) }

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000) // 1 second

                val currentState = _uiState.value
                val newTimeSeconds = when (currentState.timerDirection) {
                    TimerDirection.COUNTDOWN -> currentState.currentTimeSeconds - 1
                    TimerDirection.COUNTUP -> currentState.currentTimeSeconds + 1
                }

                // Check if timer should finish
                val shouldFinish = when (currentState.timerDirection) {
                    TimerDirection.COUNTDOWN -> newTimeSeconds <= 0
                    TimerDirection.COUNTUP -> newTimeSeconds >= currentState.totalDurationSeconds
                }

                if (shouldFinish) {
                    _uiState.update {
                        it.copy(
                            currentTimeSeconds = when (it.timerDirection) {
                                TimerDirection.COUNTDOWN -> 0
                                TimerDirection.COUNTUP -> it.totalDurationSeconds
                            },
                            isRunning = false,
                            isFinished = true
                        )
                    }
                    break
                } else {
                    _uiState.update { it.copy(currentTimeSeconds = newTimeSeconds) }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    private fun resetTimer() {
        stopTimer()
        _uiState.update { state ->
            val resetSeconds = when (state.timerDirection) {
                TimerDirection.COUNTDOWN -> state.totalDurationSeconds
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                currentTimeSeconds = resetSeconds,
                isRunning = false,
                isFinished = false
            )
        }
    }

    private fun updateTimer(seconds: Int) {
        _uiState.update { it.copy(currentTimeSeconds = seconds) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}