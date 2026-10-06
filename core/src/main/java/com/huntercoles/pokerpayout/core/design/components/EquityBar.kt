package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * A seat's equity: solid gold for wins, hatched gold for its share of ties, on a FeltDeep track.
 * While the odds are still a Monte Carlo [estimate], the win share is dashed. Ties are hatched, so
 * the bar reads without colour. 10 dp tall, or 18 dp when [large] (Run it out).
 *
 * TalkBack reads "Win 53.0%, tie 1.4%" (or "Win about ..., still estimating").
 *
 * @param win the share of runouts this seat wins outright, 0 to 1.
 * @param tie this seat's share of split pots, 0 to 1.
 */
@Composable
fun EquityBar(
    win: Float,
    tie: Float,
    modifier: Modifier = Modifier,
    estimate: Boolean = false,
    large: Boolean = false,
) {
    val winShare = win.coerceIn(0f, 1f)
    val tieShare = tie.coerceIn(0f, 1f - winShare)
    val description = stringResource(
        if (estimate) R.string.design_equity_estimate else R.string.design_equity,
        formatPercent(winShare),
        formatPercent(tieShare),
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (large) 18.dp else 10.dp)
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(winShare + tieShare, 0f..1f)
            },
    ) {
        drawEquity(winShare, tieShare, estimate)
    }
}

/** "53.0%": one decimal, US symbols, like every other number the app shows. */
internal fun formatPercent(share: Float): String =
    DecimalFormat("0.0", DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }
        .format(share * PERCENT) + "%"

private fun DrawScope.drawEquity(win: Float, tie: Float, estimate: Boolean) {
    val radius = CornerRadius(size.height / 2)
    val track = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), radius)) }
    clipPath(track) {
        drawRect(PokerColors.FeltDeep)
        val winWidth = size.width * win
        if (estimate) drawDashedWin(winWidth) else drawRect(PokerColors.PokerGold, size = Size(winWidth, size.height))
        val tieWidth = size.width * tie
        if (tieWidth > 0f) drawHatch(winWidth, tieWidth)
    }
}

/** While estimating: 6 dp of gold, then 2 dp of gold at 65%, repeated. */
private fun DrawScope.drawDashedWin(width: Float) {
    val dash = 6.dp.toPx()
    val gap = 2.dp.toPx()
    clipRect(right = width) {
        var x = 0f
        while (x < width) {
            drawRect(PokerColors.PokerGold, topLeft = Offset(x, 0f), size = Size(dash, size.height))
            drawRect(PokerColors.PokerGold.copy(alpha = GAP_ALPHA), topLeft = Offset(x + dash, 0f), size = Size(gap, size.height))
            x += dash + gap
        }
    }
}

/** Ties: 2 dp gold stripes every 5 dp at 135 degrees. */
private fun DrawScope.drawHatch(start: Float, width: Float) {
    val stripe = 2.dp.toPx()
    val pitch = 5.dp.toPx()
    clipRect(left = start, right = start + width) {
        var x = start - size.height
        while (x < start + width) {
            drawLine(PokerColors.PokerGold, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = stripe)
            x += pitch
        }
    }
}

private const val PERCENT = 100
private const val GAP_ALPHA = 0.65f

@Preview(name = "EquityBar", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun EquityBarPreview() {
    PokerPreviewPage {
        PokerStage {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Win 53.0 · tie 1.4", style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
                Text("53.7%", style = PokerType.NumberM.copy(fontSize = 22.sp), color = PokerColors.CardWhite)
            }
            EquityBar(win = 0.53f, tie = 0.014f, large = true)
            EquityBar(win = 0.671f, tie = 0f, estimate = true)
            EquityBar(win = 0.25f, tie = 0.2f)
            Text(
                "Solid = win. Hatched = share of ties. Dashed = estimate still refining.",
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
    }
}
