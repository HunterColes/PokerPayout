package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ShotClockUiState
import com.huntercoles.pokerpayout.tools.shotclock.ShotClockPhase
import kotlinx.coroutines.delay

/**
 * The shot clock's face: one round button as big as the screen allows. The seconds left in the
 * middle, a gold ring that empties as time runs, both turning Danger for the last ten seconds and
 * "TIME" when it's up. Any tap starts the next decision. The digits are part of the graphic and
 * keep their size whatever the font scale; TalkBack reads the face as one button
 * ("Shot clock, 27 seconds left").
 */
@Composable
internal fun ShotClockFace(state: ShotClockUiState, size: Dp, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val ring by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(if (reduced) 0 else RING_MILLIS),
        label = "shot clock ring",
    )
    val timeUp = state.phase == ShotClockPhase.TimeUp
    val ink = if (state.lowOnTime || timeUp) PokerColors.Danger else PokerColors.PokerGold
    val description = faceDescription(state)
    val action = stringResource(
        if (state.phase == ShotClockPhase.Ready) R.string.shot_clock_face_action_start else R.string.shot_clock_face_action_next,
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(PokerColors.FeltGreen)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onTap)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * RING_SHARE
            val inset = stroke / 2 + this.size.minDimension * RING_MARGIN
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(PokerColors.FeltDeep, 0f, FULL_TURN, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                color = ink,
                startAngle = TOP,
                sweepAngle = FULL_TURN * ring,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        FaceFlash(state.flashes)
        Text(
            text = if (timeUp) stringResource(R.string.shot_clock_time_word) else state.shownSeconds.toString(),
            color = if (state.phase == ShotClockPhase.Paused) ink.copy(alpha = PokerColors.PokerPausedAlpha) else ink,
            style = PokerType.Clock.copy(
                fontSize = (size * if (timeUp) WORD_SHARE else DIGITS_SHARE).fixedSp(),
                lineHeight = (size * if (timeUp) WORD_SHARE else DIGITS_SHARE).fixedSp(),
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** "Shot clock, 27 seconds left", "..., paused", or "Shot clock, time is up". */
@Composable
private fun faceDescription(state: ShotClockUiState): String {
    val left = pluralStringResource(R.plurals.shot_clock_face_description, state.shownSeconds, state.shownSeconds)
    return when (state.phase) {
        ShotClockPhase.TimeUp -> stringResource(R.string.shot_clock_face_time_up)
        ShotClockPhase.Paused -> stringResource(R.string.shot_clock_face_paused, left)
        else -> left
    }
}

/**
 * The line under the face: what a tap does, or where the decision stands. TalkBack reads it when it
 * changes, which is only at a start, a pause, ten seconds left and time up, never every second.
 */
@Composable
internal fun ShotClockStatus(state: ShotClockUiState, modifier: Modifier = Modifier) {
    val text = stringResource(
        when {
            state.phase == ShotClockPhase.Ready -> R.string.shot_clock_status_ready
            state.phase == ShotClockPhase.TimeUp -> R.string.shot_clock_status_time_up
            state.phase == ShotClockPhase.Paused -> R.string.shot_clock_status_paused
            state.lowOnTime -> R.string.shot_clock_status_low
            else -> R.string.shot_clock_status_running
        },
    )
    val alarm = state.phase == ShotClockPhase.TimeUp || state.lowOnTime
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (alarm) PokerColors.Danger else PokerColors.Chalk,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * A gold flash over the face for a warning, when Flash the clock is on in Tools ([flashes] grows by
 * one each time). Under Reduce motion it doesn't blink: a steady gold ring shows for a moment instead.
 */
@Composable
private fun FaceFlash(flashes: Int) {
    val reduced = LocalReducedMotion.current
    val glow = remember { Animatable(0f) }
    var framed by remember { mutableStateOf(false) }
    // A flash already shown (before the screen turned, say) isn't shown again
    var shown by rememberSaveable { mutableIntStateOf(flashes) }
    LaunchedEffect(flashes) {
        if (flashes <= shown) return@LaunchedEffect
        shown = flashes
        if (reduced) {
            framed = true
            delay(STEADY_FRAME_MILLIS)
            framed = false
        } else {
            glow.animateTo(PEAK_ALPHA, tween(RISE_MILLIS))
            glow.animateTo(0f, tween(FALL_MILLIS))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (framed) Modifier.border(FRAME_WIDTH, PokerColors.PokerGold, CircleShape) else Modifier)
            .background(PokerColors.PokerGold.copy(alpha = glow.value), CircleShape),
    )
}

private const val RING_MILLIS = 250
private const val FULL_TURN = 360f
private const val TOP = -90f

/** The ring's width, and its gap from the edge, as shares of the face. */
private const val RING_SHARE = 0.06f
private const val RING_MARGIN = 0.03f

/** The digits' size, and the word "TIME"'s, as shares of the face. */
private const val DIGITS_SHARE = 0.46f
private const val WORD_SHARE = 0.3f

private const val PEAK_ALPHA = 0.35f
private const val RISE_MILLIS = 150
private const val FALL_MILLIS = 450
private const val STEADY_FRAME_MILLIS = 1_500L
private val FRAME_WIDTH = 6.dp
