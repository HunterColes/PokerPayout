package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.MoneyMeter
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * The money at a glance (S5 v2): Collected (paid in against the whole pool) and Paid out (against
 * the prize and bounty pools), each gold when complete, then Breakdown and Payout structure. On a
 * tablet the breakdown and the payout table are open in the side pane, so the buttons go
 * ([buttons] null).
 */
@Composable
internal fun MoneySummary(
    state: BankUiState,
    buttons: SummaryButtons?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyMeter(
                label = stringResource(R.string.bank_collected),
                currentCents = state.totalPaidInCents,
                targetCents = state.totalPoolCents,
                modifier = Modifier.weight(1f),
            )
            MoneyMeter(
                label = stringResource(R.string.bank_paid_out),
                currentCents = state.totalPaidOutCents,
                targetCents = state.payableCents,
                modifier = Modifier.weight(1f),
            )
        }
        if (buttons != null) ButtonRow {
            PokerButton(
                text = stringResource(R.string.bank_breakdown),
                onClick = buttons.onBreakdown,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Info,
                modifier = it,
            )
            PokerButton(
                text = stringResource(R.string.bank_payout_structure),
                onClick = buttons.onPayoutStructure,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = MoneyIcons.Scale,
                modifier = it,
            )
        }
    }
}

/** What the summary's two buttons open. */
internal class SummaryButtons(val onBreakdown: () -> Unit, val onPayoutStructure: () -> Unit)

/**
 * Two buttons side by side, each taking half; at large text sizes one above the other, full width,
 * so neither label has to break a word.
 */
@Composable
internal fun ButtonRow(content: @Composable (Modifier) -> Unit) {
    if (LocalDensity.current.fontScale > STACK_ABOVE_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier.weight(1f)) }
    }
}

private const val STACK_ABOVE_FONT_SCALE = 1.15f
