package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.LocalReducedMotion
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.CardFace
import com.huntercoles.pokerpayout.core.design.components.EquityBar
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import com.huntercoles.pokerpayout.tools.presentation.Street
import kotlin.math.abs

/**
 * Run it out (S10), the full-screen sweat inside the odds route. The board deals one street at a
 * time; each seat's equity bar swings with an arrow and the change; "The sweat" charts every
 * street; the outs are drawn as cards. In a landscape window (propped up, or the header's ⤢) the
 * board and seats sit on the left and the chart, outs and buttons on the right.
 */
@Composable
internal fun RunItOutContent(
    runOut: RunOutState,
    fourColour: Boolean,
    onIntent: (OddsCalculatorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        val twoPanes = maxWidth > maxHeight && maxWidth >= TWO_PANES_FROM
        Column(Modifier.fillMaxSize()) {
            val toggle = if (runOut.landscape) R.string.odds_runout_portrait else R.string.odds_runout_landscape
            PokerTopBar(
                title = stringResource(R.string.odds_runout_title),
                subtitle = runOutStatus(runOut),
                onBack = { onIntent(OddsCalculatorIntent.ExitRunItOut) },
            ) {
                PokerIconButton(
                    icon = if (runOut.landscape) PokerIcons.Close else PokerIcons.Fullscreen,
                    contentDescription = stringResource(toggle),
                    onClick = { onIntent(OddsCalculatorIntent.SetRunOutLandscape(!runOut.landscape)) },
                )
            }
            if (twoPanes) {
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = PokerDimens.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(PokerDimens.Gutter),
                ) {
                    Pane(Modifier.weight(1f)) { RunTable(runOut, fourColour) }
                    Pane(Modifier.weight(1f)) { RunSide(runOut, fourColour, onIntent) }
                }
            } else {
                Pane(Modifier.weight(1f).padding(horizontal = PokerDimens.Gutter).align(Alignment.CenterHorizontally)) {
                    RunTable(runOut, fourColour)
                    RunSide(runOut, fourColour, onIntent)
                }
            }
        }
    }
}

/** A scrolling column, at most 720 dp wide. */
@Composable
private fun Pane(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .widthIn(max = PANE_MAX_WIDTH)
            .verticalScroll(rememberScrollState())
            .padding(bottom = PokerDimens.SpacingDefault),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The board (or both boards of run it twice) and the seats. */
@Composable
private fun RunTable(runOut: RunOutState, fourColour: Boolean) {
    val twice = runOut.twice
    if (twice == null) {
        RunBoard(runOut, fourColour)
    } else {
        twice.forEachIndexed { i, board -> TwiceBoardRow(i, board, runOut, fourColour) }
    }
    // Run twice: each seat's share of the pot is the average of its two halves.
    val equity = runOut.contestants.associateWith { seat ->
        twice?.let { boards -> boards.sumOf { it.equityPct[seat] } / boards.size } ?: runOut.equity.getOrElse(seat) { 0.0 }
    }
    val best = equity.values.maxOrNull()
    val lead = equity.filterValues { it == best }.keys.singleOrNull()
    runOut.contestants.forEach { seat ->
        RunSeatRow(runOut, seat, equity[seat], lead = seat == lead, fourColour = fourColour)
    }
}

/** The chart, the outs (or the result) and the buttons. */
@Composable
private fun RunSide(runOut: RunOutState, fourColour: Boolean, onIntent: (OddsCalculatorIntent) -> Unit) {
    if (runOut.twice == null && runOut.history.isNotEmpty()) SweatCard(runOut)
    OutsCard(runOut, fourColour)
    RunButtons(runOut, onIntent)
}

/**
 * One seat in run it out: cards, name with the swing ("▼ 19.7", an arrow and a number, not just
 * colour), the equity, and a thick bar. The leader has a gold edge; after the river the winner
 * gets a gold glow and a "Wins" / "Holds" pill.
 */
@Composable
private fun RunSeatRow(runOut: RunOutState, seat: Int, equity: Double?, lead: Boolean, fourColour: Boolean) {
    val labels = rememberOddsLabels()
    val delta = runOut.delta.getOrNull(seat)?.takeIf { runOut.twice == null }
    val won = seat in runOut.winners && runOut.twice == null
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    val reduced = LocalReducedMotion.current
    val bar by animateFloatAsState(
        targetValue = ((equity ?: 0.0) / PERCENT).toFloat(),
        animationSpec = tween(if (reduced) 0 else BAR_MS),
        label = "equity",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (won && !reduced) Modifier.drawBehind { drawGlow() } else Modifier)
            .background(PokerColors.FeltGreen, shape)
            .then(if (lead || won) Modifier.border(if (won) 2.dp else 1.5.dp, PokerColors.PokerGold, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeatHeader(
            cards = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    runOut.request.seats[seat].cards.forEach { CardFace(it.toPlayingCard(), fourColour = fourColour) }
                }
            },
            info = { RunSeatInfo(runOut, seat, labels.player(seat), delta, won) },
            equity = { EquityText(equity, estimate = false, style = equityStyle(RUN_EQUITY_SIZE)) },
        )
        EquityBar(win = bar, tie = 0f, large = true)
    }
}

/** The name, then the swing ("▲ 19.7" in Live, "▼ 19.7" in Danger) or the result pill after the river. */
@Composable
private fun RunSeatInfo(runOut: RunOutState, seat: Int, name: String, delta: Double?, won: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
        when {
            won -> {
                val pill = when {
                    runOut.winners.size > 1 -> R.string.odds_runout_split_pill
                    seat == runOut.leaderBeforeRiver -> R.string.odds_runout_holds_pill
                    else -> R.string.odds_runout_winner_pill
                }
                PokerPill(stringResource(pill), tone = PokerPillTone.Gold)
            }
            delta != null && abs(delta) >= MIN_DELTA -> {
                val up = delta > 0
                val amount = OddsFormat.oneDecimal(abs(delta))
                val spoken = stringResource(if (up) R.string.odds_runout_up else R.string.odds_runout_down, amount)
                val colour = if (up) PokerColors.Live else PokerColors.Danger
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                ) {
                    val arrow = if (up) PokerIcons.TriangleUp else PokerIcons.TriangleDown
                    Icon(arrow, contentDescription = null, tint = colour, modifier = Modifier.size(12.dp))
                    Text(amount, style = PokerType.NumberS, color = colour)
                }
            }
        }
    }
}

/** "Turn dealt · one card to come", "River dealt · Player 1 wins", "Dealing…". */
@Composable
private fun runOutStatus(runOut: RunOutState): String {
    val labels = rememberOddsLabels()
    return when {
        runOut.isDealing -> stringResource(R.string.odds_runout_status_dealing)
        runOut.twice != null -> stringResource(R.string.odds_runout_status_twice, stringResource(R.string.odds_runout_pot_split))
        runOut.street == Street.RIVER -> stringResource(R.string.odds_runout_status_river, resultLine(runOut, labels))
        runOut.street == Street.TURN -> stringResource(R.string.odds_runout_status_turn)
        runOut.street == Street.FLOP -> stringResource(R.string.odds_runout_status_flop)
        else -> stringResource(R.string.odds_runout_status_preflop)
    }
}

/** "Player 1 wins", "Player 2 holds", "Split pot". */
@Composable
internal fun resultLine(runOut: RunOutState, labels: OddsLabels): String {
    val winner = runOut.winners.singleOrNull()
    return when (winner) {
        null -> stringResource(R.string.odds_runout_split)
        runOut.leaderBeforeRiver -> stringResource(R.string.odds_runout_holds, labels.player(winner))
        else -> stringResource(R.string.odds_runout_wins, labels.player(winner))
    }
}

/** A soft gold glow around the winner's row. */
private fun DrawScope.drawGlow() {
    val spread = GLOW_SPREAD.toPx()
    drawRoundRect(
        color = PokerColors.PokerGold.copy(alpha = GLOW_ALPHA),
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + 2 * spread, size.height + 2 * spread),
        cornerRadius = CornerRadius(PokerDimens.CornerCard.toPx() + spread),
    )
}

private val TWO_PANES_FROM = 600.dp
private val PANE_MAX_WIDTH = 720.dp
private val GLOW_SPREAD = 6.dp
private val RUN_EQUITY_SIZE = 40.sp
private const val PERCENT = 100.0
private const val MIN_DELTA = 0.05
private const val BAR_MS = 600
private const val GLOW_ALPHA = 0.18f
