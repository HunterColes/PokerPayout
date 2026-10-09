package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.mayTruncate
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

/**
 * S1 v2, Start: setup folds into the clock, once.
 *
 * 1. Each section collapses to its one-line summary (250 ms).
 * 2. The summaries stack into the strip at the top (250 ms).
 * 3. The ticket grows into the hero clock; the blinds and the controls come in (300 ms).
 *
 * All `FastOutSlowIn`. Under Reduce motion it cuts straight to the clock. The clock is already
 * running underneath: this is only how the page gets from one shape to the other.
 */
@Composable
internal fun SetupFold(setup: TournamentConfigUiState, timer: TimerUiState, gutter: Dp, onFinished: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(FOLD_MILLIS, easing = LinearEasing))
        finished()
    }
    FoldScene(setup, timer, gutter, progress.value)
}

/** One frame of the fold, at [progress] (0 to 1 of the whole 800 ms). */
@Composable
internal fun FoldScene(setup: TournamentConfigUiState, timer: TimerUiState, gutter: Dp, progress: Float) {
    val collapse = phase(progress, 0f, COLLAPSE_END)
    val stack = phase(progress, COLLAPSE_END, STACK_END)
    val grow = phase(progress, STACK_END, 1f)
    val lines = listOf(
        SetupSummary.strip(setup, timer),
        SetupSummary.moneyLine(setup, timer),
        SetupSummary.blindsLine(timer),
        SetupSummary.payoutsLine(setup),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = gutter, end = gutter, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FoldingLines(lines, collapse, stack, grow)
        FoldingHero(timer, grow)
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = grow
                translationY = (1f - grow) * RISE.toPx()
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlindsCard(timer.blindsUp)
            ClockControls(timer.buttons, onIntent = {})
        }
    }
}

/**
 * The four sections as summary cards: tall section cards collapsing to one line, then sliding up to
 * stack into the strip, the first on top; the others fade behind it as the strip settles.
 */
@Composable
private fun FoldingLines(lines: List<String>, collapse: Float, stack: Float, grow: Float) {
    val rowHeight = lerp(SectionHeight, LineHeight, collapse)
    val spread = rowHeight + RowGap
    val stackedTotal = LineHeight + StackStep * (lines.size - 1)
    val total = lerp(spread * lines.size, stackedTotal, stack)
    Box(Modifier.fillMaxWidth().height(lerp(total, LineHeight, grow))) {
        lines.indices.reversed().forEach { index ->
            val y = lerp(lerp(spread * index, StackStep * index, stack), 0.dp, grow)
            val behind = index > 0
            FoldLine(
                text = lines[index],
                first = index == 0,
                modifier = Modifier
                    .offset(y = y)
                    .height(rowHeight)
                    .graphicsLayer {
                        val shrink = if (behind) STACK_SHRINK * index * stack else 0f
                        scaleX = 1f - shrink
                        alpha = if (behind) (1f - stack * BEHIND_FADE) * (1f - grow) else 1f
                    },
            )
        }
    }
}

@Composable
private fun FoldLine(text: String, first: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, shape)
            .border(1.dp, if (first) PokerColors.FeltEdge else PokerColors.FeltLine, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (first) Icon(PokerIcons.Tune, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (first) PokerColors.CardWhite else PokerColors.Chalk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { mayTruncate = true },
        )
    }
}

/** The ticket's "20:00" growing into the hero, "ready" turning into "time left". */
@Composable
private fun FoldingHero(timer: TimerUiState, grow: Float) {
    val level = timer.currentLevelSegment?.level?.level ?: 1
    val time = clockText(timer.segmentRemainingSeconds)
    WithWidth(Modifier.fillMaxWidth()) { width ->
        val full = rememberFittedSize(time, PokerType.Clock, width, HeroCap)
        val ticket = rememberFittedSize(time, PokerType.Clock, width / 2, TicketHero)
        val size = lerp(ticket, full, grow)
        val align = if (grow > HALF) Alignment.CenterHorizontally else Alignment.Start
        Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
            PokerEyebrow(
                text = stringResource(if (grow > HALF) R.string.clock_level_time_left else R.string.clock_level_ready, level),
                color = PokerColors.PokerGold,
            )
            Text(
                text = time,
                style = PokerType.Clock.copy(fontSize = size, lineHeight = size * HERO_LINE_HEIGHT),
                color = PokerColors.PokerGold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** How far through the stretch [start] to [end] the fold is, eased. */
private fun phase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

/** 250 + 250 + 300 ms. */
internal const val FOLD_MILLIS = 800
private const val COLLAPSE_END = 250f / FOLD_MILLIS
private const val STACK_END = 500f / FOLD_MILLIS
private const val STACK_SHRINK = 0.03f
private const val BEHIND_FADE = 0.4f
private const val HALF = 0.5f
private val SectionHeight = 88.dp
private val LineHeight = 40.dp
private val RowGap = 10.dp
private val StackStep = 6.dp
private val RISE = 16.dp
private val HeroCap = 112.dp
private val TicketHero = 56.dp
