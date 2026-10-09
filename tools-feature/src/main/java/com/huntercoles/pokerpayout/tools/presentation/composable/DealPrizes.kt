package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.design.components.MoneyField
import com.huntercoles.pokerpayout.core.design.components.PokerSegmentedControl
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.DealIntent
import com.huntercoles.pokerpayout.tools.presentation.DealUiState
import com.huntercoles.pokerpayout.tools.presentation.PrizeSource
import com.huntercoles.pokerpayout.tools.table.DealProblem

/**
 * The prizes left, one per player still in: tonight's payouts (read only) or typed for the deal
 * (typing starts from tonight's), and what to save for the winner.
 */
@Composable
internal fun PrizesCard(state: DealUiState, onIntent: (DealIntent) -> Unit) {
    val payouts = stringResource(R.string.deal_source_payouts)
    val typed = stringResource(R.string.deal_source_typed)
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.deal_prizes_title),
            note = stringResource(R.string.deal_prizes_places, ordinalOf(1), ordinalOf(state.players.size)),
        )
        PokerSegmentedControl(
            options = PrizeSource.entries,
            selected = state.source,
            onSelect = { onIntent(DealIntent.SetSource(it)) },
            label = { if (it == PrizeSource.Payouts) payouts else typed },
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            state.source == PrizeSource.Typed -> TypedPrizes(state, onIntent)
            state.hasPayouts -> PayoutPrizes(state)
            else -> ToolNote(stringResource(R.string.deal_no_payouts))
        }
        ForWinnerField(state, onIntent)
    }
}

/** Tonight's payouts for the places left, read by TalkBack in one go. */
@Composable
private fun PayoutPrizes(state: DealUiState) {
    ToolWell(merged = true) {
        state.prizes.forEachIndexed { index, prize ->
            AmountLine(label = ordinalOf(index + 1), amount = FormatUtils.formatMoney(prize))
        }
    }
}

/**
 * A money field per place left: "1st prize", "2nd prize", ... Each counts once you leave it (Done,
 * Enter, another field), as the app's money fields do: on a phone the deal sits above, and would
 * otherwise jump between a split and "pays more than" with every key while a prize is half typed.
 */
@Composable
private fun TypedPrizes(state: DealUiState, onIntent: (DealIntent) -> Unit) {
    state.prizes.indices.forEach { index ->
        val place = index + 1
        MoneyField(
            valueCents = state.typed.getOrNull(index),
            label = stringResource(R.string.deal_prize_label, ordinalOf(place)),
            onCommit = { onIntent(DealIntent.SetPrize(place, it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Kept back and played for, up to what 1st pays over 2nd; the rest is shared now. Counts once you leave it. */
@Composable
private fun ForWinnerField(state: DealUiState, onIntent: (DealIntent) -> Unit) {
    val max = FormatUtils.formatMoney(state.maxForWinnerCents)
    val tooMuch = state.problem is DealProblem.TooMuchForWinner
    MoneyField(
        valueCents = state.forWinnerCents,
        label = stringResource(R.string.deal_for_winner),
        onCommit = { onIntent(DealIntent.SetForWinner(it)) },
        supportingText = stringResource(if (tooMuch) R.string.deal_for_winner_too_much else R.string.deal_for_winner_help, max),
        isError = tooMuch,
        modifier = Modifier.fillMaxWidth(),
    )
}
