package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * For labels in fixed-width slots (nav tabs, segments): the label style at the user's font size if
 * every one of [pieces] fits [width] on one line, else the largest size that does, in half-sp
 * steps, but never smaller than [floor] (an unscaled size in dp, so it doesn't shrink with the
 * font scale). Pass whole labels to keep them on one line, or their words to let them wrap
 * between words but never inside one. All the slots share the size, so they stay consistent.
 */
@Composable
internal fun rememberFittedStyle(style: TextStyle, pieces: List<String>, width: Dp, floor: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, pieces, width, floor, density) {
        val available = with(density) { width.roundToPx() }
        // Never grow a label, even when the floor is above it (font scale below 1).
        val floorSp = minOf(with(density) { floor.toSp() }.value, style.fontSize.value).sp
        val size = fitSize(style.fontSize, floorSp) { candidate ->
            val trial = style.copy(fontSize = candidate)
            pieces.all { measurer.measure(it, trial, softWrap = false, maxLines = 1).size.width <= available }
        }
        val keepsRatio = style.lineHeight.isSp && style.fontSize.isSp
        val lineHeight = if (keepsRatio) (size.value * style.lineHeight.value / style.fontSize.value).sp else style.lineHeight
        style.copy(fontSize = size, lineHeight = lineHeight)
    }
}

/**
 * The words of the labels. A hyphenated word counts as one: the line breaker doesn't reliably
 * break after a hyphen, and "Top-hea / vy" is worse than a slightly smaller label.
 */
internal fun wordsOf(labels: List<String>): List<String> =
    labels.flatMap { label -> label.split(Whitespace) }.filter { it.isNotBlank() }

private val Whitespace = Regex("\\s+")

private fun fitSize(start: TextUnit, floor: TextUnit, fits: (TextUnit) -> Boolean): TextUnit {
    var size = start.value
    while (size > floor.value && !fits(size.sp)) size -= FIT_STEP_SP
    return maxOf(size, floor.value).sp
}

private const val FIT_STEP_SP = 0.5f
