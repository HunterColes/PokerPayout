package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankSection
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.bank.presentation.BankSummary
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.ConfirmSheet
import com.huntercoles.pokerpayout.core.design.components.LocalWidthClass
import com.huntercoles.pokerpayout.core.design.components.PayoutPreview
import com.huntercoles.pokerpayout.core.design.components.PayoutStructureSheet
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.WidthClass
import com.huntercoles.pokerpayout.core.design.components.fillShellWidth
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The Bank (S5 v2), stateless: the top bar (live subtitle, Undo, the chime bell and ⋮), the money
 * summary, the labelled sticky header and one line per player, in sections. Below 360 dp closed
 * columns fold into the line under each name (Z2); from 840 dp the list gets wider columns with In
 * and Owed, and the meters, pool breakdown and payout table sit open in a side pane (Z5). Once the
 * night is over with a buy-in still open, the summary offers Settle up: who pays whom, with
 * [onShareSettleUp] for Share as text.
 */
@Composable
fun BankContent(
    state: BankUiState,
    onIntent: (BankIntent) -> Unit,
    modifier: Modifier = Modifier,
    onShareSettleUp: () -> Unit = {},
) {
    val widthClass = LocalWidthClass.current
    val layout = BankLayout.of(state, widthClass)
    val expanded = widthClass == WidthClass.Expanded
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (expanded) Modifier.fillShellWidth() else Modifier),
    ) {
        BankTopBar(state, onIntent)
        if (expanded) {
            TabletBody(state, layout, onIntent)
        } else {
            PhoneBody(state, layout, onIntent)
        }
    }
    BankSheets(state, onIntent, onShareSettleUp)
}

@Composable
private fun BankTopBar(state: BankUiState, onIntent: (BankIntent) -> Unit) {
    PokerTopBar(title = stringResource(CoreR.string.navigation_bank), subtitle = subtitle(state.summary)) {
        PokerIconButton(
            icon = PokerIcons.Undo,
            contentDescription = state.undoLabel?.let { stringResource(R.string.bank_undo_last, it) }
                ?: stringResource(R.string.bank_undo_nothing),
            onClick = { onIntent(BankIntent.Undo) },
            enabled = state.canUndo,
            tint = PokerColors.PokerGold,
        )
        PokerIconButton(
            icon = if (state.isMuted) PokerIcons.VolumeMute else PokerIcons.Bell,
            contentDescription = stringResource(if (state.isMuted) R.string.bank_unmute else R.string.bank_mute),
            onClick = { onIntent(BankIntent.ToggleMute) },
        )
        MoreMenu(canReset = state.canReset, onReset = { onIntent(BankIntent.ShowResetConfirm) })
    }
}

@Composable
private fun MoreMenu(canReset: Boolean, onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokerIconButton(
            icon = PokerIcons.More,
            contentDescription = stringResource(R.string.bank_more),
            onClick = { open = true }
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = PokerColors.FeltGreen,
        ) {
            DropdownMenuItem(
                text = { Text(
                    stringResource(R.string.bank_reset_menu),
                    color = if (canReset) PokerColors.Danger else PokerColors.ChalkDim
                ) },
                onClick = {
                    open = false
                    onReset()
                },
                enabled = canReset,
                leadingIcon = {
                    Icon(
                        PokerIcons.Restart,
                        contentDescription = null,
                        tint = if (canReset) PokerColors.Danger else PokerColors.ChalkDim,
                    )
                },
            )
        }
    }
}

/** "7 of 9 left · $540 collected", "9 players · $0 of $540 collected", "Finished · Dana wins · …". */
@Composable
private fun subtitle(summary: BankSummary): String = when {
    summary.championName != null && summary.stillToPayCents > 0L ->
        stringResource(R.string.bank_subtitle_finished, summary.championName, formatMoney(summary.stillToPayCents))
    summary.championName != null -> stringResource(R.string.bank_subtitle_finished_paid, summary.championName)
    summary.collectedCents == 0L && summary.playersLeft == summary.playerCount -> pluralStringResource(
        R.plurals.bank_subtitle_before,
        summary.playerCount,
        summary.playerCount,
        formatMoney(summary.expectedCents),
    )
    else -> pluralStringResource(
        R.plurals.bank_subtitle_playing,
        summary.playerCount,
        summary.playersLeft,
        summary.playerCount,
        formatMoney(summary.collectedCents)
    )
}

// Phone ---------------------------------------------------------------------------------------------

@Composable
private fun PhoneBody(state: BankUiState, layout: BankLayout, onIntent: (BankIntent) -> Unit) {
    val hint = stringResource(R.string.bank_hint_rename)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = layout.gutter, end = layout.gutter, bottom = 24.dp),
    ) {
        item(key = "summary") {
            MoneySummary(
                state = state,
                buttons = SummaryButtons(
                    onBreakdown = { onIntent(BankIntent.ShowPoolBreakdown) },
                    onPayoutStructure = { onIntent(BankIntent.ShowPayoutStructure) },
                ),
                onSettleUp = { onIntent(BankIntent.ShowSettleUp) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        playerList(state, layout, onIntent, hint = hint)
    }
}

// Tablet --------------------------------------------------------------------------------------------

@Composable
private fun TabletBody(state: BankUiState, layout: BankLayout, onIntent: (BankIntent) -> Unit) {
    val hint = stringResource(R.string.bank_hint_rename_hold)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            playerList(state, layout, onIntent, hint = hint)
        }
        Column(
            modifier = Modifier
                .width(SidePaneWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneySummary(state = state, buttons = null, onSettleUp = { onIntent(BankIntent.ShowSettleUp) })
            PaneCard {
                PokerEyebrow(stringResource(R.string.bank_pool_title), color = PokerColors.PokerGold)
                PoolBreakdownContent(state)
            }
            PaneCard {
                PayoutTableContent(state)
                PokerButton(
                    text = stringResource(R.string.bank_structure),
                    onClick = { onIntent(BankIntent.ShowPayoutStructure) },
                    variant = PokerButtonVariant.Text,
                    size = PokerButtonSize.Small,
                    icon = MoneyIcons.Scale,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

private val SidePaneWidth = 380.dp

@Composable
private fun PaneCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

// The list --------------------------------------------------------------------------------------------

/**
 * The header (sticky), then each section with its label and rows, then the cutoff note. Rows keep
 * their key, so a knocked-out player slides into the Out section (no red flash), or jumps there
 * under Reduce motion.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.playerList(state: BankUiState, layout: BankLayout, onIntent: (BankIntent) -> Unit, hint: String) {
    stickyHeader(key = "header") { BankColumnHeader(state, layout) }
    val sections = state.rows.groupBy { it.section }
    val order = listOf(BankSection.CHAMPION, BankSection.PLAYING, BankSection.OUT).filter { it in sections }
    val last = state.rows.lastOrNull()?.playerId
    order.forEachIndexed { sectionIndex, section ->
        val rows = sections.getValue(section)
        item(key = "section-$section") {
            SectionLabel(section, rows.size, hint.takeIf { sectionIndex == 0 }, first = sectionIndex == 0)
        }
        rows.forEachIndexed { index, row ->
            item(key = row.playerId) {
                val motion = if (LocalReducedMotion.current) Modifier else Modifier.animateItem()
                BankRow(
                    row = row,
                    state = state,
                    layout = layout,
                    onIntent = onIntent,
                    modifier = motion
                        .then(if (row.playerId == last) Modifier.clip(LastRowShape) else Modifier)
                        .then(if (index > 0) Modifier.topRule() else Modifier),
                )
            }
        }
    }
    item(key = "note") { CutoffNote(state) }
}

private val LastRowShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

// Sheets ----------------------------------------------------------------------------------------------

/** The open sheet, if any. Each is a modal bottom sheet; its content composable is screenshot-tested. */
@Composable
private fun BankSheets(state: BankUiState, onIntent: (BankIntent) -> Unit, onShareSettleUp: () -> Unit) {
    val dismiss = { onIntent(BankIntent.DismissSheet) }
    when (val sheet = state.sheet) {
        is BankSheet.Knockout -> KnockoutSheet(
            sheet = sheet,
            onKnockOut = { onIntent(BankIntent.KnockOut(sheet.playerId, it)) },
            onDismiss = dismiss,
        )
        is BankSheet.PayOut -> PayOutSheet(
            sheet = sheet,
            onSetPaid = { onIntent(BankIntent.SetPaid(sheet.playerId, it)) },
            onDismiss = dismiss,
        )
        is BankSheet.Count -> CountSheet(
            sheet = sheet,
            onSet = { onIntent(BankIntent.SetCount(sheet.playerId, sheet.kind, it)) },
            onDismiss = dismiss,
        )
        is BankSheet.Envelope -> EnvelopeSheet(sheet = sheet, onDismiss = dismiss)
        BankSheet.PoolBreakdown -> PoolBreakdownSheet(
            state = state,
            onPayoutStructure = { onIntent(BankIntent.ShowPayoutStructure) },
            onDismiss = dismiss,
        )
        BankSheet.PayoutStructure -> PayoutStructureSheet(
            current = state.payoutSettings,
            preview = PayoutPreview(prizePoolCents = state.prizePoolCents, playerCount = state.players.size),
            onSave = { onIntent(BankIntent.UpdatePayoutSettings(it)) },
            onDismiss = dismiss,
            isLocked = state.isTimerRunning,
        )
        BankSheet.SettleUp -> SettleUpSheet(
            state = state,
            onSetPaid = { transfer, paid -> onIntent(BankIntent.SetSettlePaid(transfer, paid)) },
            onShare = onShareSettleUp,
            onDismiss = dismiss,
        )
        is BankSheet.ResetConfirm -> ConfirmSheet(
            title = stringResource(R.string.bank_reset_title),
            body = pluralStringResource(R.plurals.bank_reset_body, sheet.playerCount, sheet.playerCount),
            dismissLabel = stringResource(R.string.bank_reset_keep),
            confirmLabel = pluralStringResource(R.plurals.bank_reset_confirm, sheet.playerCount, sheet.playerCount),
            onDismiss = dismiss,
            onConfirm = { onIntent(BankIntent.ConfirmReset) },
            destructive = true,
        )
        null -> Unit
    }
}
