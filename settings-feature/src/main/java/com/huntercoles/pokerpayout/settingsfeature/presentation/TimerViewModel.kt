package com.huntercoles.pokerpayout.settingsfeature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timerPreferences: TimerPreferences,
    private val tournamentPreferences: TournamentPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var latestPlayerCount: Int = runBlocking {
        runCatching { tournamentPreferences.playerCount.first() }
            .getOrElse { TimerUiState().playerCount }
    }

    init {
        // Restore timer state from preferences
        restoreTimerState()
        observePlayerCount()
        regenerateBlindSchedule()
    }

    private fun restoreTimerState() {
        val actualTime = timerPreferences.calculateActualTime()
        val storedDirection = timerPreferences.getTimerDirection()
        if (storedDirection != "COUNTDOWN") {
            timerPreferences.setTimerDirection("COUNTDOWN")
        }
        val direction = TimerDirection.COUNTDOWN
        val finishedPref = timerPreferences.getIsFinished()
        if (finishedPref && direction == TimerDirection.COUNTDOWN) {
            timerPreferences.setIsFinished(false)
        }
        
        _uiState.update { 
            it.copy(
                gameDurationMinutes = timerPreferences.getGameDurationMinutes(),
                currentTimeSeconds = actualTime,
                timerDirection = direction,
                isRunning = timerPreferences.getTimerRunning(),
                isFinished = if (direction == TimerDirection.COUNTDOWN) false else finishedPref,
                hasTimerStarted = timerPreferences.getHasTimerStarted()
            )
        }
        
        // Update preferences with the calculated time
        timerPreferences.setCurrentTimeSeconds(actualTime)
        
        // If timer was running and not finished, continue it
        if (timerPreferences.getTimerRunning()) {
            startTimer()
        }
        regenerateBlindSchedule()
    }

    fun acceptIntent(intent: TimerIntent) {
        when (intent) {
            is TimerIntent.GameDurationChanged -> updateGameDuration(intent.minutes)
            is TimerIntent.GameDurationHoursChanged -> updateGameDurationHours(intent.hours)
            is TimerIntent.ToggleTimer -> toggleTimer()
            is TimerIntent.ResetTimer -> resetTimer()
            is TimerIntent.TimerTick -> updateTimer(intent.seconds)
            is TimerIntent.NextBlindLevel -> goToNextBlindLevel()
            is TimerIntent.PreviousBlindLevel -> goToPreviousBlindLevel()
            
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
        regenerateBlindSchedule()
    }

    private fun updateGameDurationHours(hours: Int) {
        val validHours = hours.coerceIn(1, 24) // 1 hour to 24 hours
        val minutes = validHours * 60
        updateGameDuration(minutes)
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

                val reachedCountUpLimit = currentState.timerDirection == TimerDirection.COUNTUP &&
                    newTimeSeconds >= currentState.totalDurationSeconds

                if (reachedCountUpLimit) {
                    val finalSeconds = currentState.totalDurationSeconds
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
                }

                _uiState.update {
                    it.copy(
                        currentTimeSeconds = newTimeSeconds,
                        isFinished = if (currentState.timerDirection == TimerDirection.COUNTDOWN) false else it.isFinished
                    )
                }
                timerPreferences.setCurrentTimeSeconds(newTimeSeconds)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
        timerPreferences.setTimerRunning(false)
        
        // Unlock tournament settings when timer is paused
        tournamentPreferences.setTournamentLocked(false)
    }

    private fun resetTimer() {
        stopTimer()
        
        // Unlock tournament settings when timer is reset
        tournamentPreferences.setTournamentLocked(false)
        
        // Reload duration from preferences in case it was reset
        val resetDurationMinutes = timerPreferences.getGameDurationMinutes()
        
        _uiState.update { state ->
            val resetSeconds = when (state.timerDirection) {
                TimerDirection.COUNTDOWN -> resetDurationMinutes * 60
                TimerDirection.COUNTUP -> 0
            }
            state.copy(
                gameDurationMinutes = resetDurationMinutes,
                currentTimeSeconds = resetSeconds,
                isRunning = false,
                isFinished = false,
                isBlindConfigCollapsed = false,  // Unlock and expand blind config on reset
                hasTimerStarted = false  // Reset the timer started flag
            )
        }
        
        timerPreferences.resetTimer()
        updateCurrentBlindLevel()
    }

    private fun updateTimer(seconds: Int) {
        _uiState.update { it.copy(currentTimeSeconds = seconds) }
        timerPreferences.setCurrentTimeSeconds(seconds)
        updateCurrentBlindLevel()
    }

    // Blind configuration methods
    private fun updateSmallestChip(value: Int) {
        val sanitizedValue = value.coerceAtLeast(1)
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(smallestChip = sanitizedValue)
            )
        }
        regenerateBlindSchedule()
    }

    private fun updateStartingChips(value: Int) {
        val sanitizedValue = value.coerceAtLeast(1)
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(startingChips = sanitizedValue)
            )
        }
        regenerateBlindSchedule()
    }

    private fun updateRoundLength(minutes: Int) {
        val sanitizedMinutes = minutes.coerceAtLeast(1)
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(roundLengthMinutes = sanitizedMinutes)
            )
        }
        regenerateBlindSchedule()
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

    private fun observePlayerCount() {
        viewModelScope.launch {
            tournamentPreferences.playerCount.collect { count ->
                latestPlayerCount = count
                regenerateBlindSchedule()
            }
        }
    }

    private fun regenerateBlindSchedule() {
        val state = _uiState.value
        val roundLength = state.blindConfiguration.roundLengthMinutes
        if (roundLength <= 0 || state.gameDurationMinutes <= 0) {
            _uiState.update {
                it.copy(
                    playerCount = latestPlayerCount,
                    baseBlindLevels = emptyList(),
                    blindLevels = emptyList(),
                    currentBlindLevelIndex = 0
                )
            }
            return
        }

        val input = BlindStructureInput(
            players = latestPlayerCount,
            targetDurationMinutes = state.gameDurationMinutes,
            smallestChip = state.blindConfiguration.smallestChip,
            startingStack = state.blindConfiguration.startingChips,
            roundLengthMinutes = roundLength
        )

        val schedule = runCatching { BlindStructureCalculator.generateSchedule(input) }
            .getOrElse { emptyList() }

        val levelIndex = if (schedule.isEmpty()) {
            0
        } else {
            calculateBlindLevelIndex(schedule, state).coerceIn(0, schedule.lastIndex)
        }

        _uiState.update {
            it.copy(
                playerCount = latestPlayerCount,
                baseBlindLevels = schedule,
                blindLevels = schedule,
                currentBlindLevelIndex = levelIndex
            )
        }
    }

    private fun updateCurrentBlindLevel() {
        val state = _uiState.value
        if (state.blindLevels.isEmpty()) return

        val newIndex = calculateBlindLevelIndex(state.blindLevels, state)
        if (newIndex != state.currentBlindLevelIndex) {
            _uiState.update { it.copy(currentBlindLevelIndex = newIndex) }
        }
    }

    private fun calculateBlindLevelIndex(levels: List<BlindLevel>, state: TimerUiState): Int {
        if (levels.isEmpty()) return 0
        val elapsedSeconds = when (state.timerDirection) {
            TimerDirection.COUNTDOWN -> state.totalDurationSeconds - state.currentTimeSeconds
            TimerDirection.COUNTUP -> state.currentTimeSeconds.coerceAtMost(state.totalDurationSeconds)
        }
        val elapsedMinutes = elapsedSeconds / 60
        val index = levels.indexOfLast { elapsedMinutes >= it.roundStartMinute }
        return if (index == -1) 0 else index.coerceIn(0, levels.lastIndex)
    }

    private fun goToNextBlindLevel() {
        val state = _uiState.value
        if (state.blindLevels.isEmpty()) return
        val targetIndex = (state.currentBlindLevelIndex + 1).coerceAtMost(state.blindLevels.lastIndex)
        if (targetIndex == state.currentBlindLevelIndex) return
        jumpToBlindLevel(targetIndex)
    }

    private fun goToPreviousBlindLevel() {
        val state = _uiState.value
        if (state.blindLevels.isEmpty()) return
        if (state.currentBlindLevelIndex <= 0) {
            resetTimer()
            return
        }
        val targetIndex = state.currentBlindLevelIndex - 1
        jumpToBlindLevel(targetIndex)
    }

    private fun jumpToBlindLevel(targetIndex: Int) {
        val state = _uiState.value
        val levels = state.blindLevels
        if (targetIndex !in levels.indices) return

        val targetLevel = levels[targetIndex]
        val elapsedSecondsForLevel = (targetLevel.roundStartMinute * 60).coerceAtLeast(0)
        val newCurrentSeconds = when (state.timerDirection) {
            TimerDirection.COUNTDOWN -> state.totalDurationSeconds - elapsedSecondsForLevel
            TimerDirection.COUNTUP -> elapsedSecondsForLevel.coerceAtMost(state.totalDurationSeconds)
        }

        val hasStarted = targetIndex > 0 || newCurrentSeconds != state.totalDurationSeconds

        _uiState.update {
            it.copy(
                currentTimeSeconds = newCurrentSeconds,
                currentBlindLevelIndex = targetIndex,
                isFinished = false,
                hasTimerStarted = it.hasTimerStarted || hasStarted
            )
        }

        timerPreferences.setCurrentTimeSeconds(_uiState.value.currentTimeSeconds)
        if (hasStarted) {
            timerPreferences.setHasTimerStarted(true)
        }
        timerPreferences.setIsFinished(false)
        updateCurrentBlindLevel()
    }
}