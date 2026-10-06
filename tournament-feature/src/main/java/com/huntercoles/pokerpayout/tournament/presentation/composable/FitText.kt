package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * The font size at which [text] in [style] just fits [width], at most [cap] and never below [floor].
 * Sizes are in dp, so the result doesn't grow with the system font size: the clock's digits and the
 * big blinds fit their space at 200% as at 100% (design spec §6.4); the words around them scale.
 *
 * Every digit is measured as "0": with tabular figures each is as wide, so the size holds steady as
 * the clock ticks instead of jumping on every second. For "12:41" in Barlow Condensed SemiBold the
 * width comes to about 2.2 em (the system font needed 3.1, the table view's old HERO_WIDTH_EMS).
 */
@Suppress("LongParameterList") // the text, its style, the box it must fit and the size limits
@Composable
internal fun rememberFittedSize(
    text: String,
    style: TextStyle,
    width: Dp,
    cap: Dp,
    floor: Dp = MinFitted,
    height: Dp = Dp.Unspecified,
    lineHeightRatio: Float = 1f,
): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val template = text.map { if (it.isDigit()) '0' else it }.joinToString("")
    return remember(template, style, width, cap, floor, height, lineHeightRatio, density) {
        with(density) {
            val reference = ReferenceSize.toSp()
            val measured = measurer.measure(
                text = template,
                style = style.copy(fontSize = reference, lineHeight = reference * lineHeightRatio),
                softWrap = false,
                maxLines = 1,
            ).size
            val widthFit = if (measured.width > 0) ReferenceSize * (width * FIT_SLACK / measured.width.toDp()) else cap
            // With a height as well, the text's laid-out height (its line, trimmed as the caller trims it) fits it.
            val heightFit = if (height.isSpecified && measured.height > 0) {
                ReferenceSize * (height / measured.height.toDp())
            } else {
                cap
            }
            minOf(cap, widthFit, heightFit).coerceAtLeast(minOf(floor, cap)).toSp()
        }
    }
}

private val ReferenceSize = 100.dp
private val MinFitted = 12.dp

/** A hair under the width: hinting and rounding make big text a pixel or two wider than scaled. */
private const val FIT_SLACK = 0.97f
