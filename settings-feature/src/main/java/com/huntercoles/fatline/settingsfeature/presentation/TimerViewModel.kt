package com.huntercoles.fatline.settingsfeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.fatline.core.preferences.TimerPreferences
import com.huntercoles.fatline.core.preferences.TournamentPreferences
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
class TimerViewModel @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Restore timer state from preferences
        restoreTimerState()
    }

    private fun restoreTimerState() {
        val actualTime = timerPreferences.calculateActualTime()
        val direction = when (timerPreferences.getTimerDirection()) {
            "COUNTUP" -> TimerDirection.COUNTUP
            else -> TimerDirection.COUNTDOWN
        }
        
        _uiState.update { 
            it.copy(
                gameDurationMinutes = timerPreferences.getGameDurationMinutes(),
                currentTimeSeconds = actualTime,
                timerDirection = direction,
                isRunning = timerPreferences.getTimerRunning() && !timerPreferences.getIsFinished(),
                isFinished = timerPreferences.getIsFinished(),
                hasTimerStarted = timerPreferences.getHasTimerStarted()
            )
        }
        
        // Update preferences with the calculated time
        timerPreferences.setCurrentTimeSeconds(actualTime)
        
        // If timer was running and not finished, continue it
        if (timerPreferences.getTimerRunning() && !timerPreferences.getIsFinished()) {
            startTimer()
        }
    }

    fun acceptIntent(intent: TimerIntent) {
        when (intent) {
            is TimerIntent.GameDurationChanged -> updateGameDuration(intent.minutes)
            is TimerIntent.GameDurationHoursChanged -> updateGameDurationHours(intent.hours)
            is TimerIntent.TimerDirectionChanged -> updateTimerDirection(intent.direction)
            is TimerIntent.ToggleTimer -> toggleTimer()
            is TimerIntent.ResetTimer -> resetTimer()
            is TimerIntent.TimerTick -> updateTimer(intent.seconds)
            
            // Blind configuration intents
            is TimerIntent.UpdateSmallestChip -> updateSmallestChip(intent.value)
            is TimerIntent.UpdateStartingChips -> updateStartingChips(intent.value)
            is TimerIntent.UpdateRoundLength -> updateRoundLength(intent.minutes)
            is TimerIntent.ToggleBlindConfigCollapsed -> toggleBlindConfigCollapsed(intent.collapsed)
        }
    }

    private fun updateGameDuration(minutes: Int) {
        val validMinutes = minutes.coerceIn(1, 1440) // 1 minute to 24 hours
        timerPreferences.setGameDurationMinutes(validMinutes)
        
        _uiState.update { state ->
            val newTotalSeconds = validMinutes * 60
            val newCurrentSeconds = when (state.timerDirection) {
                TimerDirection.COUNTDOWN -> newTotalSeconds
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                gameDurationMinutes = validMinutes,
                currentTimeSeconds = newCurrentSeconds,
                isFinished = false,
                hasTimerStarted = state.hasTimerStarted  // Preserve hasTimerStarted
            )
        }
        
        timerPreferences.setCurrentTimeSeconds(_uiState.value.currentTimeSeconds)
        timerPreferences.setIsFinished(false)
        stopTimer()
    }

    private fun updateGameDurationHours(hours: Int) {
        val validHours = hours.coerceIn(1, 24) // 1 hour to 24 hours
        val minutes = validHours * 60
        updateGameDuration(minutes)
    }

    private fun updateTimerDirection(direction: TimerDirection) {
        val directionString = when (direction) {
            TimerDirection.COUNTDOWN -> "COUNTDOWN"
            TimerDirection.COUNTUP -> "COUNTUP"
        }
        timerPreferences.setTimerDirection(directionString)
        
        _uiState.update { state ->
            val newCurrentSeconds = when (direction) {
                TimerDirection.COUNTDOWN -> state.totalDurationSeconds
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                timerDirection = direction,
                currentTimeSeconds = newCurrentSeconds,
                isFinished = false,
                hasTimerStarted = state.hasTimerStarted  // Preserve hasTimerStarted
            )
        }
        
        timerPreferences.setCurrentTimeSeconds(_uiState.value.currentTimeSeconds)
        timerPreferences.setIsFinished(false)
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

        _uiState.update { 
            it.copy(
                isRunning = true, 
                isFinished = false,
                isBlindConfigCollapsed = true,  // Auto-collapse when timer starts
                hasTimerStarted = true  // Mark that timer has been started (stays true until reset)
            ) 
        }
        timerPreferences.setTimerRunning(true)
        timerPreferences.setIsFinished(false)
        timerPreferences.setHasTimerStarted(true)  // Set the preference
        
        // Lock tournament settings when timer starts
        tournamentPreferences.setTournamentLocked(true)

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
                    val finalSeconds = when (currentState.timerDirection) {
                        TimerDirection.COUNTDOWN -> 0
                        TimerDirection.COUNTUP -> currentState.totalDurationSeconds
                    }
                    
                    _uiState.update {
                        it.copy(
                            currentTimeSeconds = finalSeconds,
                            isRunning = false,
                            isFinished = true
                        )
                    }
                    
                    timerPreferences.setCurrentTimeSeconds(finalSeconds)
                    timerPreferences.setTimerRunning(false)
                    timerPreferences.setIsFinished(true)
                    break
                } else {
                    _uiState.update { it.copy(currentTimeSeconds = newTimeSeconds) }
                    timerPreferences.setCurrentTimeSeconds(newTimeSeconds)
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
        timerPreferences.setTimerRunning(false)
    }

    private fun resetTimer() {
        stopTimer()
        
        // Unlock tournament settings when timer is reset
        tournamentPreferences.setTournamentLocked(false)
        
        _uiState.update { state ->
            val resetSeconds = when (state.timerDirection) {
                TimerDirection.COUNTDOWN -> state.totalDurationSeconds
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                currentTimeSeconds = resetSeconds,
                isRunning = false,
                isFinished = false,
                isBlindConfigCollapsed = false,  // Unlock and expand blind config on reset
                hasTimerStarted = false  // Reset the timer started flag
            )
        }
        
        timerPreferences.resetTimer()
    }

    private fun updateTimer(seconds: Int) {
        _uiState.update { it.copy(currentTimeSeconds = seconds) }
        timerPreferences.setCurrentTimeSeconds(seconds)
    }

    // Blind configuration methods
    private fun updateSmallestChip(value: Int) {
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(smallestChip = value)
            )
        }
    }

    private fun updateStartingChips(value: Int) {
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(startingChips = value)
            )
        }
    }

    private fun updateRoundLength(minutes: Int) {
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(roundLengthMinutes = minutes)
            )
        }
    }

    private fun toggleBlindConfigCollapsed(collapsed: Boolean) {
        _uiState.update { state ->
            state.copy(isBlindConfigCollapsed = collapsed)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}