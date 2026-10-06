package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.CardFaceSize
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.OddsInsights
import com.huntercoles.pokerpayout.tools.poker.rankOf
import com.huntercoles.pokerpayout.tools.poker.suitOf
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import com.huntercoles.pokerpayout.tools.presentation.Street
import com.huntercoles.pokerpayout.tools.presentation.TwiceBoard

/** A FeltGreen card for run it out's sections. */
@Composable
internal fun RunCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * What the table is sweating: the trailing hand's outs drawn as cards ("Player 1 needs one of 16
 * rivers (16 of 44)"), or after the river who won and with what.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutsCard(runOut: RunOutState, fourColour: Boolean) {
    val labels = rememberOddsLabels()
    val outs = runOut.outs
    when {
        runOut.twice != null -> Unit
        runOut.isComplete -> RunCard {
            val winner = runOut.winners.firstOrNull()
            val hand = winner?.let { OddsInsights.describe(runOut.request.seats[it].cards, runOut.board) }
            val line = resultLine(runOut, labels)
            Text(
                text = if (hand != null) stringResource(R.string.odds_runout_with, line, labels.phrase(hand.made)) else line,
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
        }
        outs != null -> RunCard {
            val request = runOut.request.copy(board = runOut.board)
            val focus = OddsInsights.focusSeat(outs, request, runOut.equity) ?: return@RunCard
            val count = outs.leadCount(focus)
            val name = labels.player(focus)
            val needs = if (runOut.street == Street.TURN) R.plurals.odds_runout_needs_river else R.plurals.odds_runout_needs_turn
            val text = if (count == 0) {
                stringResource(R.string.odds_runout_drawing_dead, name)
            } else {
                pluralStringResource(needs, count, name, count, outs.cards.size)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
            // The flush draw's suit first, then by rank: "Q♠ 9♠ ... 2♠ A♥ A♦ A♣ K♥ K♦ K♣ Q♣".
            val flushSuit = OddsInsights.describe(request.seats[focus].cards, request.board).flushDrawSuit
            val ordered = outs.outs(focus).sortedWith(
                compareBy({ if (suitOf(it) == flushSuit) 0 else 1 }, { -rankOf(it) }, { -suitOf(it) }),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ordered.forEach { CardFace(it.toPlayingCard(), size = CardFaceSize.Small, fourColour = fourColour) }
            }
        }
    }
}

/**
 * The big button deals the next street ("Deal the river"); then "Run again from the flop" and
 * "Run it twice". Once the river is out, running again is the big button.
 */
@Composable
internal fun RunButtons(runOut: RunOutState, onIntent: (OddsCalculatorIntent) -> Unit) {
    val ready = !runOut.isDealing
    val again = stringResource(
        when (runOut.startStreet) {
            Street.PREFLOP -> R.string.odds_runout_again_preflop
            Street.FLOP -> R.string.odds_runout_again_flop
            else -> R.string.odds_runout_again_turn
        },
    )
    val twice = stringResource(R.string.odds_runout_twice)
    val runTwice = { onIntent(OddsCalculatorIntent.RunTwice) }
    when (val next = runOut.nextStreet) {
        null -> {
            PokerButton(again, { onIntent(OddsCalculatorIntent.RunAgain) }, Modifier.fillMaxWidth(), enabled = ready)
            PokerButton(twice, runTwice, variant = PokerButtonVariant.Text, size = PokerButtonSize.Small, enabled = ready)
        }
        else -> {
            val deal = stringResource(
                when (next) {
                    Street.FLOP -> R.string.odds_runout_deal_flop
                    Street.TURN -> R.string.odds_runout_deal_turn
                    else -> R.string.odds_runout_deal_river
                },
            )
            PokerButton(deal, { onIntent(OddsCalculatorIntent.DealNext) }, Modifier.fillMaxWidth(), enabled = ready)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PokerButton(
                    text = again,
                    onClick = { onIntent(OddsCalculatorIntent.RunAgain) },
                    variant = PokerButtonVariant.Secondary,
                    size = PokerButtonSize.Small,
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                )
                PokerButton(twice, runTwice, variant = PokerButtonVariant.Text, size = PokerButtonSize.Small, enabled = ready)
            }
        }
    }
}

/** One board of "run it twice": "Run 1 · Player 1 wins" and its five cards. */
@Composable
internal fun TwiceBoardRow(index: Int, board: TwiceBoard, runOut: RunOutState, fourColour: Boolean) {
    val labels = rememberOddsLabels()
    val winners = runOut.contestants.filter { board.equityPct[it] > 0.0 }
    val result = if (winners.size == 1) {
        stringResource(R.string.odds_runout_wins, labels.player(winners.single()))
    } else {
        stringResource(R.string.odds_runout_split)
    }
    RunCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PokerEyebrow(stringResource(R.string.odds_runout_run, index + 1), color = PokerColors.PokerGold)
            Text(result, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val size = if (maxWidth >= MEDIUM_ROW) CardFaceSize.Medium else CardFaceSize.Small
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                board.board.forEach { CardFace(it.toPlayingCard(), size = size, fourColour = fourColour) }
            }
        }
    }
}

/** Five medium cards and their gaps. */
private val MEDIUM_ROW = 44.dp * 5 + 6.dp * 4
