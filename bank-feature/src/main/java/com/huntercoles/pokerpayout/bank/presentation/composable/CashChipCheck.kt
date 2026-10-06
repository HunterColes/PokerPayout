package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.domain.cash.ChipCheck
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import kotlin.math.absoluteValue

/**
 * The chip check (S13): cash in against chips counted out, before anyone settles. Balanced is a
 * Live pill; off is a Danger pill with the difference, and the choice to recount or to split it
 * across the stacks; a chosen split says so and can be taken back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipCheckCard(state: CashUiState, onIntent: (CashIntent) -> Unit) {
    val check = state.chipCheck
    val settlement = state.settlement
    val splitCents = (settlement as? CashSettlement.Settled)?.splitCents?.takeIf { it != 0L }
    CashCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PokerEyebrow(
                text = stringResource(R.string.cash_check_title),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .semantics { heading() },
            )
            CheckPill(check, splitCents, modifier = Modifier.align(Alignment.CenterVertically))
        }
        AmountLine(stringResource(R.string.cash_check_in), state.ledger.cashInCents)
        AmountLine(stringResource(R.string.cash_check_out), state.ledger.countedOutCents)
        when {
            check is ChipCheck.Counting -> {
                val uncounted = state.players.filter { !it.isCounted }.map { it.name }
                val names = uncounted.joinToString(", ")
                CashNote(pluralStringResource(R.plurals.cash_check_to_count, uncounted.size, uncounted.size, names))
            }
            check is ChipCheck.Off && splitCents != null -> SplitChosen(splitCents, onIntent)
            check is ChipCheck.Off -> Unbalanced(check.differenceCents, settlement, onIntent)
            else -> Unit
        }
    }
}

@Composable
private fun CheckPill(check: ChipCheck, splitCents: Long?, modifier: Modifier = Modifier) {
    when {
        check is ChipCheck.Counting -> PokerPill(stringResource(R.string.cash_check_counting), modifier, PokerPillTone.Outline)
        check is ChipCheck.Balanced ->
            PokerPill(stringResource(R.string.cash_check_balanced), modifier, PokerPillTone.Live, icon = PokerIcons.Check)
        check is ChipCheck.Off && splitCents != null ->
            PokerPill(stringResource(R.string.cash_check_split), modifier, PokerPillTone.Gold)
        check is ChipCheck.Off -> {
            val amount = formatMoney(check.differenceCents.absoluteValue)
            PokerPill(stringResource(R.string.cash_check_off, amount), modifier, PokerPillTone.Danger)
        }
        else -> Unit
    }
}

/** Off, and nobody chose yet: recount, or split it (when there are chips to split it over). */
@Composable
private fun Unbalanced(differenceCents: Long, settlement: CashSettlement, onIntent: (CashIntent) -> Unit) {
    val amount = formatMoney(differenceCents.absoluteValue)
    val canSplit = (settlement as? CashSettlement.Unbalanced)?.canSplit == true
    when {
        !canSplit -> CashNote(stringResource(R.string.cash_check_nothing_to_split, amount))
        differenceCents > 0L -> CashNote(stringResource(R.string.cash_check_more, amount))
        else -> CashNote(stringResource(R.string.cash_check_less, amount))
    }
    if (canSplit) {
        PokerButton(
            text = stringResource(R.string.cash_split_button, amount),
            onClick = { onIntent(CashIntent.SplitDifference) },
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The players chose to split the difference: how, and the way back. */
@Composable
private fun SplitChosen(splitCents: Long, onIntent: (CashIntent) -> Unit) {
    val amount = formatMoney(splitCents.absoluteValue)
    CashNote(stringResource(if (splitCents > 0L) R.string.cash_check_split_more else R.string.cash_check_split_less, amount))
    PokerButton(
        text = stringResource(R.string.cash_recount_button),
        onClick = { onIntent(CashIntent.Recount) },
        variant = PokerButtonVariant.Text,
        size = PokerButtonSize.Small,
    )
}

/** "Cash in ……… $260": the label wraps if it must; the amount never does. */
@Composable
private fun AmountLine(label: String, cents: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(cents),
            style = PokerType.NumberM.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = PokerColors.CardWhite,
            textAlign = TextAlign.End,
            softWrap = false,
        )
    }
}

@Composable
internal fun CashNote(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk, modifier = modifier)
}
