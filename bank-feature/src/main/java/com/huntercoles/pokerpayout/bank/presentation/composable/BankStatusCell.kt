package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.presentation.BankCell
import com.huntercoles.pokerpayout.bank.presentation.BankColumn
import com.huntercoles.pokerpayout.bank.presentation.CellStatus
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PlaceBadge
import com.huntercoles.pokerpayout.core.design.components.PlaceBadgeSize
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/** Each column's icon, the same in the header and in every cell. */
internal fun BankColumn.icon(): ImageVector = when (this) {
    BankColumn.BUY_IN -> MoneyIcons.Cash
    BankColumn.REBUY -> MoneyIcons.Renew
    BankColumn.ADD_ON -> PokerIcons.Plus
    BankColumn.OUT -> MoneyIcons.Exit
    BankColumn.PAID -> MoneyIcons.PaidCheck
}

/**
 * One state cell of a Bank row (S5 v2): a 34 dp shape inside a [width] x 48 dp touch box. The state
 * is in the shape and fill, never only the colour (design spec, section 3):
 * dashed ring (open), solid gold (done, with a white count from 2), a dot (closed, none taken),
 * a faint ring (nothing to do yet), the finishing place on red, a green tick (paid), the amount in
 * a gold ring (owed), the crown (champion).
 *
 * TalkBack reads [description] ("Marcus, rebuy, closed after level 4, 1 taken"), as a button, with
 * the long press as a named action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList") // one cell: what it is, what it says, what tap and hold do
@Composable
internal fun BankStatusCell(
    column: BankColumn,
    cell: BankCell,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    width: Dp = PokerDimens.MinTouch,
    dimmed: Boolean = false,
) {
    val clickable = cell.enabled
    val holdable = cell.holdable && onLongClick != null
    Box(
        modifier = modifier
            .size(width = width, height = PokerDimens.MinTouch)
            .then(
                if (clickable || holdable) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 22.dp, color = PokerColors.PokerGold),
                        role = Role.Button,
                        onClick = { if (clickable) onClick() },
                        onLongClick = if (holdable) onLongClick else null,
                        onLongClickLabel = longClickLabel,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = description
                if (!clickable && !holdable) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        // The cell's description says it all; the shapes inside stay quiet.
        Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { CellShape(column, cell, dimmed) }
    }
}

@Composable
private fun CellShape(column: BankColumn, cell: BankCell, dimmed: Boolean) {
    when (cell.status) {
        CellStatus.Open -> Ring(PokerColors.FeltEdge) { CellIcon(column.icon(), PokerColors.Chalk) }
        CellStatus.Muted -> Ring(PokerColors.FeltLine) { CellIcon(column.icon(), PokerColors.ChalkDim) }
        CellStatus.Done -> Box(Modifier.alpha(if (dimmed) DIMMED_ALPHA else 1f)) {
            Disc(PokerColors.PokerGold) { CellIcon(column.icon(), PokerColors.FeltDeep) }
            if (cell.count >= 2) CountBadge(cell.count, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp))
        }
        CellStatus.ClosedNotTaken -> Box(Modifier.size(6.dp).clip(CircleShape).background(PokerColors.ChalkDim))
        CellStatus.OutPlace -> PlaceBadge(place = cell.place)
        CellStatus.Champion -> PlaceBadge(place = 1, champion = true)
        CellStatus.Paid -> Disc(PokerColors.Live) { CellIcon(PokerIcons.Check, PokerColors.FeltDeep) }
        CellStatus.Owed -> OwedAmount(cell.amountCents)
    }
}

@Composable
private fun CellIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
private fun Disc(color: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(PlaceBadgeSize).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A 1.5 dp dashed ring: "not yet" (FeltEdge) or "nothing to do yet" (FeltLine). */
@Composable
private fun Ring(color: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(PlaceBadgeSize)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2 - stroke / 2,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The white count on a done cell, from 2 ("×2" in the mockups). */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(PokerColors.CardWhite)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = PokerColors.FeltDeep,
            style = PokerType.NumberS.copy(fontSize = fixedSp(12.dp), lineHeight = fixedSp(18.dp)),
        )
    }
}

/** What is still owed, in a 2 dp gold ring; a wide amount stretches the ring into a pill. */
@Composable
private fun OwedAmount(cents: Long) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = PlaceBadgeSize, minHeight = PlaceBadgeSize)
            .widthIn(max = 46.dp)
            .border(2.dp, PokerColors.PokerGold, RoundedCornerShape(17.dp))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        val text = FormatUtils.formatMoney(cents)
        Text(
            text = text,
            color = PokerColors.PokerGold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = PokerType.NumberS.copy(fontSize = fixedSp(owedTextSize(text)), lineHeight = fixedSp(16.dp)),
        )
    }
}

/** 13.5 dp up to "$1,255"; smaller for longer amounts, so they stay inside the 46 dp pill. */
private fun owedTextSize(text: String): Dp = when {
    text.length <= OWED_FULL_SIZE_CHARS -> 13.5.dp
    text.length <= OWED_FULL_SIZE_CHARS + 2 -> 11.5.dp
    else -> 9.5.dp
}

private const val OWED_FULL_SIZE_CHARS = 6
private const val DIMMED_ALPHA = 0.55f

/** A size in dp as a font size that ignores the font scale: the cell is a fixed graphic. */
@Composable
private fun fixedSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }
