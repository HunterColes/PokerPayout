package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.DealUiState
import com.huntercoles.pokerpayout.tools.table.Deal
import com.huntercoles.pokerpayout.tools.table.DealProblem

/**
 * The deal: per player, their chips and share, then what they take by ICM and by chip chop, side by
 * side (one under the other when they don't fit). Then what is saved for the winner, the check that
 * each way adds up, and a line on how each is worked out. Until there is a deal, what's missing.
 */
@Composable
internal fun DealCard(state: DealUiState) {
    val deal = state.deal
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.deal_result_title),
            note = deal?.let { stringResource(R.string.deal_result_note, FormatUtils.formatMoney(it.splitCents)) },
        )
        if (deal != null) DealRows(state, deal) else DealProblemNote(state.problem)
    }
}

@Composable
private fun DealRows(state: DealUiState, deal: Deal) {
    val stacks = state.players.map { it.chips ?: 0L }
    val allChips = stacks.sum()
    state.players.forEachIndexed { index, player ->
        ToolWell(merged = true) {
            Text(
                text = playerName(index, player.name),
                style = MaterialTheme.typography.titleSmall,
                color = PokerColors.CardWhite,
            )
            val share = percent(PERCENT * stacks[index] / allChips)
            Text(
                text = stringResource(R.string.deal_stack_share, chips(stacks[index]), share),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
            SplitAmounts(icm = deal.icm[index], chipChop = deal.chipChop[index])
        }
    }
    if (deal.forWinnerCents > 0L) {
        Text(
            text = stringResource(R.string.deal_plus_winner, FormatUtils.formatMoney(deal.forWinnerCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
        )
    }
    Text(
        text = stringResource(R.string.deal_adds_up, FormatUtils.formatMoney(deal.splitCents)),
        style = MaterialTheme.typography.bodyMedium,
        color = PokerColors.CardWhite,
    )
    val firstPays = state.prizes.first() - deal.forWinnerCents
    val overFirst = deal.chipChop.indexOfFirst { it > firstPays }
    if (overFirst >= 0) {
        ToolNote(stringResource(R.string.deal_chop_over_first, playerName(overFirst, state.players[overFirst].name)))
    }
    ToolNote(stringResource(R.string.deal_icm_how))
    ToolNote(stringResource(R.string.deal_chop_how, FormatUtils.formatMoney(deal.floorCents)))
}

/** ICM and chip chop for one player, side by side, or one under the other when they don't fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitAmounts(icm: Long, chipChop: Long) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SplitAmount(stringResource(R.string.deal_icm), FormatUtils.formatCents(icm))
        SplitAmount(stringResource(R.string.deal_chip_chop), FormatUtils.formatCents(chipChop))
    }
}

@Composable
private fun SplitAmount(label: String, amount: String) {
    Column(Modifier.widthIn(min = SplitMinWidth)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        Text(text = amount, style = SplitStyle, color = PokerColors.CardWhite)
    }
}

/** What stops the deal: chips or prizes still to type, or prizes that can't be right. */
@Composable
private fun DealProblemNote(problem: DealProblem?) {
    when (problem) {
        DealProblem.MissingChips, null -> ToolNote(stringResource(R.string.deal_missing_chips))
        DealProblem.NoPrizes -> ToolNote(stringResource(R.string.deal_no_prizes))
        is DealProblem.PrizesGoUp -> ProblemBox(
            stringResource(R.string.deal_prizes_go_up, ordinalOf(problem.place), ordinalOf(problem.place - 1)),
        )
        is DealProblem.TooMuchForWinner -> ProblemBox(
            stringResource(R.string.deal_for_winner_too_much, FormatUtils.formatMoney(problem.maxCents)),
        )
    }
}

@Composable
private fun ProblemBox(text: String) {
    NoteBox(ok = false) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
    }
}

private const val PERCENT = 100.0

/** Wide enough that ICM and chip chop line up from one player to the next. */
private val SplitMinWidth = 112.dp
private val SplitStyle = PokerType.NumberM.copy(fontSize = 22.sp, lineHeight = 26.sp)
