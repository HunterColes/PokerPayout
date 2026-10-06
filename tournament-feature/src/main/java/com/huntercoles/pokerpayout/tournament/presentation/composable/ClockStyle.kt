package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.LevelTone
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimeTone
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

// How the clock's state looks: colours, the eyebrow, the pills.

/** The hero digits: gold; Danger in the final minutes; white on a break; dimmed while paused or done. */
internal fun heroColor(uiState: TimerUiState): Color = when {
    uiState.isFinished -> PokerColors.CardWhite.copy(alpha = FINISHED_ALPHA)
    uiState.isOnBreak -> PokerColors.CardWhite.copy(alpha = if (isPaused(uiState)) PokerColors.PokerPausedAlpha else 1f)
    isPaused(uiState) -> PokerColors.PokerGold.copy(alpha = PokerColors.PokerPausedAlpha)
    uiState.tone == TimeTone.CRITICAL -> PokerColors.Danger
    else -> PokerColors.PokerGold
}

internal fun isPaused(uiState: TimerUiState): Boolean = uiState.hasTimerStarted && !uiState.isRunning && !uiState.isFinished

/** The level bar's colour: Live, gold in the last quarter (and on breaks), Danger in the last tenth. */
internal fun levelTone(uiState: TimerUiState): LevelTone = when {
    uiState.isOnBreak -> LevelTone.Low
    uiState.tone == TimeTone.CRITICAL -> LevelTone.Critical
    uiState.tone == TimeTone.LOW -> LevelTone.Low
    else -> LevelTone.Normal
}

/** The eyebrow over the hero: "LEVEL 6 · TIME LEFT", "LEVEL 1 · READY", "LEVEL 10 · OVERTIME", "FINISHED". */
@Composable
internal fun clockEyebrow(uiState: TimerUiState): String {
    val level = uiState.currentLevelSegment?.level?.level ?: 1
    return when {
        uiState.isFinished -> stringResource(R.string.clock_finished)
        uiState.isOnBreak -> stringResource(R.string.clock_break_back_at, uiState.nextLevelSegment?.level?.level ?: level)
        !uiState.hasTimerStarted -> stringResource(R.string.clock_level_ready, level)
        uiState.currentLevelSegment?.isOvertime == true -> stringResource(R.string.clock_level_overtime, level)
        else -> stringResource(R.string.clock_level_time_left, level)
    }
}

/** A word for each state colour shows too (design spec §6.3): ready, paused, final minutes. */
internal data class ClockPill(val text: String, val tone: PokerPillTone)

@Composable
internal fun clockPills(uiState: TimerUiState): List<ClockPill> {
    // Overtime is in the eyebrow already ("LEVEL 10 · OVERTIME"), in words as well as in red.
    val finalMinutes = uiState.hasTimerStarted && !uiState.isOnBreak && uiState.tone == TimeTone.CRITICAL
    return listOfNotNull(
        ClockPill(stringResource(R.string.clock_pill_ready), PokerPillTone.Gold).takeIf { !uiState.hasTimerStarted },
        ClockPill(stringResource(R.string.clock_pill_paused), PokerPillTone.Muted).takeIf { isPaused(uiState) },
        ClockPill(stringResource(R.string.clock_pill_final), PokerPillTone.Danger).takeIf { finalMinutes },
    )
}

/** The eyebrow's colour: gold, Danger in overtime, Chalk once it's over. */
internal fun eyebrowColor(uiState: TimerUiState): Color = when {
    uiState.isFinished -> PokerColors.Chalk
    uiState.currentLevelSegment?.isOvertime == true && !uiState.isOnBreak -> PokerColors.Danger
    else -> PokerColors.PokerGold
}

private const val FINISHED_ALPHA = 0.5f
