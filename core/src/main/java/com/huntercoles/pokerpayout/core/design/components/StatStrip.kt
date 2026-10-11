package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import java.util.Locale

/**
 * The numbers players ask about, side by side: players left, average stack, prize pool. FeltDeep,
 * one column per [Stat] with FeltLine rules between them. Above a 1.5 font scale the stats stack
 * into one column (label and value on one line each), so nothing has to shrink or break.
 * [showSubs] off drops the sub-lines (small phones, Z1).
 */
@Composable
fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier, showSubs: Boolean = true) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val stacked = LocalDensity.current.fontScale > STACK_ABOVE_FONT_SCALE
    val strip = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(PokerColors.FeltDeep)
    if (stacked) {
        Column(strip.padding(horizontal = 16.dp, vertical = 8.dp)) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) Rule(Modifier.fillMaxWidth().height(1.dp))
                StackedStat(stat, showSubs)
            }
        }
    } else {
        BoxWithConstraints(strip) {
            // The values shrink together (never below 18) when a column is too narrow for them
            // at a large font size, rather than break "10,714" across two lines.
            val columns = stats.size.coerceAtLeast(1)
            val textWidth = (maxWidth - RuleWidth * (columns - 1)) / columns - ColumnPadding * 2
            val valueStyle = rememberFittedStyle(PokerType.NumberL, stats.map { it.value }, textWidth, ValueFloor)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 12.dp)) {
                stats.forEachIndexed { index, stat ->
                    if (index > 0) Rule(Modifier.width(RuleWidth).fillMaxHeight())
                    ColumnStat(stat, showSubs, valueStyle, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColumnStat(stat: Stat, showSubs: Boolean, valueStyle: TextStyle, modifier: Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = ColumnPadding)
            .semantics(mergeDescendants = true) { contentDescription = stat.describe(showSubs) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stat.label.uppercase(Locale.ROOT),
            style = PokerType.Eyebrow.copy(fontSize = 12.sp),
            color = PokerColors.Chalk,
            textAlign = TextAlign.Center,
        )
        Text(text = stat.value, style = valueStyle, color = stat.valueColor, textAlign = TextAlign.Center)
        if (showSubs && stat.sub != null) {
            Text(
                text = stat.sub,
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StackedStat(stat: Stat, showSubs: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = stat.describe(showSubs) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stat.label.uppercase(Locale.ROOT), style = PokerType.Eyebrow, color = PokerColors.Chalk)
            if (showSubs && stat.sub != null) {
                Text(text = stat.sub, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
            }
        }
        Text(text = stat.value, style = PokerType.NumberL, color = stat.valueColor)
    }
}

@Composable
private fun Rule(modifier: Modifier) {
    Box(modifier.background(PokerColors.FeltLine))
}

/** "Players, 7, of 9": one TalkBack stop per stat. */
private fun Stat.describe(showSubs: Boolean): String =
    listOfNotNull(label, value, sub?.takeIf { showSubs }).joinToString(", ")

/** Stats stack into one column above this font scale (design spec §6.4). */
private const val STACK_ABOVE_FONT_SCALE = 1.5f
private val RuleWidth = 1.dp
private val ColumnPadding = 6.dp
private val ValueFloor = 18.dp

/** The previews' prize pool: 450. */
private const val PREVIEW_POOL_CENTS = 45_000L

@Preview(name = "StatStrip", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun StatStripPreview() {
    PokerPreviewPage {
        PokerStage {
            StatStrip(
                stats = listOf(
                    Stat("Players", "7", "of 9"),
                    Stat("Avg stack", "10,714", "18 BB"),
                    Stat("Prize pool", FormatUtils.formatMoney(PREVIEW_POOL_CENTS), "3 paid", valueColor = PokerColors.PokerGold),
                ),
            )
            StatStrip(
                stats = listOf(
                    Stat("Players", "7", "of 9"),
                    Stat("Avg", "10,714", "18 BB"),
                    Stat("Pool", FormatUtils.formatMoney(PREVIEW_POOL_CENTS), "3 paid", valueColor = PokerColors.PokerGold),
                ),
                showSubs = false,
            )
        }
    }
}
