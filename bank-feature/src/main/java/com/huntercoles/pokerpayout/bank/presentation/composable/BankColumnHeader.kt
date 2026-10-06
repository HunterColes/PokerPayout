package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankColumn
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.mayTruncate
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import java.util.Locale

/**
 * The Bank's legend row (S5 v2), sticky at the top of the list: "PLAYER", then each column's icon
 * and name, with a lock on a column that has closed (rebuys after their cutoff, add-ons after the
 * break). This is what names the round buttons; the emoji chips had no labels. On a tablet it adds
 * the In and Owed headings.
 */
@Composable
internal fun BankColumnHeader(state: BankUiState, layout: BankLayout, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (layout.showAmounts) 58.dp else 54.dp)
            .background(PokerColors.FeltDeep, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .drawBehind {
                drawLine(PokerColors.FeltLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerLabel(Modifier.weight(1f))
        layout.columns.forEach { column ->
            val closed = when (column) {
                BankColumn.REBUY -> !state.rebuyWindow.isOpen
                BankColumn.ADD_ON -> !state.addOnWindow.isOpen
                else -> false
            }
            ColumnLegend(column, closed, layout.iconsOnly, layout.cellWidth)
        }
        if (layout.showAmounts) {
            HeaderLabel(stringResource(R.string.bank_column_in), Modifier.width(AmountWidth), TextAlign.End)
            HeaderLabel(
                stringResource(R.string.bank_column_owed),
                Modifier.width(AmountWidth).padding(end = 12.dp),
                TextAlign.End
            )
        }
    }
}

/** "PLAYER": a fixed size like the legends beside it, one line, and allowed to end in "…" when squeezed. */
@Composable
private fun PlayerLabel(modifier: Modifier) {
    val size = with(LocalDensity.current) { 12.dp.toSp() }
    Text(
        text = stringResource(R.string.bank_column_player).uppercase(Locale.ROOT),
        style = PokerType.Eyebrow.copy(fontSize = size, lineHeight = size, letterSpacing = 0.8.sp),
        color = PokerColors.Chalk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { mayTruncate = true },
    )
}

/** The width of the tablet's In and Owed columns. */
internal val AmountWidth: Dp = 84.dp

@Composable
private fun HeaderLabel(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = PokerType.Eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
        color = PokerColors.Chalk,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
private fun ColumnLegend(column: BankColumn, closed: Boolean, iconsOnly: Boolean, width: Dp) {
    val label = column.label()
    val description = if (closed) stringResource(R.string.bank_column_closed, label) else label
    Column(
        modifier = Modifier
            .width(width)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(column.icon(), contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
            if (closed) {
                Icon(
                    PokerIcons.Lock,
                    contentDescription = null,
                    tint = PokerColors.PokerGold,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 3.dp),
                )
            }
        }
        if (!iconsOnly) {
            // A fixed size, like the cells under it: the five labels have to fit five 48 dp columns.
            val size = with(LocalDensity.current) { LegendText.toSp() }
            Text(
                text = label.uppercase(Locale.ROOT),
                style = PokerType.Eyebrow.copy(fontSize = size, lineHeight = size, letterSpacing = 0.5.sp),
                color = if (closed) PokerColors.Chalk else PokerColors.CardWhite,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private val LegendText = 10.5.dp
