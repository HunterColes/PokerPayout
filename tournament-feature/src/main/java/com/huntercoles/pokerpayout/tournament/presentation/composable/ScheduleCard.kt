package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import java.text.NumberFormat

/**
 * The blind schedule with its breaks (PP-026). Levels already played fold into one row ("1–5
 * 25/50 → 200/400 · Break 1  done"); the current level or break has a GoldWash row and a NOW pill;
 * the rest say when they start ("in 12:41"). Overtime levels appear as the clock reaches them.
 */
@Composable
internal fun ScheduleCard(uiState: TimerUiState, modifier: Modifier = Modifier) {
    val segments = uiState.timeline.visibleSegmentsAt(uiState.elapsedSeconds)
    if (segments.isEmpty()) return
    val formatter = rememberChipFormatter()
    val current = if (uiState.isFinished) segments.size else uiState.currentSegmentIndex
    val started = uiState.hasTimerStarted
    val done = if (started) segments.take(current) else emptyList()
    val lastRegular = uiState.timeline.levels.lastOrNull { !it.isOvertime }?.index
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PokerColors.FeltGreen)
            .padding(6.dp),
    ) {
        if (done.isNotEmpty()) DoneRow(done, formatter)
        segments.drop(done.size).forEachIndexed { offset, segment ->
            val now = offset == 0 && !uiState.isFinished
            if (done.isNotEmpty() || offset > 0) {
                HorizontalDivider(color = PokerColors.FeltLine, modifier = Modifier.padding(horizontal = 6.dp))
            }
            ScheduleRow(
                segment = segment,
                now = now,
                trailing = if (now) null else inTime(segment.startSeconds - uiState.elapsedSeconds),
                isLast = segment is LevelSegment && segment.index == lastRegular,
                formatter = formatter,
                pill = if (now) stringResource(if (started) R.string.schedule_now else R.string.clock_pill_ready) else null,
            )
        }
    }
}

/** Everything already played, in one row. */
@Composable
private fun DoneRow(done: List<ClockSegment>, formatter: NumberFormat) {
    val levels = done.filterIsInstance<LevelSegment>()
    val breaks = done.filterIsInstance<BreakSegment>()
    val first = levels.firstOrNull()?.level
    val last = levels.lastOrNull()?.level
    val label = when {
        first == null || last == null -> ""
        first.level == last.level -> stringResource(R.string.schedule_level, first.level)
        else -> stringResource(R.string.schedule_levels_range, first.level, last.level)
    }
    val span = if (first != null && last != null) {
        stringResource(R.string.schedule_done_span, blindsText(first, formatter), blindsText(last, formatter))
    } else {
        ""
    }
    val breakText = when (breaks.size) {
        0 -> null
        1 -> stringResource(R.string.schedule_break_number, breaks.single().number)
        else -> pluralStringResource(R.plurals.setup_breaks, breaks.size, breaks.size)
    }
    RowFrame(background = Color.Transparent) {
        RowLabel(label, PokerColors.ChalkDim)
        Text(
            text = breakText?.let { stringResource(R.string.schedule_done_breaks, span, it) } ?: span,
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.schedule_done), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
}

@Suppress("LongParameterList") // one schedule row: what, when, and how it's marked
@Composable
private fun ScheduleRow(
    segment: ClockSegment,
    now: Boolean,
    trailing: String?,
    isLast: Boolean,
    formatter: NumberFormat,
    pill: String?,
) {
    val main = if (now) PokerColors.PokerGold else PokerColors.CardWhite
    RowFrame(background = if (now) PokerColors.GoldWash else Color.Transparent) {
        when (segment) {
            is LevelSegment -> {
                val labelColor = if (now) PokerColors.PokerGold else PokerColors.Chalk
                RowLabel(stringResource(R.string.schedule_level, segment.level.level), labelColor)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = blindsText(segment.level, formatter),
                        style = PokerType.NumberM,
                        color = if (segment.isOvertime) PokerColors.Danger else main,
                    )
                    levelDetails(segment, isLast, formatter)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
                    }
                }
            }
            is BreakSegment -> {
                Box(Modifier.widthIn(min = LabelWidth)) {
                    Icon(PokerIcons.Coffee, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.schedule_break, segment.durationSeconds / SECONDS_PER_MINUTE),
                        style = PokerType.NumberM.copy(fontSize = PokerType.NumberS.fontSize),
                        color = main,
                    )
                    breakDetails(segment, formatter)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
                    }
                }
            }
        }
        if (pill != null) PokerPill(pill, tone = PokerPillTone.Gold)
        trailing?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk, textAlign = TextAlign.End)
        }
    }
}

/** "ante 600", "ante 2,000 · last level", "Color up the 25s", "overtime". */
@Composable
private fun levelDetails(segment: LevelSegment, isLast: Boolean, formatter: NumberFormat): String? {
    val parts = listOfNotNull(
        segment.level.ante.takeIf { it > 0 }?.let { stringResource(R.string.clock_ante, formatter.format(it)) },
        segment.colorUp.takeIf { it.isNotEmpty() }?.let { colorUpText(it, formatter) },
        stringResource(R.string.schedule_last_level).takeIf { isLast },
        stringResource(R.string.schedule_overtime).takeIf { segment.isOvertime },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.strip_separator))
}

@Composable
private fun RowFrame(background: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clip(RowShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun RowLabel(text: String, color: Color) {
    Text(
        text = text,
        style = PokerType.Eyebrow.copy(fontSize = PokerType.NumberS.fontSize),
        color = color,
        modifier = Modifier.widthIn(min = LabelWidth),
    )
}

private val RowShape = RoundedCornerShape(10.dp)
private val RowHeight = 48.dp
private val LabelWidth = 28.dp
