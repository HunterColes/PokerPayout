package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimeTone
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import java.text.NumberFormat
import java.util.Locale

/** Secondary text on the felt: readable, clearly below the gold and white of the live numbers. */
internal val Dim = PokerColors.CardWhite.copy(alpha = 0.62f)
internal val Faint = PokerColors.CardWhite.copy(alpha = 0.45f)
internal val Caption = TextStyle(fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)

internal const val SECONDS_PER_MINUTE = 60
private const val PILL_ROUNDING_PERCENT = 50

/** "READY" / "PAUSED" next to the level name. */
@Composable
internal fun StatusPill(text: String) {
    Text(
        text = text,
        style = Caption,
        color = PokerColors.FeltGreen,
        modifier = Modifier
            .background(PokerColors.PokerGold.copy(alpha = 0.85f), RoundedCornerShape(PILL_ROUNDING_PERCENT))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
internal fun rememberChipFormatter(): NumberFormat = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

internal fun blindsText(level: BlindLevel, formatter: NumberFormat) =
    "${formatter.format(level.smallBlind)} / ${formatter.format(level.bigBlind)}"

/** "Color up the 25s", "Color up the 100s, 500s and 1,000s". */
internal fun colorUpText(chips: List<Int>, formatter: NumberFormat): String {
    val names = chips.map { "${formatter.format(it)}s" }
    val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
    return "Color up the $list"
}

/** Progress bar: green, gold in the last quarter of a level, red in the last tenth (B14). */
internal fun toneColor(tone: TimeTone): Color = when (tone) {
    TimeTone.CRITICAL -> PokerColors.ErrorRed
    TimeTone.LOW -> PokerColors.PokerGold
    TimeTone.NORMAL -> PokerColors.AccentGreen
}

/** Hero digits: gold, red in the last tenth of a level, faded while paused or finished. */
internal fun heroColor(uiState: TimerUiState): Color = when {
    uiState.isFinished -> PokerColors.CardWhite.copy(alpha = 0.5f)
    uiState.hasTimerStarted && !uiState.isRunning -> PokerColors.PokerGold.copy(alpha = PokerColors.PokerPausedAlpha)
    uiState.tone == TimeTone.CRITICAL -> PokerColors.ErrorRed
    else -> PokerColors.PokerGold
}

internal fun headline(uiState: TimerUiState): String {
    val level = uiState.currentLevelSegment
    return when {
        uiState.timeline.isEmpty -> "BLINDS NOT SET"
        uiState.isFinished -> "FINISHED"
        uiState.isOnBreak -> "BREAK"
        level == null -> "LEVEL 1"
        level.isOvertime -> "LEVEL ${level.level.level} · OVERTIME"
        else -> "LEVEL ${level.level.level}"
    }
}

internal fun statusText(uiState: TimerUiState): String? = when {
    uiState.isFinished -> null
    !uiState.hasTimerStarted -> "READY"
    !uiState.isRunning -> "PAUSED"
    else -> null
}

/** "Next: 300 / 600", or what comes next when that's a break, the end, or overtime. */
internal fun nextText(uiState: TimerUiState, formatter: NumberFormat): String {
    val next = uiState.nextLevelSegment
    val upcomingBreak = uiState.timeline.segments.getOrNull(uiState.currentSegmentIndex + 1) as? BreakSegment
    return when {
        uiState.isFinished -> "Final level"
        upcomingBreak != null -> "Next: Break · ${upcomingBreak.durationSeconds / SECONDS_PER_MINUTE} min"
        next == null -> "Final level"
        uiState.isOnBreak -> "Next: Level ${next.level.level} · ${blindsText(next.level, formatter)}"
        next.isOvertime -> "Next: ${blindsText(next.level, formatter)} (overtime)"
        else -> "Next: ${blindsText(next.level, formatter)}"
    }
}
