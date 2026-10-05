package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.utils.BlindSetupFix

sealed class TimerIntent {
    data object ToggleTimer : TimerIntent()
    data object ResetTimer : TimerIntent()

    /** Jump to the start of the next level or break. Past the schedule this reveals an overtime level. */
    data object NextBlindLevel : TimerIntent()

    /** Jump to the start of the previous level or break. */
    data object PreviousBlindLevel : TimerIntent()

    // Blind setup
    data class GameDurationHoursChanged(val hours: Int) : TimerIntent()
    data class UpdateSmallestChip(val value: Int) : TimerIntent()
    data class UpdateStartingChips(val value: Int) : TimerIntent()
    data class UpdateRoundLength(val minutes: Int) : TimerIntent()
    data class ApplyFix(val fix: BlindSetupFix) : TimerIntent()

    // Breaks and antes
    /** 0 turns breaks off. */
    data class UpdateBreakEvery(val levels: Int) : TimerIntent()
    data class UpdateBreakLength(val minutes: Int) : TimerIntent()
    data class UpdateBreakMessage(val message: String) : TimerIntent()

    /** 0 turns the big-blind ante off; otherwise the first level that has it. */
    data class UpdateBigBlindAnte(val fromLevel: Int) : TimerIntent()

    // Dialogs and views
    data object ShowInvalidConfigDialog : TimerIntent()
    data object HideInvalidConfigDialog : TimerIntent()
    data class SetTableView(val enabled: Boolean) : TimerIntent()
}
