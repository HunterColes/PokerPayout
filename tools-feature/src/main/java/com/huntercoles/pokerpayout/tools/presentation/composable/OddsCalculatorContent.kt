package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.components.ToggleChip
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.OddsTable
import com.huntercoles.pokerpayout.tools.presentation.SlotValue

/**
 * The odds screen, stateless (S8 entering cards, S9 results). Top to bottom: the header (back, Swap,
 * New hand), the board, one row per seat, "Add player", "Run it out", the insight panel, the table
 * settings, and the docked keypad while it is open. In a short landscape window the keypad docks at
 * the side.
 */
@Composable
fun OddsCalculatorContent(
    state: OddsCalculatorUiState,
    onIntent: (OddsCalculatorIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        val sideKeypad = state.keypad.isOpen && maxWidth > maxHeight && maxHeight < SIDE_KEYPAD_BELOW
        // As wide as seven 48 dp keys, but never leaving the board less than it needs.
        val sideWidth = (maxWidth - SIDE_CONTENT_MIN).coerceAtMost(SIDE_KEYPAD_WIDTH)
        if (sideKeypad) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) {
                    OddsHeader(state, onIntent, onBack)
                    OddsBody(state, onIntent, Modifier.weight(1f))
                }
                CardKeypad(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier
                        .width(sideWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    dock = KeypadDock.Side,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                OddsHeader(state, onIntent, onBack)
                OddsBody(state, onIntent, Modifier.weight(1f))
                CardKeypad(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.widthIn(max = KEYPAD_MAX_WIDTH).fillMaxWidth().align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** "Odds" with the live status underneath, then Swap and New hand (as in the mockup). */
@Composable
private fun OddsHeader(state: OddsCalculatorUiState, onIntent: (OddsCalculatorIntent) -> Unit, onBack: () -> Unit) {
    PokerTopBar(title = stringResource(R.string.odds_title), subtitle = oddsStatus(state), onBack = onBack) {
        PokerIconButton(PokerIcons.Swap, stringResource(R.string.odds_swap), onClick = { onIntent(OddsCalculatorIntent.Swap()) })
        PokerIconButton(PokerIcons.Restart, stringResource(R.string.odds_new_hand), { onIntent(OddsCalculatorIntent.NewHand) })
    }
}

/** The table settings at the end of the page: the four-colour deck, and clearing back to two seats. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OddsFooter(state: OddsCalculatorUiState, onIntent: (OddsCalculatorIntent) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToggleChip(
            label = stringResource(R.string.odds_four_colour_deck),
            checked = state.fourColourDeck,
            onCheckedChange = { onIntent(OddsCalculatorIntent.SetFourColourDeck(it)) },
            icon = PokerIcons.Diamond,
        )
        PokerButton(
            text = stringResource(R.string.odds_clear_table),
            onClick = { onIntent(OddsCalculatorIntent.ClearTable) },
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            icon = PokerIcons.Close,
        )
    }
}

/** The scrolling part: board, seats, add player, run it out, insight. Centred at 720 dp on tablets. */
@Composable
private fun OddsBody(state: OddsCalculatorUiState, onIntent: (OddsCalculatorIntent) -> Unit, modifier: Modifier) {
    val labels = rememberOddsLabels()
    val res = LocalContext.current.resources
    val seats = remember(state.table, state.result, labels) { seatUis(state, labels, res) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokerDimens.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoardStrip(state.table, state.keypad.target, state.fourColourDeck, onIntent)
            seats.forEach { SeatRow(it, state.table, state.keypad.target, state.fourColourDeck, onIntent) }
            if (state.table.seats.size < OddsTable.MAX_SEATS) {
                PokerButton(
                    text = stringResource(R.string.odds_add_player),
                    onClick = { onIntent(OddsCalculatorIntent.AddPlayer) },
                    variant = PokerButtonVariant.Secondary,
                    size = PokerButtonSize.Small,
                    icon = PokerIcons.Plus,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Danger) }
            if (state.canRunItOut && state.table.boardCards.size < OddsTable.BOARD_SLOTS) {
                PokerButton(
                    text = stringResource(R.string.odds_run_it_out),
                    onClick = { onIntent(OddsCalculatorIntent.RunItOut) },
                    icon = PokerIcons.Play,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            InsightPanel(state)
            OddsFooter(state, onIntent)
            Spacer(Modifier.height(PokerDimens.SpacingDefault))
        }
    }
}

/**
 * The header's live status, short enough for a 360 dp phone: "Flop · exact · 990 runouts",
 * "Preflop · exact", "Preflop · estimating" (a hand still being typed), "Preflop · estimate ±0.2"
 * (a 95% margin), or what to do first.
 */
@Composable
private fun oddsStatus(state: OddsCalculatorUiState): String {
    val table = state.table
    val street = streetName(table.boardCards.size)
    val result = state.result
    val typing = table.contestants.any { seat -> table.seats[seat].cards.any { it == SlotValue.Empty } }
    return when {
        state.error != null -> stringResource(R.string.odds_status_error, street)
        result == null && state.isCalculating -> stringResource(R.string.odds_status_working, street)
        result == null -> stringResource(R.string.odds_status_empty)
        result.exact && table.boardCards.size == OddsTable.BOARD_SLOTS -> stringResource(R.string.odds_status_showdown, street)
        result.exact && result.deals < COUNT_RUNOUTS_BELOW ->
            stringResource(R.string.odds_status_exact, street, OddsFormat.grouped(result.deals))
        result.exact -> stringResource(R.string.odds_status_exact_plain, street)
        typing -> stringResource(R.string.odds_status_typing, street)
        else -> stringResource(R.string.odds_status_estimate, street, OddsFormat.oneDecimal(MARGIN_95 * result.maxStdErr))
    }
}

/** A window shorter than this, in landscape, docks the keypad at the side. */
private val SIDE_KEYPAD_BELOW = 480.dp
private val SIDE_KEYPAD_WIDTH = 352.dp

/** The board strip's five 48 dp targets, its padding and the gutters. */
private val SIDE_CONTENT_MIN = 320.dp
private val KEYPAD_MAX_WIDTH = 560.dp
private val CONTENT_MAX_WIDTH = 720.dp

/** Runout counts this big are left out of the status (preflop's 1,712,304 won't fit). */
private const val COUNT_RUNOUTS_BELOW = 10_000L

/** 1.96 standard errors: the half-width of a 95% interval. */
private const val MARGIN_95 = 1.96
