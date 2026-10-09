package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.SidePotsUiState
import com.huntercoles.pokerpayout.tools.table.Pot
import com.huntercoles.pokerpayout.tools.table.PotSplit
import com.huntercoles.pokerpayout.tools.table.Uncalled

/**
 * The pots: the main pot, then each side pot, each with its chips, who can win it and which chips
 * make it ("Over 300, up to 800 from each player"); chips nobody matched go back. The total and
 * how pots are made close the card.
 */
@Composable
internal fun PotsCard(state: SidePotsUiState) {
    ChipSetSection {
        SectionHeader(title = stringResource(R.string.side_pots_pots_title), note = null)
        when (val split = state.split) {
            PotSplit.Empty -> ToolNote(stringResource(R.string.side_pots_empty))
            PotSplit.AllFolded -> NoteBox(ok = false) {
                Text(
                    text = stringResource(R.string.side_pots_all_folded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite,
                )
            }
            is PotSplit.Pots -> {
                val names = state.players.mapIndexed { index, player -> playerName(index, player.name) }
                split.pots.forEachIndexed { number, pot -> PotWell(number, pot, names) }
                split.uncalled?.let { UncalledWell(it, names) }
                Text(
                    text = stringResource(R.string.side_pots_total, chips(split.total)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite,
                )
            }
        }
        ToolNote(stringResource(R.string.side_pots_how))
    }
}

/** One pot, read by TalkBack in one go: "Side pot 1, 1,000, Dana or Sam can win it, Over 300, ...". */
@Composable
private fun PotWell(number: Int, pot: Pot, names: List<String>) {
    ToolWell(merged = true) {
        AmountLine(
            label = if (number == 0) stringResource(R.string.side_pots_main) else stringResource(R.string.side_pots_side, number),
            amount = chips(pot.chips),
        )
        val winners = pot.eligible.map { names[it] }
        Text(
            text = if (winners.size == 1) {
                stringResource(R.string.side_pots_only, winners.single())
            } else {
                stringResource(R.string.side_pots_can_win, joinWithOr(winners))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
        )
        Text(
            text = if (pot.from == 0L) {
                stringResource(R.string.side_pots_up_to, chips(pot.upTo))
            } else {
                stringResource(R.string.side_pots_over, chips(pot.from), chips(pot.upTo))
            },
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
}

/** A bet nobody matched: back to whoever made it. */
@Composable
private fun UncalledWell(uncalled: Uncalled, names: List<String>) {
    ToolWell(merged = true) {
        AmountLine(label = stringResource(R.string.side_pots_back, names[uncalled.player]), amount = chips(uncalled.chips))
        Text(
            text = stringResource(R.string.side_pots_back_why),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
}

/** A result's name on the left and its number on the right, the number under it when they don't fit. */
@Composable
internal fun AmountLine(label: String, amount: String, gold: Boolean = false) {
    SideBySideOrStacked(
        first = { Text(text = label, style = MaterialTheme.typography.titleSmall, color = PokerColors.CardWhite) },
        second = { Text(text = amount, style = AmountStyle, color = if (gold) PokerColors.PokerGold else PokerColors.CardWhite) },
    )
}

/** Pot sizes and prizes in the number face. */
private val AmountStyle = PokerType.NumberM.copy(fontSize = 22.sp, lineHeight = 26.sp)
