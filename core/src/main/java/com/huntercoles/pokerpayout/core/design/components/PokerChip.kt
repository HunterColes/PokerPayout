package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType

/**
 * A poker chip, drawn: the denomination's colour (from [ChipDenominations], the physical chips),
 * eight edge spots, an inner ring and the value in Barlow. A hairline ring keeps the black chip
 * visible on black. [selected] adds a gold ring outside the chip, so leave 4 dp around it.
 *
 * The value is drawn at a fixed size, like the chip. TalkBack reads "Green 25 chip"; pass
 * [contentDescription] to add more ("Green 25 chip, 150 owned").
 *
 * [color] draws the chip in another colour than [denomination]'s standard one, for sets whose
 * colours mean other values (the chip set, PP-033: white 25s). Pass [contentDescription] with it.
 */
@Suppress("LongParameterList") // a component API: one parameter per visual option
@Composable
fun PokerChip(
    denomination: Int,
    modifier: Modifier = Modifier,
    size: Dp = PokerDimens.PokerChipMedium,
    selected: Boolean = false,
    contentDescription: String? = null,
    color: Color? = null,
) {
    val chip = ChipDenominations.getChipByValue(denomination)
    val style = chipStyle(color ?: chip?.color ?: UnknownChip)
    val description = contentDescription
        ?: stringResource(R.string.design_chip, chip?.name ?: "", chipLabel(denomination)).trim()
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawChip(style, selected) }
        val textSize = with(LocalDensity.current) { (size * VALUE_SCALE).toSp() }
        Text(
            text = chipLabel(denomination),
            color = style.ink,
            style = PokerType.CardRank.copy(fontSize = textSize, lineHeight = textSize, letterSpacing = 0.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 1 to 999 as is, then "1K", "2.5K", "1M". */
internal fun chipLabel(value: Int): String = when {
    value >= MILLION && value % MILLION == 0 -> "${value / MILLION}M"
    value >= THOUSAND && value % THOUSAND == 0 -> "${value / THOUSAND}K"
    value >= THOUSAND && value % HALF_THOUSAND == 0 -> "${value / THOUSAND}.5K"
    else -> value.toString()
}

private class ChipStyle(val base: Color, val spots: Color, val ink: Color)

/** White and yellow chips get dark ink, and the white chip blue spots so its edge shows. */
private fun chipStyle(base: Color): ChipStyle = when (base) {
    Color.White -> ChipStyle(base, WhiteChipSpots, PokerColors.SuitBlack)
    in LightChips -> ChipStyle(base, SpotWhite, PokerColors.SuitBlack)
    else -> ChipStyle(base, SpotWhite, Color.White)
}

/** Chips too light for white numbers. */
private val LightChips = setOf(
    ChipDenominations.YELLOW.color,
    ChipDenominations.LIGHT_BLUE.color,
    ChipDenominations.ORANGE.color,
    ChipDenominations.PINK.color,
)

private fun DrawScope.drawChip(style: ChipStyle, selected: Boolean) {
    val radius = size.minDimension / 2
    if (selected) {
        drawCircle(PokerColors.PokerBlack, radius = radius + 2.dp.toPx())
        drawCircle(PokerColors.PokerGold, radius = radius + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
    drawCircle(style.base, radius)
    repeat(SPOTS) { index ->
        drawArc(
            color = style.spots,
            startAngle = SPOT_START + index * (360f / SPOTS),
            sweepAngle = SPOT_SWEEP,
            useCenter = true,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
        )
    }
    val inner = radius * INNER_RADIUS
    drawCircle(style.base, inner)
    drawCircle(Color.White.copy(alpha = INNER_RING_ALPHA), inner, style = Stroke(1.5.dp.toPx()))
    val hairline = 1.dp.toPx()
    drawCircle(Color.White.copy(alpha = HAIRLINE_ALPHA), radius - hairline / 2, style = Stroke(hairline))
}

private val UnknownChip = Color(0xFF808080)
private val SpotWhite = Color(0xFFEDEDED)
private val WhiteChipSpots = Color(0xFF2E7D9A)
private const val SPOTS = 8
private const val SPOT_START = 8f
private const val SPOT_SWEEP = 15f
private const val INNER_RADIUS = 0.68f
private const val INNER_RING_ALPHA = 0.6f
private const val HAIRLINE_ALPHA = 0.25f
private const val VALUE_SCALE = 0.34f
private const val THOUSAND = 1_000
private const val HALF_THOUSAND = 500
private const val MILLION = 1_000_000

private val PreviewChips = listOf(1, 5, 25, 100, 500, 1000)
private val PreviewSmallChips = listOf(10, 20, 50, 250, 2000, 5000)

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "PokerChip", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerChipPreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PreviewChips.forEach { PokerChip(it) }
                PokerChip(denomination = 25, size = 48.dp, selected = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewSmallChips.forEach { PokerChip(it, size = PokerDimens.PokerChipSmall) }
                PokerChip(denomination = 100, size = PokerDimens.PokerChipLarge)
            }
        }
        PokerStage {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PokerChip(denomination = 100)
                PokerChip(denomination = 1000, selected = true)
            }
        }
    }
}
