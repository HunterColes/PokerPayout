package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.StringRes
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
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.OutsIntent
import com.huntercoles.pokerpayout.tools.presentation.OutsUiState
import com.huntercoles.pokerpayout.tools.table.Chance
import com.huntercoles.pokerpayout.tools.table.OutsMath
import com.huntercoles.pokerpayout.tools.table.Street

/**
 * The chance to hit, exactly: by the river and with the next card on the flop, the river on the
 * turn; the rule of 4 or 2 beside each, and the odds against under it.
 */
@Composable
internal fun ChanceCard(state: OutsUiState) {
    val ruleOfFour = stringResource(R.string.outs_rule_of_four, OutsMath.ruleOfThumb(state.outs, cards = 2))
    val ruleOfTwo = stringResource(R.string.outs_rule_of_two, OutsMath.ruleOfThumb(state.outs, cards = 1))
    ChipSetSection {
        SectionHeader(title = stringResource(R.string.outs_chance_title), note = null)
        if (state.street == Street.Flop) {
            ChanceWell(R.string.outs_by_river, state.byRiver, ruleOfFour)
            ChanceWell(R.string.outs_next_card, state.nextCard, ruleOfTwo)
            ToolNote(stringResource(R.string.outs_exact_flop))
            if (state.outs > RULE_OF_FOUR_HOLDS_TO) ToolNote(stringResource(R.string.outs_rule_runs_high))
        } else {
            ChanceWell(R.string.outs_on_river, state.byRiver, ruleOfTwo)
            ToolNote(stringResource(R.string.outs_exact_turn))
        }
    }
}

/** One chance, read by TalkBack in one go: "By the river, 35.0%, Rule of 4: 36%, 1.9 to 1 against". */
@Composable
private fun ChanceWell(@StringRes title: Int, chance: Chance, rule: String) {
    val share = stringResource(R.string.outs_percent, percent(chance.percent))
    ToolWell(merged = true) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall, color = PokerColors.CardWhite)
        SideBySideOrStacked(
            first = { Text(text = share, style = ChanceStyle, color = PokerColors.PokerGold) },
            second = { Text(text = rule, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk) },
        )
        chance.againstToOne?.let { against ->
            Text(
                text = stringResource(R.string.outs_against, toOne(against)),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
    }
}

/** The pot and the call, then the share of the pot a call needs and whether the outs cover it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PotOddsCard(state: OutsUiState, onIntent: (OutsIntent) -> Unit) {
    ChipSetSection {
        SectionHeader(title = stringResource(R.string.outs_pot_title), note = null)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val field = Modifier
                .weight(1f)
                .widthIn(min = AmountFieldMinWidth)
            LabelledChips(stringResource(R.string.outs_pot), state.pot, field) { onIntent(OutsIntent.SetPot(it)) }
            LabelledChips(stringResource(R.string.outs_call), state.call, field) { onIntent(OutsIntent.SetCall(it)) }
        }
        val needed = state.equityNeeded
        if (needed == null) {
            ToolNote(stringResource(R.string.outs_type_pot))
        } else {
            Verdict(state, needed)
        }
    }
}

/** A chips field with its label above it, which is also its TalkBack name. */
@Composable
private fun LabelledChips(label: String, value: Long?, modifier: Modifier, onChange: (Long?) -> Unit) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        ChipsField(
            value = value,
            onChange = onChange,
            placeholder = stringResource(R.string.outs_amount_hint),
            description = label,
        )
    }
}

/** "You need 25.0% to call", the pot odds, and the outs held against it. */
@Composable
private fun Verdict(state: OutsUiState, needed: Double) {
    Text(
        text = stringResource(R.string.outs_need, percent(needed)),
        style = MaterialTheme.typography.titleSmall,
        color = PokerColors.CardWhite,
    )
    val potOdds = (state.pot ?: 0L).toDouble() / (state.call ?: 1L)
    Text(
        text = stringResource(R.string.outs_pot_odds, toOne(potOdds)),
        style = MaterialTheme.typography.bodySmall,
        color = PokerColors.Chalk,
    )
    val next = state.nextCard.percent
    val river = state.byRiver.percent
    NoteBox(ok = next >= needed) {
        if (state.street == Street.Flop) {
            val nextLine = if (next >= needed) R.string.outs_next_enough else R.string.outs_next_short
            val riverLine = if (river >= needed) R.string.outs_river_enough else R.string.outs_river_short
            VerdictLine(stringResource(nextLine, percent(next), percent(needed)))
            VerdictLine(stringResource(riverLine, percent(river)))
        } else {
            val riverLine = if (river >= needed) R.string.outs_turn_enough else R.string.outs_turn_short
            VerdictLine(stringResource(riverLine, percent(river), percent(needed)))
        }
        Text(text = stringResource(R.string.outs_later), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
}

@Composable
private fun VerdictLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
}

/** "4.2", "3": odds to one, to one decimal, without a trailing ".0". */
private fun toOne(value: Double): String = percent(value).removeSuffix(".0")

/** The rule of 4 is close up to 8 outs (a flush draw's 9 is already 1.0 high). */
private const val RULE_OF_FOUR_HOLDS_TO = 8

private val AmountFieldMinWidth = 132.dp
private val ChanceStyle = PokerType.NumberL.copy(fontSize = 30.sp, lineHeight = 34.sp)
