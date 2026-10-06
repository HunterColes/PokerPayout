package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashSheet
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.components.fillShellWidth
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The Bank in cash-game mode (S13, PP-029), stateless: the top bar (live subtitle, Undo, ⋮ with
 * Clear), the mode switch, the chip check, the ledger (in, out and net per player), the settle-up
 * with a tick per payment, and Share as text. Players open their own sheet to top up or enter their
 * count. From 840 dp the settle-up and Share move to a side pane, as the tournament Bank's breakdown
 * does (Z5).
 */
@Composable
fun CashLedgerContent(
    state: CashUiState,
    onIntent: (CashIntent) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    modeSwitch: (@Composable () -> Unit)? = null,
) {
    val widthClass = LocalWidthClass.current
    val expanded = widthClass == WidthClass.Expanded
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (expanded) Modifier.fillShellWidth() else Modifier),
    ) {
        CashTopBar(state, onIntent)
        if (expanded) {
            TabletBody(state, onIntent, onShare, modeSwitch)
        } else {
            val gutter = if (widthClass == WidthClass.Small) 12.dp else PokerDimens.Gutter
            PhoneBody(state, onIntent, onShare, modeSwitch, gutter)
        }
    }
    CashSheets(state, onIntent)
}

@Composable
private fun CashTopBar(state: CashUiState, onIntent: (CashIntent) -> Unit) {
    val subtitle = if (state.players.isEmpty()) {
        stringResource(R.string.cash_subtitle_empty)
    } else {
        val count = state.players.size
        pluralStringResource(R.plurals.cash_subtitle, count, count, formatMoney(state.ledger.cashInCents))
    }
    PokerTopBar(title = stringResource(CoreR.string.navigation_bank), subtitle = subtitle) {
        PokerIconButton(
            icon = PokerIcons.Undo,
            contentDescription = state.undoLabel?.let { stringResource(R.string.cash_undo_last, it) }
                ?: stringResource(R.string.cash_undo_nothing),
            onClick = { onIntent(CashIntent.Undo) },
            enabled = state.canUndo,
            tint = PokerColors.PokerGold,
        )
        MoreMenu(canClear = state.canClear, onClear = { onIntent(CashIntent.ClearGame) })
    }
}

/** ⋮: Clear the cash game. It applies at once; Undo brings it back. */
@Composable
private fun MoreMenu(canClear: Boolean, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokerIconButton(
            icon = PokerIcons.More,
            contentDescription = stringResource(R.string.bank_more),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PokerColors.FeltGreen) {
            val tint = if (canClear) PokerColors.Danger else PokerColors.ChalkDim
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cash_clear_menu), color = tint) },
                onClick = {
                    open = false
                    onClear()
                },
                enabled = canClear,
                leadingIcon = { Icon(PokerIcons.Restart, contentDescription = null, tint = tint) },
            )
        }
    }
}

// Phone ---------------------------------------------------------------------------------------------

@Composable
private fun PhoneBody(
    state: CashUiState,
    onIntent: (CashIntent) -> Unit,
    onShare: () -> Unit,
    modeSwitch: (@Composable () -> Unit)?,
    gutter: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        modeSwitch?.invoke()
        if (state.players.isEmpty()) {
            EmptyCard(state, onIntent)
        } else {
            ChipCheckCard(state, onIntent)
            LedgerCard(state, onIntent)
            SettleUpCard(state, onIntent)
            ShareButton(state, onShare)
        }
    }
}

// Tablet --------------------------------------------------------------------------------------------

@Composable
private fun TabletBody(
    state: CashUiState,
    onIntent: (CashIntent) -> Unit,
    onShare: () -> Unit,
    modeSwitch: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            modeSwitch?.invoke()
            if (state.players.isEmpty()) {
                EmptyCard(state, onIntent)
            } else {
                ChipCheckCard(state, onIntent)
                LedgerCard(state, onIntent)
            }
        }
        Column(
            modifier = Modifier
                .width(SidePaneWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettleUpCard(state, onIntent)
            if (state.players.isNotEmpty()) ShareButton(state, onShare)
        }
    }
}

private val SidePaneWidth = 380.dp

// Pieces --------------------------------------------------------------------------------------------

/** A felt card, as the tournament Bank's summary is. */
@Composable
internal fun CashCard(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Before anyone sits down: what the mode is for, and the first player. */
@Composable
private fun EmptyCard(state: CashUiState, onIntent: (CashIntent) -> Unit) {
    CashCard(spacing = 12.dp) {
        Text(
            text = stringResource(R.string.cash_empty_title),
            style = PokerType.Title,
            color = PokerColors.CardWhite,
            modifier = Modifier.semantics { heading() },
        )
        CashNote(stringResource(R.string.cash_empty_body))
        PokerButton(
            text = stringResource(R.string.cash_add_player),
            onClick = { onIntent(CashIntent.ShowAddPlayer) },
            icon = PokerIcons.Plus,
            enabled = state.canAddPlayer,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Share as text: the settle-up for the group chat, once there is one. */
@Composable
private fun ShareButton(state: CashUiState, onShare: () -> Unit) {
    PokerButton(
        text = stringResource(R.string.cash_share),
        onClick = onShare,
        variant = PokerButtonVariant.Secondary,
        icon = PokerIcons.Share,
        enabled = state.settlement is CashSettlement.Settled,
        modifier = Modifier.fillMaxWidth(),
    )
}

// Sheets ----------------------------------------------------------------------------------------------

/** The open sheet, if any, as a modal bottom sheet; its content composable is screenshot-tested. */
@Composable
private fun CashSheets(state: CashUiState, onIntent: (CashIntent) -> Unit) {
    val dismiss = { onIntent(CashIntent.DismissSheet) }
    when (val sheet = state.sheet) {
        CashSheet.AddPlayer -> PokerSheet(onDismissRequest = dismiss) {
            CashAddPlayerSheetContent(state, onIntent, onDismiss = dismiss)
        }
        is CashSheet.Player -> state.ledger.player(sheet.playerId)?.let { player ->
            PokerSheet(onDismissRequest = dismiss) {
                CashPlayerSheetContent(player, state, onIntent, onDone = dismiss)
            }
        }
        null -> Unit
    }
}
