package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors

/**
 * How far through the current level (or break) the clock is: an 8 dp bar on a FeltDeep track,
 * always paired with words ("7:19 played" under it at the start, "20-min level" at the end).
 * [progress] runs 0 to 1. The colour crossfades between tones in 300 ms; in [LevelTone.Critical] the
 * bar pulses its alpha between 1.0 and 0.7 once a second, unless Reduce motion is on.
 */
@Composable
fun LevelProgress(
    progress: Float,
    tone: LevelTone,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val color by animateColorAsState(
        targetValue = tone.color,
        animationSpec = if (reduced) snap() else tween(TONE_MILLIS),
        label = "levelTone",
    )
    val alpha = if (tone == LevelTone.Critical && !reduced) pulseAlpha() else 1f
    val share = progress.coerceIn(0f, 1f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .graphicsLayer { this.alpha = alpha }
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f) },
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(PokerColors.FeltDeep, cornerRadius = radius)
            if (share > 0f) {
                drawRoundRect(color, size = Size(size.width * share, size.height), cornerRadius = radius)
            }
        }
        if (startLabel != null || endLabel != null) {
            // Each label has half the row (or all of it alone), so a long one wraps instead of
            // squeezing the other out.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (startLabel != null) {
                    Text(
                        text = startLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PokerColors.Chalk,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (endLabel != null) {
                    Text(
                        text = endLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PokerColors.Chalk,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun pulseAlpha(): Float {
    val pulse = rememberInfiniteTransition(label = "finalMinutesPulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_LOW_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_HALF_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "finalMinutesAlpha",
    )
    return alpha
}

private val BarHeight = 8.dp
private const val TONE_MILLIS = 300

/** One full pulse a second: out and back. Nothing on screen flashes faster than 1 Hz. */
private const val PULSE_HALF_MILLIS = 500
private const val PULSE_LOW_ALPHA = 0.7f

@Preview(name = "LevelProgress", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun LevelProgressPreview() {
    PokerPreviewPage {
        PokerStage {
            LevelProgress(progress = 0.366f, tone = LevelTone.Normal, startLabel = "7:19 played", endLabel = "20-min level")
            LevelProgress(progress = 0.8f, tone = LevelTone.Low, startLabel = "16:00 played", endLabel = "20-min level")
            LevelProgress(progress = 0.93f, tone = LevelTone.Critical, startLabel = "18:36 played", endLabel = "Final minutes")
            LevelProgress(progress = 0.247f, tone = LevelTone.Low, endLabel = "Add-ons close when the break ends")
        }
    }
}
