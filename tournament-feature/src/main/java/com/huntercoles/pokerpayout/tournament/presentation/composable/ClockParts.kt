package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.presentation.ClockFormat
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import java.text.NumberFormat

// Sizes relative to the blinds text, so the portrait card and the table view scale together
private const val BREAK_NOTE_SCALE = 0.75f
private const val ANTE_SCALE = 0.45f
private const val LINE_HEIGHT_SCALE = 1.1f
private const val ICON_IN_BUTTON = 0.6f
private const val DISABLED_ALPHA = 0.3f
private const val PROGRESS_WIDTH = 0.8f

@Composable
internal fun ToneProgress(uiState: TimerUiState, modifier: Modifier = Modifier.fillMaxWidth(PROGRESS_WIDTH)) {
    val color by animateColorAsState(toneColor(uiState.tone), label = "levelProgressColor")
    LinearProgressIndicator(
        progress = { uiState.segmentProgress },
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = PokerColors.CardWhite.copy(alpha = 0.12f),
        drawStopIndicator = {}
    )
}

/** Big current blinds, or during a break the break note and any color-up. */
@Composable
internal fun CurrentBlinds(segment: ClockSegment, formatter: NumberFormat, bigSize: TextUnit) {
    if (segment is BreakSegment) {
        Text(
            text = segment.message.ifBlank { "Break" },
            fontSize = bigSize * BREAK_NOTE_SCALE,
            fontWeight = FontWeight.Bold,
            color = PokerColors.CardWhite,
            textAlign = TextAlign.Center
        )
        if (segment.colorUp.isNotEmpty()) ColorUpBanner(colorUpText(segment.colorUp, formatter))
        return
    }
    val levelSegment = segment as LevelSegment
    val level = levelSegment.level
    Text(text = "BLINDS", style = Caption, color = Dim)
    Text(
        text = blindsText(level, formatter),
        fontSize = bigSize,
        lineHeight = bigSize * LINE_HEIGHT_SCALE,
        fontWeight = FontWeight.Bold,
        color = if (levelSegment.isOvertime) PokerColors.ErrorRed else PokerColors.CardWhite
    )
    if (level.ante > 0) {
        Text(
            text = "BB ante ${formatter.format(level.ante)}",
            fontSize = bigSize * ANTE_SCALE,
            fontWeight = FontWeight.SemiBold,
            color = PokerColors.PokerGold
        )
    }
    if (levelSegment.colorUp.isNotEmpty()) {
        ColorUpBanner(colorUpText(levelSegment.colorUp, formatter) + ": no longer needed")
    }
}

@Composable
private fun ColorUpBanner(text: String) {
    Text(
        text = "● $text",
        color = PokerColors.PokerGold,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 6.dp)
            .border(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** "Next: 300 / 600", dimmer and labelled so it can't be mistaken for the current level. */
@Composable
internal fun NextLevelLine(uiState: TimerUiState, formatter: NumberFormat, fontSize: TextUnit) {
    Text(
        text = nextText(uiState, formatter),
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        color = Dim,
        textAlign = TextAlign.Center
    )
}

/** Previous level, play/pause, next level. The play button sits apart from the digits (PP-046). */
@Composable
internal fun ClockControls(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit, playSize: Dp) {
    val started = uiState.hasTimerStarted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        SkipButton(
            enabled = started && uiState.canGoBack,
            description = "Previous blind level",
            forward = false,
            onClick = { onIntent(TimerIntent.PreviousBlindLevel) }
        )
        PlayPauseButton(uiState, playSize) { onIntent(TimerIntent.ToggleTimer) }
        SkipButton(
            enabled = started && uiState.canGoForward,
            description = "Next blind level",
            forward = true,
            onClick = { onIntent(TimerIntent.NextBlindLevel) }
        )
    }
}

@Composable
private fun SkipButton(enabled: Boolean, description: String, forward: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = if (forward) Icons.Default.SkipNext else Icons.Default.SkipPrevious,
            contentDescription = description,
            tint = PokerColors.PokerGold.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun PlayPauseButton(uiState: TimerUiState, size: Dp, onClick: () -> Unit) {
    val description = when {
        !uiState.hasTimerStarted -> "Start timer"
        uiState.isRunning -> "Pause timer"
        else -> "Resume timer"
    }
    Surface(
        onClick = onClick,
        enabled = !uiState.isFinished,
        shape = CircleShape,
        color = if (uiState.isFinished) PokerColors.CardWhite.copy(alpha = 0.15f) else PokerColors.PokerGold,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (uiState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = PokerColors.FeltGreen,
                modifier = Modifier.size(size * ICON_IN_BUTTON)
            )
        }
    }
}

/** Total tournament time, demoted to a small labelled line. */
@Composable
internal fun TournamentLine(uiState: TimerUiState, fontSize: TextUnit = 14.sp) {
    val remaining = uiState.tournamentRemainingSeconds
    val (label, value, color) = when {
        uiState.isFinished -> Triple("Tournament", "time's up", Dim)
        remaining > 0 -> Triple("Tournament", "${ClockFormat.long(remaining)} left", Dim)
        else -> Triple("Overtime", "+${ClockFormat.long(-remaining)}", PokerColors.ErrorRed)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label:", fontSize = fontSize, color = Faint)
        Text(text = value, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Players left, average stack and prize pool, from the Tournament and Bank settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TableStatsLine(uiState: TimerUiState, formatter: NumberFormat, fontSize: TextUnit = 14.sp) {
    val table = uiState.table
    if (table.playerCount <= 0) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
        Stat("Players", "${table.playersLeft}/${table.playerCount}", fontSize)
        if (table.averageStack > 0) Stat("Avg stack", formatter.format(table.averageStack), fontSize)
        if (table.prizePool > 0) Stat("Pool", FormatUtils.formatCurrency(table.prizePool), fontSize)
    }
}

@Composable
private fun Stat(label: String, value: String, fontSize: TextUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = fontSize, color = Faint)
        Text(text = value, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = PokerColors.CardWhite)
    }
}
