package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.utils.BlindSetupFix

sealed class TimerIntent {
    data object ToggleTimer : TimerIntent()
    data object ResetTimer : TimerIntent()

    /** Jump to the start of the next level or break. Past the schedule this reveals an overtime level. */
    data object NextBlindLevel : TimerIntent()

    /** Jump to the start of the previous level or break. */
    data object PreviousBlindLevel : TimerIntent()

    /**
     * D5: adds [minutes] to the time left in the current level or break (negative takes time off).
     * The clock stays inside the segment: +1 can't go past its full length, and -1 stops a second
     * before its end, so the level change and its chime still happen.
     */
    data class NudgeMinutes(val minutes: Int) : TimerIntent()

    /** S4: on a break, back to the levels now ("everyone's back early"). */
    data object EndBreakNow : TimerIntent()

    /** S4: the host ticks off (or un-ticks) the current break's color-up. Saved per break. */
    data class MarkColorUpDone(val done: Boolean = true) : TimerIntent()

    /** The top bar's bell: chimes on or off (the same setting as Tools, Sound). */
    data object ToggleMute : TimerIntent()

    // Blind setup
    data class GameDurationHoursChanged(val hours: Int) : TimerIntent()
    data class UpdateSmallestChip(val value: Int) : TimerIntent()
    data class UpdateStartingChips(val value: Int) : TimerIntent()
    data class UpdateRoundLength(val minutes: Int) : TimerIntent()
    data class ApplyFix(val fix: BlindSetupFix) : TimerIntent()

    /**
     * A blind setup change made mid-game through "Unlock to edit…" (S1 v2): the schedule is rebuilt
     * and the clock stays on the same level with the same time left. [edit] is one of the blind setup
     * intents above. Before the start it is the same as sending [edit].
     */
    data class KeepingLevel(val edit: TimerIntent) : TimerIntent()

    /** The last level at which rebuys are allowed; 0 = no cutoff. */
    data class UpdateRebuyUntil(val level: Int) : TimerIntent()

    /** The last level at which a player can join late or re-enter (PP-116); 0 = no cutoff. */
    data class UpdateLateEntryUntil(val level: Int) : TimerIntent()

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

    /** S3 forced on with ⤢ (or off with ✕); rotating a phone shows it too, without this flag. */
    data class SetTableView(val enabled: Boolean) : TimerIntent()
}
