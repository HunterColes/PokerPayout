package com.huntercoles.fatline.settingsfeature.presentation

sealed class TimerIntent {
    data class GameDurationChanged(val minutes: Int) : TimerIntent()
    data class TimerDirectionChanged(val direction: TimerDirection) : TimerIntent()
    data object ToggleTimer : TimerIntent()
    data object ResetTimer : TimerIntent()
    data class TimerTick(val seconds: Int) : TimerIntent()
}