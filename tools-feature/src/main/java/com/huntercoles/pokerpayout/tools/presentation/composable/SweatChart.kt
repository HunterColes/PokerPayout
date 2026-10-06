package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import com.huntercoles.pokerpayout.tools.presentation.Street
import java.util.Locale

/**
 * "The sweat" (S10): each hand's equity after every street, one line per seat (gold for the first,
 * Chalk for the second), with the numbers at each point heads-up, a dashed 50% line and a dashed
 * marker where the next street will land. TalkBack reads the numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SweatCard(runOut: RunOutState) {
    RunCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PokerEyebrow(
                text = stringResource(R.string.odds_runout_sweat),
                color = PokerColors.PokerGold,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            runOut.contestants.forEachIndexed { i, seat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 14.dp, height = 3.dp).background(lineColour(i), RoundedCornerShape(2.dp)))
                    Text(
                        text = runOut.request.seats[seat].cards.joinToString("", transform = ::cardText),
                        style = MaterialTheme.typography.bodySmall,
                        color = PokerColors.Chalk,
                    )
                }
            }
        }
        SweatChart(runOut)
    }
}

@Composable
private fun SweatChart(runOut: RunOutState) {
    val measurer = rememberTextMeasurer()
    val labels = rememberOddsLabels()
    val res = LocalContext.current.resources
    val streets = stringArrayResource(R.array.odds_chart_streets).map { it.uppercase(Locale.US) }
    val description = runOut.contestants.joinToString(". ") { seat ->
        val points = runOut.history.joinToString(", ") { h ->
            val street = streets[h.street.ordinal].lowercase(Locale.US)
            res.getString(R.string.odds_runout_sweat_point, street, Math.round(h.equityPct[seat]).toString())
        }
        res.getString(R.string.odds_runout_sweat_desc, labels.player(seat), points)
    }
    val styles = ChartStyles(
        number = PokerType.NumberS.copy(fontSize = NUMBER_GLYPH.fixedSp(), lineHeight = NUMBER_GLYPH.fixedSp()),
        label = PokerType.Eyebrow.copy(fontSize = LABEL_GLYPH.fixedSp(), lineHeight = LABEL_GLYPH.fixedSp()),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = (maxWidth * HEIGHT_RATIO).coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            drawSweat(runOut, measurer, streets, styles)
        }
    }
}

private class ChartStyles(val number: TextStyle, val label: TextStyle)

private fun DrawScope.drawSweat(runOut: RunOutState, measurer: TextMeasurer, streets: List<String>, styles: ChartStyles) {
    val xs = Street.entries.indices.map { size.width * (FIRST_X + STEP_X * it) }
    val top = TOP_PAD.toPx()
    val bottom = size.height - BOTTOM_PAD.toPx()
    fun y(pct: Double) = top + ((1 - pct / PERCENT) * (bottom - top)).toFloat()

    // The 50% line, labelled above the next street's marker (where the next point lands). Once the
    // river is out the river's points sit there, so the label goes.
    val mid = y(HALF)
    val edge = EDGE.toPx()
    drawLine(PokerColors.FeltLine, Offset(edge, mid), Offset(size.width - edge, mid), 1.dp.toPx(), pathEffect = dashes(DASH, GAP))
    if (runOut.nextStreet != null) {
        val half = measurer.measure("50%", styles.label.copy(color = PokerColors.Chalk))
        val lift = NEXT_RADIUS.toPx() + 3.dp.toPx() + half.size.height
        drawText(half, topLeft = Offset(size.width - edge - half.size.width, mid - lift))
    }

    runOut.contestants.forEachIndexed { line, seat ->
        val points = runOut.history.map { Offset(xs[it.street.ordinal], y(it.equityPct[seat])) }
        val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val width = if (line == 0) LEAD_LINE else OTHER_LINE
        drawPath(path, lineColour(line), style = Stroke(width = width.toPx(), join = StrokeJoin.Round))
        points.forEachIndexed { i, p ->
            val radius = if (i == points.lastIndex) LAST_DOT else DOT
            drawCircle(lineColour(line), radius = radius.toPx(), center = p)
        }
    }
    if (runOut.contestants.size == 2) drawNumbers(runOut, xs, ::y, measurer, styles.number)

    runOut.nextStreet?.let { next ->
        val ring = Stroke(1.5.dp.toPx(), pathEffect = dashes(DASH, DASH))
        drawCircle(PokerColors.PokerGold, radius = NEXT_RADIUS.toPx(), center = Offset(xs[next.ordinal], mid), style = ring)
    }
    streets.forEachIndexed { i, name ->
        val colour = if (Street.entries[i] == runOut.nextStreet) PokerColors.PokerGold else PokerColors.Chalk
        val text = measurer.measure(name, styles.label.copy(color = colour))
        drawText(text, topLeft = Offset(xs[i] - text.size.width / 2f, size.height - text.size.height))
    }
}

/** Heads-up, each street's two numbers: the higher one above its point, the lower one below. */
private fun DrawScope.drawNumbers(
    runOut: RunOutState,
    xs: List<Float>,
    y: (Double) -> Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val (a, b) = runOut.contestants
    runOut.history.forEach { h ->
        listOf(a to 0, b to 1).forEach { (seat, line) ->
            val other = if (seat == a) b else a
            val above = h.equityPct[seat] >= h.equityPct[other]
            val text = measurer.measure(Math.round(h.equityPct[seat]).toString(), style.copy(color = lineColour(line)))
            val gap = NUMBER_GAP.toPx()
            val point = y(h.equityPct[seat])
            val over = point - gap - text.size.height
            val under = point + gap
            // Keep clear of the street names below and the card's edge above: near 0% or 100% the
            // number goes on the other side of its point.
            val fitsUnder = under + text.size.height <= size.height - BOTTOM_PAD.toPx() / 2
            val top = if ((above && over >= 0f) || !fitsUnder) over else under
            drawText(text, topLeft = Offset(xs[h.street.ordinal] - text.size.width / 2f, top))
        }
    }
}

private fun DrawScope.dashes(on: Dp, off: Dp): PathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))

/** Line colours, one per seat in the hand: gold, Chalk, then the signal colours. */
internal fun lineColour(index: Int): Color = LINE_COLOURS[index % LINE_COLOURS.size]

private val LINE_COLOURS = listOf(
    PokerColors.PokerGold, PokerColors.Chalk, PokerColors.Live, PokerColors.Danger, PokerColors.CardWhite,
    PokerColors.FeltEdge, PokerColors.SuitDiamond4, PokerColors.DarkGold, PokerColors.SuitClub4, PokerColors.ChalkDim,
)

private const val PERCENT = 100.0
private const val HALF = 50.0

/** The mockup's chart is 296 wide: points at 24, 108, 192 and 276, and 108 tall. */
private const val FIRST_X = 24f / 296f
private const val STEP_X = 84f / 296f
private const val HEIGHT_RATIO = 108f / 296f
private val MIN_HEIGHT = 108.dp
private val MAX_HEIGHT = 170.dp
private val TOP_PAD = 22.dp
private val BOTTOM_PAD = 34.dp
private val EDGE = 14.dp
private val NUMBER_GAP = 7.dp
private val NEXT_RADIUS = 9.dp
private val DOT = 4.dp
private val LAST_DOT = 5.dp
private val LEAD_LINE = 3.dp
private val OTHER_LINE = 2.5.dp
private val DASH = 3.dp
private val GAP = 4.dp
private val NUMBER_GLYPH = 15.dp
private val LABEL_GLYPH = 11.dp
