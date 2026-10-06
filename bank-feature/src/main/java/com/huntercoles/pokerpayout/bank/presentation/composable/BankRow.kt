package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankColumn
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankRowModel
import com.huntercoles.pokerpayout.bank.presentation.BankSection
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.bank.presentation.CellStatus
import com.huntercoles.pokerpayout.bank.presentation.Purchase
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/**
 * One player on one line (S5 v2): the name (tap to rename) with a micro line under it, then a cell
 * per column. Out rows sit on DangerWash; the champion's row on GoldWash with a gold edge. On a
 * tablet the row also shows what the player paid in and what they are still owed.
 */
@Composable
internal fun BankRow(
    row: BankRowModel,
    state: BankUiState,
    layout: BankLayout,
    onIntent: (BankIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when (row.section) {
        BankSection.OUT -> PokerColors.DangerWash
        BankSection.CHAMPION -> PokerColors.GoldWash
        BankSection.PLAYING -> PokerColors.FeltGreen
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .then(if (row.section == BankSection.CHAMPION) Modifier.goldEdge() else Modifier)
            .heightIn(min = if (layout.showAmounts) 58.dp else 56.dp)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                // Wide enough a gap that the name's 48 dp touch area stays clear of the first cell,
                // even when five open columns leave the name 42 dp (a 320 dp phone).
                .padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NameField(row, large = layout.showAmounts, onRename = { onIntent(BankIntent.PlayerNameChanged(row.playerId, it)) })
            MicroLine(row, layout)
        }
        layout.columns.forEach { column -> Cell(row, column, state, layout, onIntent) }
        if (layout.showAmounts) {
            Amount(FormatUtils.formatMoney(row.paidInCents), PokerColors.CardWhite, Modifier.width(AmountWidth))
            Amount(
                text = if (row.owedCents > 0L) FormatUtils.formatMoney(row.owedCents) else "—",
                color = PokerColors.Chalk,
                modifier = Modifier.width(AmountWidth).padding(end = 12.dp),
            )
        }
    }
}

private fun Modifier.goldEdge(): Modifier = drawBehind {
    drawRect(PokerColors.PokerGold, topLeft = Offset.Zero, size = size.copy(width = 3.dp.toPx()))
}

@Composable
private fun Cell(row: BankRowModel, column: BankColumn, state: BankUiState, layout: BankLayout, onIntent: (BankIntent) -> Unit) {
    val cell = row.cell(column)
    val id = row.playerId
    val purchase = when (column) {
        BankColumn.REBUY -> Purchase.REBUY
        BankColumn.ADD_ON -> Purchase.ADD_ON
        else -> null
    }
    BankStatusCell(
        column = column,
        cell = cell,
        description = cellDescription(row, column, state),
        width = layout.cellWidth,
        dimmed = row.section == BankSection.OUT,
        onClick = {
            when (column) {
                BankColumn.BUY_IN -> onIntent(BankIntent.BuyInToggled(id))
                BankColumn.REBUY, BankColumn.ADD_ON -> onIntent(BankIntent.AddPurchase(id, requireNotNull(purchase)))
                BankColumn.OUT -> onIntent(
                    if (cell.status == CellStatus.OutPlace) BankIntent.BringBack(id) else BankIntent.OpenKnockout(id)
                )
                BankColumn.PAID -> onIntent(BankIntent.OpenPayOut(id))
            }
        },
        onLongClick = purchase?.let { kind -> { onIntent(BankIntent.OpenCount(id, kind)) } },
        longClickLabel = purchase?.let { stringResource(R.string.bank_set_count) },
    )
}

@Composable
private fun Amount(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, style = PokerType.NumberM, color = color, textAlign = TextAlign.End, modifier = modifier)
}

/**
 * The name, edited in place. It is saved when the field loses focus, on Done or Enter, and when the
 * row goes away (tab switch, scrolling), not only on the IME action. A default name ("Player 3") is
 * selected when the field takes focus, so typing replaces it. While not being edited a long name ends
 * in "…" (the field itself, which scrolls rather than ellipsizes, is there but invisible).
 */
@Composable
private fun NameField(row: BankRowModel, large: Boolean, onRename: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var value by remember(row.playerId, row.name) { mutableStateOf(TextFieldValue(row.name)) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val latestText by rememberUpdatedState(value.text)
    val latestName by rememberUpdatedState(row.name)
    val latestOnRename by rememberUpdatedState(onRename)
    val commit = { if (latestText != latestName) latestOnRename(latestText) }

    LaunchedEffect(focused) {
        if (focused && row.name.matches(DefaultName) && value.selection.collapsed) {
            value = value.copy(selection = TextRange(0, value.text.length))
        }
    }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (wasFocused && !focused) commit()
        wasFocused = focused
    }
    DisposableEffect(row.playerId) { onDispose { commit() } }

    val style = NameStyle.copy(fontSize = if (large) 16.sp else 15.sp, color = PokerColors.CardWhite)
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        interactionSource = interactions,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .leaveOnHardwareEnter {
                commit()
                focusManager.clearFocus()
            }
            .underlineWhen(focused),
        decorationBox = { innerTextField ->
            Box {
                Box(Modifier.alpha(if (focused) 1f else 0f)) { innerTextField() }
                if (!focused) {
                    Text(
                        text = value.text,
                        style = style,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The field reads the name to TalkBack; this is only its picture.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        },
    )
}

/** A gold rule under the name while it is being edited. */
private fun Modifier.underlineWhen(focused: Boolean): Modifier = drawBehind {
    if (focused) {
        val y = size.height + 1.dp.toPx()
        drawLine(PokerColors.PokerGold, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
    }
}

private val DefaultName = Regex("^Player \\d+$")
private val NameStyle = TextStyle(fontWeight = FontWeight.Medium, lineHeight = 20.sp)
