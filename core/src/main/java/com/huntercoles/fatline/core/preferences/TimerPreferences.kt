package com.huntercoles.fatline.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)
    
    private val _timerRunning = MutableStateFlow(getTimerRunning())
    val timerRunning: Flow<Boolean> = _timerRunning.asStateFlow()
    
    private val _currentTimeSeconds = MutableStateFlow(getCurrentTimeSeconds())
    val currentTimeSeconds: Flow<Int> = _currentTimeSeconds.asStateFlow()
    
    private val _gameDurationMinutes = MutableStateFlow(getGameDurationMinutes())
    val gameDurationMinutes: Flow<Int> = _gameDurationMinutes.asStateFlow()
    
    private val _timerDirection = MutableStateFlow(getTimerDirection())
    val timerDirection: Flow<String> = _timerDirection.asStateFlow()
    
    private val _isFinished = MutableStateFlow(getIsFinished())
    val isFinished: Flow<Boolean> = _isFinished.asStateFlow()
    
    private val _hasTimerStarted = MutableStateFlow(getHasTimerStarted())
    val hasTimerStarted: Flow<Boolean> = _hasTimerStarted.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow(getLastUpdateTime())
    val lastUpdateTime: Flow<Long> = _lastUpdateTime.asStateFlow()
    
    fun setTimerRunning(running: Boolean) {
        prefs.edit().putBoolean(TIMER_RUNNING_KEY, running).apply()
        _timerRunning.value = running
        if (running) {
            setLastUpdateTime(System.currentTimeMillis())
        }
    }
    
    fun setCurrentTimeSeconds(seconds: Int) {
        prefs.edit().putInt(CURRENT_TIME_SECONDS_KEY, seconds).apply()
        _currentTimeSeconds.value = seconds
        setLastUpdateTime(System.currentTimeMillis())
    }
    
    fun setGameDurationMinutes(minutes: Int) {
        prefs.edit().putInt(GAME_DURATION_MINUTES_KEY, minutes).apply()
        _gameDurationMinutes.value = minutes
    }
    
    fun setTimerDirection(direction: String) {
        prefs.edit().putString(TIMER_DIRECTION_KEY, direction).apply()
        _timerDirection.value = direction
    }
    
    fun setIsFinished(finished: Boolean) {
        prefs.edit().putBoolean(IS_FINISHED_KEY, finished).apply()
        _isFinished.value = finished
    }
    
    fun setHasTimerStarted(hasStarted: Boolean) {
        prefs.edit().putBoolean(HAS_TIMER_STARTED_KEY, hasStarted).apply()
        _hasTimerStarted.value = hasStarted
    }
    
    private fun setLastUpdateTime(time: Long) {
        prefs.edit().putLong(LAST_UPDATE_TIME_KEY, time).apply()
        _lastUpdateTime.value = time
    }
    
    fun getTimerRunning(): Boolean {
        return prefs.getBoolean(TIMER_RUNNING_KEY, false)
    }
    
    fun getCurrentTimeSeconds(): Int {
        return prefs.getInt(CURRENT_TIME_SECONDS_KEY, 180 * 60) // Default 3 hours
    }
    
    fun getGameDurationMinutes(): Int {
        return prefs.getInt(GAME_DURATION_MINUTES_KEY, 180) // Default 3 hours
    }
    
    fun getTimerDirection(): String {
        return prefs.getString(TIMER_DIRECTION_KEY, "COUNTDOWN") ?: "COUNTDOWN"
    }
    
    fun getIsFinished(): Boolean {
        return prefs.getBoolean(IS_FINISHED_KEY, false)
    }
    
    fun getHasTimerStarted(): Boolean {
        return prefs.getBoolean(HAS_TIMER_STARTED_KEY, false)
    }
    
    fun getLastUpdateTime(): Long {
        return prefs.getLong(LAST_UPDATE_TIME_KEY, System.currentTimeMillis())
    }
    
    // Calculate the actual time if timer was running in background
    fun calculateActualTime(): Int {
        if (!getTimerRunning() || getIsFinished()) {
            return getCurrentTimeSeconds()
        }
        
        val currentTime = System.currentTimeMillis()
        val lastUpdate = getLastUpdateTime()
        val elapsedSeconds = ((currentTime - lastUpdate) / 1000).toInt()
        
        val savedSeconds = getCurrentTimeSeconds()
        return when (getTimerDirection()) {
            "COUNTDOWN" -> savedSeconds - elapsedSeconds
            "COUNTUP" -> (savedSeconds + elapsedSeconds).coerceAtMost(getGameDurationMinutes() * 60)
            else -> savedSeconds - elapsedSeconds
        }
    }
    
    fun resetTimer() {
        val resetSeconds = when (getTimerDirection()) {
            "COUNTDOWN" -> getGameDurationMinutes() * 60
            "COUNTUP" -> 0
            else -> getGameDurationMinutes() * 60
        }
        setCurrentTimeSeconds(resetSeconds)
        setTimerRunning(false)
        setIsFinished(false)
        setHasTimerStarted(false)  // Reset the started flag
    }
    
    /**
     * Reset all timer data to default values
     */
    fun resetAllTimerData() {
        // Reset specific keys instead of clearing all preferences
        val currentTime = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(TIMER_RUNNING_KEY, false)
            .putInt(CURRENT_TIME_SECONDS_KEY, 180 * 60)
            .putInt(GAME_DURATION_MINUTES_KEY, 180)
            .putString(TIMER_DIRECTION_KEY, "COUNTDOWN")
            .putBoolean(IS_FINISHED_KEY, false)
            .putBoolean(HAS_TIMER_STARTED_KEY, false)
            .putLong(LAST_UPDATE_TIME_KEY, currentTime)
            .apply()
        
        // Reset all state flows to default values
        _timerRunning.value = false
        _currentTimeSeconds.value = 180 * 60 // 3 hours in seconds
        _gameDurationMinutes.value = 180 // 3 hours
        _timerDirection.value = "COUNTDOWN"
        _isFinished.value = false
        _hasTimerStarted.value = false
        _lastUpdateTime.value = currentTime
    }
    
    /**
     * Check if timer settings are in default state
     */
    fun isInDefaultState(): Boolean {
        return !getTimerRunning() &&
               getCurrentTimeSeconds() == 180 * 60 &&
               getGameDurationMinutes() == 180 &&
               getTimerDirection() == "COUNTDOWN" &&
               !getIsFinished() &&
               !getHasTimerStarted()
    }
    
    companion object {
        private const val TIMER_RUNNING_KEY = "timer_running"
        private const val CURRENT_TIME_SECONDS_KEY = "current_time_seconds"
        private const val GAME_DURATION_MINUTES_KEY = "game_duration_minutes"
        private const val TIMER_DIRECTION_KEY = "timer_direction"
        private const val IS_FINISHED_KEY = "is_finished"
        private const val HAS_TIMER_STARTED_KEY = "has_timer_started"
        private const val LAST_UPDATE_TIME_KEY = "last_update_time"
    }
}