package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LevelProgress
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.ClockButtons
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

/**
 * The hero (PP-025): what the level is, then its time left as the biggest thing on the screen. The
 * digits fit [width] (at most [cap]) and don't grow with the system font size. Tapping them starts or
 * pauses the clock ([onIntent] null: a backdrop that can't be tapped). The eyebrow is a polite live
 * region, so TalkBack says when the level changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClockHero(uiState: TimerUiState, width: Dp, cap: Dp, onIntent: ((TimerIntent) -> Unit)?) {
    val time = clockText(uiState.segmentRemainingSeconds)
    val size = rememberFittedSize(time, PokerType.Clock, width, cap)
    val toggle = stringResource(if (uiState.isRunning) R.string.clock_pause else R.string.clock_resume)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PokerEyebrow(
                text = clockEyebrow(uiState),
                color = eyebrowColor(uiState),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            clockPills(uiState).forEach { PokerPill(it.text, tone = it.tone) }
        }
        Text(
            text = time,
            style = PokerType.Clock.copy(fontSize = size, lineHeight = size * HERO_LINE_HEIGHT),
            color = heroColor(uiState),
            maxLines = 1,
            softWrap = false,
            modifier = if (onIntent == null) {
                Modifier
            } else {
                Modifier.clickable(
                    enabled = uiState.hasTimerStarted && !uiState.isFinished,
                    onClickLabel = toggle,
                    role = Role.Button,
                ) { onIntent(TimerIntent.ToggleTimer) }
            },
        )
    }
}

/** The level bar with "7:19 played" and "20-min level" under it. */
@Composable
internal fun ClockProgress(uiState: TimerUiState, modifier: Modifier = Modifier, labelled: Boolean = true) {
    val minutes = (uiState.currentSegment?.durationSeconds ?: 0) / SECONDS_PER_MINUTE
    LevelProgress(
        progress = uiState.segmentProgress,
        tone = levelTone(uiState),
        modifier = modifier.fillMaxWidth(),
        startLabel = if (labelled) stringResource(R.string.clock_played, clockText(uiState.segmentPlayedSeconds)) else null,
        endLabel = if (labelled && !uiState.isOnBreak) stringResource(R.string.clock_level_length, minutes) else null,
    )
}

/**
 * Previous level, minus a minute, play/pause, plus a minute, next level (PP-046: play/pause has its
 * own big button, never on the digits; D5: the ±1 nudges). [compact] is for small phones: 48 dp.
 * It takes [ClockButtons], not the clock's whole state, so it skips the clock's ticks.
 */
@Composable
internal fun ClockControls(buttons: ClockButtons, onIntent: (TimerIntent) -> Unit, compact: Boolean = false) {
    val started = buttons.started
    val live = started && !buttons.finished
    val small = if (compact) CompactControl else ControlSize
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = started && buttons.canGoBack
        val forward = started && buttons.canGoForward
        RoundControl(
            stringResource(R.string.clock_previous),
            back,
            small,
            onClick = { onIntent(TimerIntent.PreviousBlindLevel) },
        ) {
            // The Next glyph mirrored: PokerIcons.Previous draws its triangle the wrong way round.
            ControlIcon(PokerIcons.Next, back, Modifier.graphicsLayer { scaleX = -1f })
        }
        RoundControl(
            stringResource(R.string.clock_minus_minute),
            live,
            small,
            onClick = { onIntent(TimerIntent.NudgeMinutes(-1)) },
        ) {
            ControlLabel(stringResource(R.string.clock_minus_one), live)
        }
        PlayPauseButton(buttons, if (compact) CompactPlay else PlayButtonSize) { onIntent(TimerIntent.ToggleTimer) }
        RoundControl(
            stringResource(R.string.clock_plus_minute),
            live,
            small,
            onClick = { onIntent(TimerIntent.NudgeMinutes(1)) },
        ) {
            ControlLabel(stringResource(R.string.clock_plus_one), live)
        }
        RoundControl(stringResource(R.string.clock_next), forward, small, onClick = { onIntent(TimerIntent.NextBlindLevel) }) {
            ControlIcon(PokerIcons.Next, forward)
        }
    }
}

@Suppress("LongParameterList") // a round button: name, state, size, action, glyph
@Composable
private fun RoundControl(
    description: String,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) PokerColors.DarkGreen else PokerColors.FeltDeep)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ControlIcon(icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) PokerColors.CardWhite else PokerColors.ChalkDim,
        modifier = modifier.size(24.dp),
    )
}

@Composable
private fun ControlLabel(text: String, enabled: Boolean) {
    Text(
        text = text,
        style = PokerType.NumberS.copy(fontSize = PokerType.NumberM.fontSize),
        color = if (enabled) PokerColors.CardWhite else PokerColors.ChalkDim,
        textAlign = TextAlign.Center,
    )
}

/** The gold play/pause button: "Start timer", "Pause timer" or "Resume timer" for TalkBack. */
@Composable
internal fun PlayPauseButton(buttons: ClockButtons, size: Dp, onClick: () -> Unit) {
    val description = stringResource(
        when {
            !buttons.started -> R.string.clock_start
            buttons.running -> R.string.clock_pause
            else -> R.string.clock_resume
        },
    )
    val enabled = !buttons.finished
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) PokerColors.PokerGold else PokerColors.FeltDeep)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (buttons.running) PokerIcons.Pause else PokerIcons.Play,
            contentDescription = null,
            tint = if (enabled) PokerColors.FeltDeep else PokerColors.ChalkDim,
            modifier = Modifier.size(size * ICON_SHARE),
        )
    }
}

/** "300 / 600", fitted to [width] at most [cap] (the big blinds are numbers to read across a table). */
@Composable
internal fun FittedNumber(text: String, width: Dp, cap: Dp, color: Color, modifier: Modifier = Modifier) {
    val size = rememberFittedSize(text, PokerType.DisplayL, width, cap)
    Text(
        text = text,
        style = PokerType.DisplayL.copy(fontSize = size, lineHeight = size),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** Fits [content] to the width it gets, for numbers that must stay on one line. */
@Composable
internal fun WithWidth(modifier: Modifier = Modifier, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(modifier) { content(maxWidth) }
}

internal val ControlSize = 52.dp
private val CompactControl = 48.dp
private val PlayButtonSize = 76.dp
private val CompactPlay = 64.dp
private const val ICON_SHARE = 0.45f
internal const val HERO_LINE_HEIGHT = 0.93f
