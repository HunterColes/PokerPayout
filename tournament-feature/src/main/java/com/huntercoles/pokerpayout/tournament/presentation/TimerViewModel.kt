package com.huntercoles.pokerpayout.tournament.presentation

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
        val direction = when (storedDirection) {
            "COUNTUP" -> TimerDirection.COUNTUP
            else -> TimerDirection.COUNTDOWN
        }
        val finishedPref = timerPreferences.getIsFinished()
        val isRunning = timerPreferences.getTimerRunning()
        val hasStarted = timerPreferences.getHasTimerStarted()
        
        _uiState.update { 
            it.copy(
                gameDurationMinutes = timerPreferences.getGameDurationMinutes(),
                currentTimeSeconds = actualTime,
                timerDirection = direction,
                isRunning = isRunning,
                isFinished = finishedPref,
                hasTimerStarted = hasStarted
            )
        }
        
        // Update preferences with the calculated time only if timer was running
        if (isRunning && !finishedPref) {
            timerPreferences.setCurrentTimeSeconds(actualTime)
            // Continue the timer after a brief delay to ensure UI is ready
            viewModelScope.launch {
                delay(100) // Brief delay to ensure restoration is complete
                startTimer()
            }
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

            // Dialog intents
            is TimerIntent.ShowInvalidConfigDialog -> _uiState.update { it.copy(showInvalidConfigDialog = true) }
            is TimerIntent.HideInvalidConfigDialog -> _uiState.update { it.copy(showInvalidConfigDialog = false) }
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
            // Validate blind configuration before starting
            if (!isValidBlindConfiguration(currentState)) {
                _uiState.update { it.copy(showInvalidConfigDialog = true) }
                return
            }

            startTimer()
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return

        val currentState = _uiState.value
        
        // Calculate final time once when timer starts
        val finalTimeSeconds = calculateFinalTimeSeconds(currentState)

        _uiState.update { 
            it.copy(
                isRunning = true, 
                isFinished = false,
                finalTimeSeconds = finalTimeSeconds,
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

                val state = _uiState.value
                val newTimeSeconds = when (state.timerDirection) {
                    TimerDirection.COUNTDOWN -> state.currentTimeSeconds - 1
                    TimerDirection.COUNTUP -> state.currentTimeSeconds + 1
                }

                // Check if we need to switch from COUNTDOWN to COUNTUP
                val shouldSwitchToCountUp = state.timerDirection == TimerDirection.COUNTDOWN && 
                    newTimeSeconds <= 0

                if (shouldSwitchToCountUp) {
                    // Switch to COUNTUP mode at 0
                    _uiState.update { 
                        it.copy(
                            currentTimeSeconds = 0,
                            timerDirection = TimerDirection.COUNTUP
                        )
                    }
                    timerPreferences.setCurrentTimeSeconds(0)
                    timerPreferences.setTimerDirection("COUNTUP")
                    continue // Skip the rest of the loop and continue with COUNTUP
                }

                val reachedCountUpLimit = state.timerDirection == TimerDirection.COUNTUP &&
                    newTimeSeconds >= state.finalTimeSeconds

                if (reachedCountUpLimit) {
                    val finalSeconds = state.finalTimeSeconds
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
                        isFinished = if (state.timerDirection == TimerDirection.COUNTDOWN) false else it.isFinished
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
            val resetSeconds = resetDurationMinutes * 60
            // Reset blind configuration to defaults from tournament preferences
            val resetBlindConfig = BlindConfiguration(
                smallestChip = tournamentPreferences.getSmallestChip(),
                startingChips = tournamentPreferences.getStartingChips(),
                roundLengthMinutes = tournamentPreferences.getRoundLengthMinutes()
            )
            state.copy(
                gameDurationMinutes = resetDurationMinutes,
                currentTimeSeconds = resetSeconds,
                timerDirection = TimerDirection.COUNTDOWN, // Always reset to COUNTDOWN
                isRunning = false,
                isFinished = false,
                isBlindConfigCollapsed = false,  // Unlock and expand blind config on reset
                hasTimerStarted = false,  // Reset the timer started flag
                finalTimeSeconds = resetDurationMinutes * 60, // Reset final time to tournament duration
                showInvalidConfigDialog = false,  // Clear any invalid config dialog
                blindConfiguration = resetBlindConfig // Reset blind configuration to defaults
            )
        }
        
        timerPreferences.resetTimer()
        // Regenerate blind schedule to ensure validation works correctly after reset
        regenerateBlindSchedule()
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
        resetTimerToFreshState()
        regenerateBlindSchedule()
    }

    private fun updateStartingChips(value: Int) {
        val sanitizedValue = value.coerceAtLeast(1)
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(startingChips = sanitizedValue)
            )
        }
        resetTimerToFreshState()
        regenerateBlindSchedule()
    }

    private fun updateRoundLength(minutes: Int) {
        val sanitizedMinutes = minutes.coerceAtLeast(1)
        _uiState.update { state ->
            state.copy(
                blindConfiguration = state.blindConfiguration.copy(roundLengthMinutes = sanitizedMinutes)
            )
        }
        resetTimerToFreshState()
        regenerateBlindSchedule()
    }

    private fun toggleBlindConfigCollapsed(collapsed: Boolean) {
        _uiState.update { state ->
            state.copy(isBlindConfigCollapsed = collapsed)
        }
    }

    private fun resetTimerToFreshState() {
        stopTimer()
        tournamentPreferences.setTournamentLocked(false)
        
        _uiState.update { state ->
            val resetSeconds = state.gameDurationMinutes * 60
            state.copy(
                currentTimeSeconds = resetSeconds,
                timerDirection = TimerDirection.COUNTDOWN,
                isRunning = false,
                isFinished = false,
                hasTimerStarted = false,  // Reset to fresh state
                isBlindConfigCollapsed = false
            )
        }
        
        timerPreferences.setCurrentTimeSeconds(_uiState.value.currentTimeSeconds)
        timerPreferences.setTimerDirection("COUNTDOWN")
        timerPreferences.setTimerRunning(false)
        timerPreferences.setIsFinished(false)
        timerPreferences.setHasTimerStarted(false)
    }

    override fun onCleared() {
        super.onCleared()
        val currentState = _uiState.value
        
        // If timer is running, preserve state for restoration
        if (currentState.isRunning && !currentState.isFinished) {
            // Only cancel the job, don't call stopTimer() which clears the running state
            timerJob?.cancel()
            timerJob = null
            
            // Persist current state
            timerPreferences.setTimerRunning(true)
            timerPreferences.setCurrentTimeSeconds(currentState.currentTimeSeconds)
            timerPreferences.setTimerDirection(when (currentState.timerDirection) {
                TimerDirection.COUNTDOWN -> "COUNTDOWN"
                TimerDirection.COUNTUP -> "COUNTUP"
            })
            timerPreferences.setIsFinished(currentState.isFinished)
            timerPreferences.setHasTimerStarted(currentState.hasTimerStarted)
        } else {
            // Timer is stopped or finished, clean stop
            stopTimer()
        }
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
                currentBlindLevelIndex = levelIndex,
                finalTimeSeconds = calculateFinalTimeSeconds(it.copy(blindLevels = schedule))
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
            TimerDirection.COUNTUP -> state.totalDurationSeconds + state.currentTimeSeconds // Add overtime to tournament duration
        }
        val elapsedMinutes = elapsedSeconds / 60
        val index = levels.indexOfLast { elapsedMinutes >= it.roundStartMinute }
        return if (index == -1) 0 else index.coerceIn(0, levels.lastIndex)
    }

    private fun calculateFinalTimeSeconds(state: TimerUiState): Int {
        return state.blindLevels.lastOrNull()?.let { finalLevel ->
            val roundLength = state.blindConfiguration.roundLengthMinutes
            (finalLevel.roundStartMinute + roundLength) * 60
        } ?: (state.totalDurationSeconds + (60 * 60)) // Fallback to 60 minutes over
    }

    private fun isValidBlindConfiguration(state: TimerUiState): Boolean {
        val levels = state.blindLevels
        if (levels.isEmpty()) return false
        
        // Check that levels are properly ordered by start time
        for (i in 1 until levels.size) {
            if (levels[i].roundStartMinute <= levels[i-1].roundStartMinute) {
                return false
            }
        }
        
        // Check that blinds increase monotonically
        for (i in 1 until levels.size) {
            if (levels[i].smallBlind <= levels[i-1].smallBlind) {
                return false
            }
        }
        
        // Check that the final level ends after the tournament duration
        val finalLevel = levels.last()
        val roundLength = state.blindConfiguration.roundLengthMinutes
        val finalEndTime = (finalLevel.roundStartMinute + roundLength) * 60
        if (finalEndTime <= state.totalDurationSeconds) {
            return false
        }
        
        return true
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
        
        // Determine appropriate timer direction and time based on elapsed time vs tournament duration
        val isInOvertime = elapsedSecondsForLevel >= state.totalDurationSeconds
        val newTimerDirection = if (isInOvertime) TimerDirection.COUNTUP else TimerDirection.COUNTDOWN
        
        val newCurrentSeconds = when (newTimerDirection) {
            TimerDirection.COUNTDOWN -> state.totalDurationSeconds - elapsedSecondsForLevel
            TimerDirection.COUNTUP -> elapsedSecondsForLevel - state.totalDurationSeconds // Show overtime from zero
        }

        val hasStarted = targetIndex > 0 || newCurrentSeconds != state.totalDurationSeconds

        _uiState.update {
            it.copy(
                currentTimeSeconds = newCurrentSeconds,
                currentBlindLevelIndex = targetIndex,
                timerDirection = newTimerDirection,
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