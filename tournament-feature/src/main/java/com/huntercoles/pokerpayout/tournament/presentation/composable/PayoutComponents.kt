package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PresetChips
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlace
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState

/**
 * The Payouts tab: the prize pool, one-tap presets, and what each place pays. The rows always add
 * up to the prize pool shown at the top. Rounding and the number of places are in the editor (✎).
 */
@Composable
internal fun PayoutsPanel(uiState: TournamentConfigUiState, onIntent: (TournamentConfigIntent) -> Unit) {
    val table = uiState.payoutTable
    val enabled = !uiState.isTournamentLocked
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Prize pool",
                color = PokerColors.CardWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = FormatUtils.formatCents(uiState.pool.prizePoolCents),
                color = PokerColors.PokerGold,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            PresetChips(
                selected = uiState.payoutPreset,
                enabled = enabled,
                onSelect = { onIntent(TournamentConfigIntent.ApplyPayoutPreset(it)) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onIntent(TournamentConfigIntent.ShowWeightsEditor) }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit payout structure",
                    tint = PokerColors.PokerGold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = PokerColors.PokerGold.copy(alpha = 0.3f))

        // Up to the 9 places a table can have; scrolls rather than pushing the clock off screen.
        Column(
            modifier = Modifier
                .heightIn(max = 168.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            table.places.forEach { row -> PayoutRow(row, uiState.placeNames[row.place]) }
        }

        HorizontalDivider(color = PokerColors.PokerGold.copy(alpha = 0.3f))
        PayoutFooter(uiState, enabled, onIntent)
    }
}

@Composable
private fun PayoutRow(row: PayoutPlace, playerName: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.ordinal,
            color = PokerColors.PokerGold,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            softWrap = false,
            // The columns grow with large text rather than break "2nd" or an amount across lines
            modifier = Modifier.widthIn(min = 44.dp)
        )
        Text(
            text = playerName.orEmpty(),
            color = PokerColors.CardWhite,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = FormatUtils.formatPercent(row.sharePercent),
            color = PokerColors.CardWhite.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            softWrap = false,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        Text(
            text = FormatUtils.formatCents(row.amountCents),
            color = PokerColors.CardWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            softWrap = false,
            modifier = Modifier.widthIn(min = 104.dp)
        )
    }
}

@Composable
private fun PayoutFooter(
    uiState: TournamentConfigUiState,
    enabled: Boolean,
    onIntent: (TournamentConfigIntent) -> Unit
) {
    val paid = uiState.paidPlaces
    val recommended = uiState.recommendedPlaces
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$paid of ${uiState.playerCount} paid · rounded to ${uiState.config.payoutRounding.label}",
                color = PokerColors.CardWhite.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            if (paid != recommended) {
                Text(
                    text = "Tip: pay about a third of the field ($recommended).",
                    color = PokerColors.CardWhite.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
        if (paid != recommended && enabled) {
            TextButton(onClick = { onIntent(TournamentConfigIntent.SetPaidPlaces(recommended)) }) {
                Text("Pay $recommended", color = PokerColors.PokerGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
