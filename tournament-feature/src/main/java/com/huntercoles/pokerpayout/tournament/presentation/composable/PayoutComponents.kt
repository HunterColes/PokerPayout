package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.design.components.presetLabel
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutRowModel
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutRows

/**
 * Retired: the payout table lives in the Payouts tab (S6). This compact copy stays only while the
 * Tournament setup still has its Payouts folder tab; it draws the same rows as the tab. Delete this
 * file when the setup stops calling it (M3 removes the folder tabs).
 */
@Deprecated("The Payouts tab (PayoutsScreen) shows the payout table")
@Composable
internal fun PayoutsPanel(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    val table = uiState.payoutTable
    val labels = PayoutPreset.entries.associateWith { presetLabel(it) }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.payouts_prize_pool),
                style = MaterialTheme.typography.bodyLarge,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = FormatUtils.formatMoney(uiState.pool.prizePoolCents),
                style = PokerType.NumberM,
                color = PokerColors.PokerGold
            )
            PokerIconButton(
                icon = PokerIcons.Edit,
                contentDescription = stringResource(R.string.payouts_edit),
                onClick = { onIntent(TournamentConfigIntent.ShowWeightsEditor) },
                tint = PokerColors.PokerGold,
            )
        }
        PokerSegmentedControl<PayoutPreset?>(
            options = PayoutPreset.entries,
            selected = uiState.payoutPreset,
            onSelect = { preset -> preset?.let { onIntent(TournamentConfigIntent.ApplyPayoutPreset(it)) } },
            label = { labels[it].orEmpty() },
            enabled = !uiState.isTournamentLocked,
        )
        val pool = table.prizePoolCents
        PayoutRows(
            table.places.map { row ->
                PayoutRowModel(
                    place = row.place,
                    amountCents = row.amountCents,
                    sharePercent = if (pool > 0L) row.amountCents * PERCENT / pool else row.sharePercent,
                    holderName = uiState.placeNames[row.place],
                )
            },
        )
    }
}

private const val PERCENT = 100.0
