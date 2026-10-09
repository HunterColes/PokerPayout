package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/**
 * One choice out of a few, side by side (payout presets, rounding, the Bank mode). Replaces
 * FilterChip rows and the Player/Blinds/Payouts folder tabs.
 *
 * The selected segment gets a FeltHigh fill, a DarkGold outline and gold text. A segment may carry
 * a [secondary] preview value under its label (the 1st-place prize, "$225"). Each segment is a
 * radio button for TalkBack, at least 48 dp tall; labels wrap rather than truncate. While not
 * [enabled] (a structure locked while the clock runs) the selection still shows, dimmed, and taps do
 * nothing.
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun <T> PokerSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    secondary: ((T) -> String?)? = null,
    enabled: Boolean = true,
) {
    val trackShape = RoundedCornerShape(PokerDimens.CornerControl)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // At large font sizes the labels shrink (all together) until no word has to break.
        val textWidth = (maxWidth - TrackPadding * 2) / options.size.coerceAtLeast(1) - SegmentInset * 2
        val labelStyle = rememberFittedStyle(SegmentLabel, wordsOf(options.map(label)), textWidth, SegmentLabelMin)
        val secondaries = secondary?.let { options.mapNotNull(it) }.orEmpty()
        val secondaryStyle = rememberFittedStyle(PokerType.NumberS, secondaries, textWidth, SegmentLabelMin)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(trackShape)
                .background(PokerColors.FeltDeep)
                .padding(horizontal = TrackPadding)
                .selectableGroup(),
        ) {
            options.forEach { option ->
                Segment(
                    label = label(option),
                    secondary = secondary?.invoke(option),
                    selected = option == selected,
                    styles = SegmentStyles(labelStyle, secondaryStyle, tall = secondary != null),
                    enabled = enabled,
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

private class SegmentStyles(val label: TextStyle, val secondary: TextStyle, val tall: Boolean)

@Suppress("LongParameterList")
@Composable
private fun Segment(
    label: String,
    secondary: String?,
    selected: Boolean,
    styles: SegmentStyles,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val thumbShape = RoundedCornerShape(9.dp)
    val color = when {
        !enabled -> PokerColors.ChalkDim
        selected -> PokerColors.PokerGold
        else -> PokerColors.Chalk
    }
    // The segment (with its share of the track's padding) takes the tap; the thumb is drawn inside.
    Box(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = if (styles.tall) 58.dp else PokerDimens.ControlHeight)
            .padding(horizontal = TrackPadding, vertical = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(thumbShape)
                .background(if (selected) PokerColors.FeltHigh else Color.Transparent)
                .then(if (selected) Modifier.border(1.dp, PokerColors.DarkGold, thumbShape) else Modifier)
                .indication(interactions, ripple(color = color))
                .padding(horizontal = ThumbPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, color = color, style = styles.label, textAlign = TextAlign.Center)
            if (secondary != null) {
                Text(
                    // Full strength: gold at 90% on the selected thumb was 4.49:1, under AA
                    text = secondary,
                    color = color,
                    style = styles.secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val SegmentLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 16.sp)
private val SegmentLabelMin = 14.dp
private val TrackPadding = 1.5.dp
private val ThumbPadding = 4.dp

/** Each segment's padding plus its thumb's: the room a label loses inside its share of the track. */
private val SegmentInset = TrackPadding + ThumbPadding

private enum class PreviewPreset(val label: String, val firstPlaceCents: Long) {
    TopHeavy("Top-heavy", 27_000L),
    Standard("Standard", 22_500L),
    Flat("Flat", 20_000L),
}

@Preview(name = "PokerSegmentedControl", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerSegmentedControlPreview() {
    PokerPreviewPage {
        PokerStage {
            PokerSegmentedControl(
                options = PreviewPreset.entries,
                selected = PreviewPreset.Standard,
                onSelect = {},
                label = { it.label },
                secondary = { FormatUtils.formatMoney(it.firstPlaceCents) },
            )
            PokerSegmentedControl(
                options = listOf(100L, 500L, 1_000L).map(FormatUtils::formatMoney),
                selected = FormatUtils.formatMoney(500L),
                onSelect = {},
                label = { it },
            )
            PokerSegmentedControl(
                options = listOf("Tournament", "Cash game"),
                selected = "Tournament",
                onSelect = {},
                label = { it },
            )
        }
    }
}
