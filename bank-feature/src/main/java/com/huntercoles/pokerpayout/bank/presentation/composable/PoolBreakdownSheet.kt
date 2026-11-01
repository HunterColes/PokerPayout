package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/** The pool breakdown as a modal bottom sheet (a tablet shows it open in the side pane instead). */
@Composable
internal fun PoolBreakdownSheet(state: BankUiState, onPayoutStructure: () -> Unit, onDismiss: () -> Unit) {
    PokerSheet(onDismissRequest = onDismiss) {
        PoolBreakdownSheetContent(state, onPayoutStructure, onDismiss)
    }
}

/** The pool breakdown sheet's body: its title, the pool, the payout table, Close and Structure. */
@Composable
internal fun PoolBreakdownSheetContent(state: BankUiState, onPayoutStructure: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetHeading(title = stringResource(R.string.bank_pool_title)) {}
        PoolBreakdownContent(state)
        PayoutTableContent(state)
        ButtonRow {
            PokerButton(
                text = stringResource(R.string.bank_close),
                onClick = onDismiss,
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
                modifier = it,
            )
            PokerButton(
                text = stringResource(R.string.bank_structure),
                onClick = onPayoutStructure,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = MoneyIcons.Scale,
                modifier = it,
            )
        }
    }
}

/**
 * Where the money in the box came from: the prize pool, with the rebuys and add-ons it already
 * holds shown as "of which" lines (they used to be listed as if on top of it, so the lines didn't
 * seem to add up), then the house rake that came off it (PP-145: the host's), the bounty pool, food
 * and the total. Progressive and mystery bounties say so on the bounty pool's line; a mystery pool
 * also says what is still in its envelopes (PP-035). The top-level lines add up to the total.
 */
@Composable
internal fun PoolBreakdownContent(state: BankUiState) {
    val pool = state.pool
    Column {
        Line(stringResource(R.string.bank_pool_prize), pool.prizePoolCents, strong = true)
        if (pool.rebuyCents > 0L) Line(stringResource(R.string.bank_pool_of_rebuys), pool.rebuyCents, sub = true)
        if (pool.addOnCents > 0L) Line(stringResource(R.string.bank_pool_of_add_ons), pool.addOnCents, sub = true)
        if (!pool.rake.isNone) Line(stringResource(R.string.bank_pool_rake, pool.rake.label), pool.rakeCents)
        if (pool.bountyCents > 0L) Line(bountyPoolLabel(state.bountyMode), pool.bountyCents)
        val envelopes = state.envelopesLeft.size
        if (pool.bountyCents > 0L && state.bountyMode == BountyMode.MYSTERY && envelopes > 0) {
            val label = pluralStringResource(R.plurals.bank_pool_envelopes_left, envelopes, envelopes)
            Line(label, state.envelopesLeft.sum(), sub = true)
        }
        if (pool.foodCents > 0L) Line(stringResource(R.string.bank_pool_food), pool.foodCents)
        HorizontalDivider(color = PokerColors.FeltLine)
        Line(stringResource(R.string.bank_pool_total), pool.totalCents, total = true)
    }
}

@Composable
private fun bountyPoolLabel(mode: BountyMode): String = stringResource(
    when (mode) {
        BountyMode.STANDARD -> R.string.bank_pool_bounty
        BountyMode.PROGRESSIVE -> R.string.bank_pool_bounty_pko
        BountyMode.MYSTERY -> R.string.bank_pool_bounty_mystery
    }
)

/** The payout table, the same one the Payouts tab shows, with who holds each decided place. */
@Composable
internal fun PayoutTableContent(state: BankUiState) {
    val holderByPlace = state.placeByPlayer.entries.associate { (playerId, place) -> place to playerId }
    Column {
        PokerEyebrow(stringResource(R.string.bank_payouts), Modifier.semantics { heading() })
        state.payoutTable.places.forEach { row ->
            val holder = holderByPlace[row.place]?.let { id -> state.players.firstOrNull { it.id == id }?.name }
            Line(
                label = stringResource(
                    R.string.bank_payout_row,
                    row.ordinal,
                    holder ?: stringResource(R.string.bank_still_playing)
                ),
                cents = row.amountCents,
                gold = row.place == 1,
            )
        }
    }
}

/** A Line's [cents] as the shared [AmountLine]. */
@Suppress("LongParameterList") // one line: words, amount and its emphasis
@Composable
private fun Line(
    label: String,
    cents: Long,
    strong: Boolean = false,
    sub: Boolean = false,
    total: Boolean = false,
    gold: Boolean = false,
) {
    AmountLine(label, formatMoney(cents), strong = strong, sub = sub, total = total, gold = gold)
}

/**
 * A money line in a Bank sheet: the words on the left, the amount on the right. The pool breakdown
 * and Pay out share it, so the same kind of line looks the same in both: [total] in gold at
 * NumberL, an "of which" line ([sub]) smaller and in Chalk, the rest at NumberAmount.
 */
@Suppress("LongParameterList") // one line: words, amount and its emphasis
@Composable
internal fun AmountLine(
    label: String,
    amount: String,
    strong: Boolean = false,
    sub: Boolean = false,
    total: Boolean = false,
    gold: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (total) PokerDimens.SpacingMedium else PokerDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (strong || total) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (strong || total) PokerColors.CardWhite else PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = when {
                total -> PokerType.NumberL
                sub -> PokerType.NumberM
                else -> PokerType.NumberAmount
            },
            color = when {
                total || gold -> PokerColors.PokerGold
                sub -> PokerColors.Chalk
                else -> PokerColors.CardWhite
            },
            softWrap = false,
        )
    }
}
