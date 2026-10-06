package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/**
 * How much of an amount is in so far ("Collected $540", "Paid out $0 of $495"): a label, the amount,
 * and a gold bar along the bottom edge. Complete (every cent) adds a tick to the label, so done
 * reads in words and shape as well as in the full bar. Replaces the Bank's `SummaryProgressBar`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyMeter(label: String, currentCents: Long, targetCents: Long, modifier: Modifier = Modifier) {
    val complete = targetCents > 0L && currentCents >= targetCents
    val progress = if (targetCents > 0L) (currentCents.toFloat() / targetCents).coerceIn(0f, 1f) else 0f
    val current = FormatUtils.formatMoney(currentCents)
    val target = FormatUtils.formatMoney(targetCents)
    val description = if (complete) {
        stringResource(R.string.money_meter_complete, label, current)
    } else {
        stringResource(R.string.money_meter_description, label, current, target)
    }
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    Box(
        modifier = modifier
            .heightIn(min = MeterHeight)
            .clip(shape)
            .background(PokerColors.FeltDeep)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MeterLabel, color = PokerColors.Chalk)
                if (complete) {
                    Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.Live, modifier = Modifier.size(13.dp))
                }
            }
            // "of $495" moves under the amount rather than break, when the meter is narrow; a long
            // amount at a large font size shrinks to the meter's width rather than break.
            BoxWithConstraints {
                val valueStyle = rememberFittedStyle(MeterValue, listOf(current), maxWidth, floor = MeterValueMin)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(text = current, style = valueStyle, color = PokerColors.CardWhite, softWrap = false)
                    if (!complete && targetCents > 0L) {
                        Text(
                            text = stringResource(R.string.money_meter_of, target),
                            style = MeterLabel.copy(fontSize = 12.sp),
                            color = PokerColors.Chalk,
                            modifier = Modifier
                                .align(Alignment.Bottom)
                                .padding(bottom = 1.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress)
                .height(BarHeight)
                .background(PokerColors.PokerGold),
        )
    }
}

private val MeterHeight = 52.dp
private val BarHeight = 4.dp
private val MeterLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 14.sp)
private val MeterValue = PokerType.NumberM.copy(fontSize = 21.sp, lineHeight = 24.sp)
private val MeterValueMin = 14.dp

@Preview(name = "MoneyMeter", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun MoneyMeterPreview() {
    PokerPreviewPage {
        PokerStage(felt = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyMeter("Collected", currentCents = 54_000, targetCents = 54_000, Modifier.weight(1f))
                MoneyMeter("Paid out", currentCents = 0, targetCents = 49_500, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyMeter("Collected", currentCents = 39_000, targetCents = 54_000, Modifier.weight(1f))
                MoneyMeter("Paid out", currentCents = 22_550, targetCents = 49_500, Modifier.weight(1f))
            }
        }
    }
}
