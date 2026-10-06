package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSegment
import com.huntercoles.pokerpayout.tournament.domain.clock.LevelSegment
import com.huntercoles.pokerpayout.tournament.presentation.ClockFormat
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import java.text.NumberFormat

private enum class RowState { PAST, CURRENT, UPCOMING }

private val SCHEDULE_ROW_HEIGHT = 58.dp
private const val SCHEDULE_ROWS = 4
private const val SCHEDULE_ROWS_WITH_CONFIG = 2
private const val RESIZE_MILLIS = 150

/**
 * The blind schedule with its breaks (PP-026), shown before the start too. The current level or break
 * is highlighted and kept in view; overtime levels appear as the clock reaches them.
 */
@Composable
internal fun ScheduleCard(uiState: TimerUiState, isConfigExpanded: Boolean) {
    val formatter = rememberChipFormatter()
    val segments = uiState.timeline.visibleSegmentsAt(uiState.elapsedSeconds)
    if (segments.isEmpty()) return
    val currentIndex = uiState.currentSegmentIndex
    val listState = rememberLazyListState()
    val rows = if (isConfigExpanded) SCHEDULE_ROWS_WITH_CONFIG else SCHEDULE_ROWS
    val height by animateDpAsState(
        targetValue = PokerDimens.BlindPanelCardPadding * 2 + SCHEDULE_ROW_HEIGHT * rows +
            PokerDimens.BlindItemSpacing * (rows - 1),
        animationSpec = tween(durationMillis = RESIZE_MILLIS, easing = FastOutSlowInEasing),
        label = "schedulePanelHeight"
    )

    LaunchedEffect(currentIndex, isConfigExpanded, segments.size) {
        if (currentIndex in segments.indices) listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        elevation = CardDefaults.cardElevation(defaultElevation = PokerDimens.ElevationDefault),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(PokerDimens.BlindPanelCardPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(PokerDimens.BlindItemSpacing)
        ) {
            itemsIndexed(segments, key = { _, segment -> segment.startSeconds }) { index, segment ->
                val state = when {
                    index == currentIndex && uiState.hasTimerStarted -> RowState.CURRENT
                    index < currentIndex -> RowState.PAST
                    else -> RowState.UPCOMING
                }
                when (segment) {
                    is LevelSegment -> LevelRow(segment, state, formatter)
                    is BreakSegment -> BreakRow(segment, state, formatter)
                }
            }
        }
    }
}

@Composable
private fun LevelRow(segment: LevelSegment, state: RowState, formatter: NumberFormat) {
    val level = segment.level
    val background by animateColorAsState(
        if (state == RowState.CURRENT) PokerColors.PokerGold.copy(alpha = 0.18f) else Color.Transparent,
        label = "levelBackground"
    )
    val primary = when {
        segment.isOvertime -> PokerColors.ErrorRed
        state == RowState.CURRENT -> PokerColors.PokerGold
        state == RowState.PAST -> Faint
        else -> PokerColors.CardWhite
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCHEDULE_ROW_HEIGHT)
            .clip(RoundedCornerShape(PokerDimens.CornerSmall))
            .background(background)
            .padding(horizontal = PokerDimens.BlindItemPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Level ${level.level}" + if (segment.isOvertime) " · overtime" else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = primary
            )
            Text(
                text = blindsText(level, formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            OffsetText(segment.startSeconds)
            val note = when {
                segment.colorUp.isNotEmpty() -> colorUpText(segment.colorUp, formatter)
                level.ante > 0 -> "BB ante ${formatter.format(level.ante)}"
                else -> null
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (segment.colorUp.isEmpty() && state != RowState.CURRENT) Dim else PokerColors.PokerGold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BreakRow(segment: BreakSegment, state: RowState, formatter: NumberFormat) {
    val accent = if (state == RowState.PAST) Faint else PokerColors.PokerGold
    val shape = RoundedCornerShape(PokerDimens.CornerSmall)
    val background = if (state == RowState.CURRENT) {
        PokerColors.PokerGold.copy(alpha = 0.18f)
    } else {
        PokerColors.FeltGreen.copy(alpha = 0.5f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCHEDULE_ROW_HEIGHT)
            .clip(shape)
            .background(background)
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .padding(horizontal = PokerDimens.BlindItemPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "☕ Break · ${segment.durationSeconds / SECONDS_PER_MINUTE} min",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            val details = listOfNotNull(
                segment.message.takeIf { it.isNotBlank() },
                segment.colorUp.takeIf { it.isNotEmpty() }?.let { colorUpText(it, formatter) }
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.CardWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        OffsetText(segment.startSeconds)
    }
}

/** "+1:20": when the level or break starts, counting breaks. */
@Composable
private fun OffsetText(startSeconds: Int) {
    Text(text = ClockFormat.offset(startSeconds), style = MaterialTheme.typography.labelSmall, color = Dim)
}
