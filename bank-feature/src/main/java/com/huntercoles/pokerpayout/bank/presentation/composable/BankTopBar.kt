package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankIntent
import com.huntercoles.pokerpayout.bank.presentation.BankSummary
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The title, the subtitle and three icons. The subtitle carries the money ("5 of 12 left · $1,234.50
 * collected"), so it is never cut (#24): where it doesn't fit on one line beside the icons (a small
 * phone, a large font, a long amount) it moves to its own full-width row under the title and wraps.
 */
@Composable
internal fun BankTopBar(state: BankUiState, onIntent: (BankIntent) -> Unit) {
    val subtitle = subtitle(state.summary)
    BoxWithConstraints {
        val besideIcons = fitsBesideIcons(subtitle, barWidth = maxWidth)
        Column(Modifier.fillMaxWidth()) {
            BankTopBarRow(state, subtitle.takeIf { besideIcons }, onIntent)
            if (!besideIcons) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, bottom = PokerDimens.SpacingSmall),
                )
            }
        }
    }
}

/**
 * Whether [text] fits on one line in PokerTopBar's title column beside the Bank's three icons: the bar
 * less its start gutter, the bar's and the column's 4 dp end padding, and three 48 dp buttons.
 */
@Composable
private fun fitsBesideIcons(text: String, barWidth: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val room = barWidth - PokerDimens.Gutter - TOP_BAR_END_PADDING - PokerDimens.MinTouch * TOP_BAR_ICONS
    val needed = remember(text, style, measurer) {
        measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    }
    return needed <= with(LocalDensity.current) { room.roundToPx() }
}

private val TOP_BAR_END_PADDING = 8.dp
private const val TOP_BAR_ICONS = 3

@Composable
private fun BankTopBarRow(state: BankUiState, subtitle: String?, onIntent: (BankIntent) -> Unit) {
    PokerTopBar(title = stringResource(CoreR.string.navigation_bank), subtitle = subtitle) {
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
        MoreMenu(
            canReset = state.canReset,
            onReset = { onIntent(BankIntent.ShowResetConfirm) },
            onRegulars = { onIntent(BankIntent.ShowRegulars) },
        )
    }
}

/** ⋮: tonight's players from the regulars (any time, a late arrival too), and Reset. */
@Composable
private fun MoreMenu(canReset: Boolean, onReset: () -> Unit, onRegulars: () -> Unit) {
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
                text = { Text(stringResource(R.string.bank_regulars), color = PokerColors.CardWhite) },
                onClick = {
                    open = false
                    onRegulars()
                },
                leadingIcon = { Icon(PokerIcons.People, contentDescription = null, tint = PokerColors.PokerGold) },
            )
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
