package com.huntercoles.fatline.settingsfeature.presentation

sealed class TimerIntent {
    data class GameDurationChanged(val minutes: Int) : TimerIntent()
    data class GameDurationHoursChanged(val hours: Int) : TimerIntent()
    data object ToggleTimer : TimerIntent()
    data object ResetTimer : TimerIntent()
    data class TimerTick(val seconds: Int) : TimerIntent()
    data object NextBlindLevel : TimerIntent()
    data object PreviousBlindLevel : TimerIntent()
    
    // Blind configuration intents
    data class UpdateSmallestChip(val value: Int) : TimerIntent()
    data class UpdateStartingChips(val value: Int) : TimerIntent()
    data class UpdateRoundLength(val minutes: Int) : TimerIntent()
    data class ToggleBlindConfigCollapsed(val collapsed: Boolean) : TimerIntent()

    // Blind customization intents
    data class UpdateBlindOverride(
        val level: Int,
        val smallBlind: Int,
        val bigBlind: Int,
        val ante: Int
    ) : TimerIntent()
    data class ApplyBlindOverrides(
        val overrides: Map<Int, BlindLevelOverride>
    ) : TimerIntent()
    data object ClearBlindOverrides : TimerIntent()
}