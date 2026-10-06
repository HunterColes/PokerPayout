package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.EquityBar
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.OddsInsights
import com.huntercoles.pokerpayout.tools.poker.PlayerOdds
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorIntent
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.OddsTable
import com.huntercoles.pokerpayout.tools.presentation.SeatState
import com.huntercoles.pokerpayout.tools.presentation.SlotRef
import com.huntercoles.pokerpayout.tools.presentation.SlotValue

/** What the second line under a seat's name says. */
internal sealed interface SeatStatus {
    /** Highest equity: a gold "Favourite" pill (and a gold edge on the row). */
    data object Favourite : SeatStatus

    /** Best made hand right now, though not the favourite. */
    data object AheadNow : SeatStatus

    data object Folded : SeatStatus

    /** The hand isn't fully known: "Q♥ + a random card", "Random hand". */
    data class Hint(val text: String) : SeatStatus

    data object None : SeatStatus
}

/** One seat as the odds screen draws it. */
internal data class SeatUi(
    val index: Int,
    val name: String,
    val seat: SeatState,
    val odds: PlayerOdds?,
    val exact: Boolean,
    val status: SeatStatus,
    /** Under the bar: "Overpair, queens · win 43.94 · tie 0.00". */
    val detail: String?,
)

/** Works out each seat's row from the state: who is the favourite, who is ahead now, the labels. */
internal fun seatUis(state: OddsCalculatorUiState, labels: OddsLabels, res: Resources): List<SeatUi> {
    val table = state.table
    val result = state.result
    val contestants = table.contestants
    val equity = result?.players?.map { it.equityPct }
    // The favourite has the highest equity on its own; a tie at the top has no favourite.
    val favourite = equity?.let { eq ->
        contestants.maxByOrNull { eq[it] }?.takeIf { best -> contestants.count { eq[it] == eq[best] } == 1 }
    }
    val aheadNow = table.toRequest()?.let(OddsInsights::madeLeader)
    val exact = result?.exact == true
    return table.seats.mapIndexed { i, seat ->
        val odds = result?.players?.getOrNull(i)?.takeUnless { it.folded || seat.folded }
        val status = when {
            seat.folded -> SeatStatus.Folded
            !seat.isKnown -> SeatStatus.Hint(hint(seat, res))
            i == favourite -> SeatStatus.Favourite
            i == aheadNow -> SeatStatus.AheadNow
            else -> SeatStatus.None
        }
        SeatUi(i, labels.player(i), seat, odds, exact, status, detail(seat, table, odds, exact, labels, res))
    }
}

/** "Overpair, queens · win 43.94 · tie 0.00": two decimals when exact, one while estimating. */
@Suppress("LongParameterList") // the seat, its odds, and how to word them
private fun detail(
    seat: SeatState,
    table: OddsTable,
    odds: PlayerOdds?,
    exact: Boolean,
    labels: OddsLabels,
    res: Resources,
): String? {
    if (seat.folded) return res.getString(R.string.odds_folded_detail)
    // No label for a duplicated card (an old save): the error line explains.
    val hand = seat.takeIf { it.isKnown }?.let {
        runCatching { labels.hand(OddsInsights.describe(it.known, table.boardCards)) }.getOrNull()
    }
    val winTie = odds?.let {
        val f = if (exact) OddsFormat::twoDecimals else OddsFormat::oneDecimal
        res.getString(R.string.odds_win_tie, f(it.winPct), f(it.equityPct - it.winPct))
    }
    return if (hand != null && winTie != null) res.getString(R.string.odds_detail, hand, winTie) else hand ?: winTie
}

private fun hint(seat: SeatState, res: Resources): String = when {
    seat.known.size == 1 -> res.getString(R.string.odds_one_random, cardText(seat.known.single()))
    seat.cards.all { it == SlotValue.Random } -> res.getString(R.string.odds_random_hand)
    else -> res.getString(R.string.odds_no_cards)
}

/**
 * One seat (S8, S9): its two cards (tap to type them), the name with a status line, the equity
 * number, the equity bar, and the hand label with win and tie. The favourite gets a gold edge.
 * Tapping the name opens the seat's menu: fold, swap, random hand, clear, remove.
 */
@Composable
internal fun SeatRow(
    ui: SeatUi,
    table: OddsTable,
    target: SlotRef?,
    fourColour: Boolean,
    onIntent: (OddsCalculatorIntent) -> Unit,
) {
    val labels = rememberOddsLabels()
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, shape)
            .then(if (ui.status == SeatStatus.Favourite) Modifier.border(1.5.dp, PokerColors.PokerGold, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeatHeader(
            cards = {
                Row(horizontalArrangement = Arrangement.spacedBy(CardTargetGap)) {
                    ui.seat.cards.forEachIndexed { j, value ->
                        val ref = SlotRef.Hole(ui.index, j)
                        val name = stringResource(R.string.odds_slot_hole, ui.name, j + 1)
                        CardTarget(
                            value = value,
                            description = slotDescription(name, value, ref == target, labels),
                            onClick = { onIntent(OddsCalculatorIntent.SelectSlot(ref)) },
                            style = CardTargetStyle(isTarget = ref == target, fourColour = fourColour, dim = ui.seat.folded),
                        )
                    }
                }
            },
            info = { SeatInfo(ui, table, onIntent) },
            equity = {
                if (!ui.seat.folded) {
                    EquityText(ui.odds?.equityPct, estimate = ui.odds != null && !ui.exact, style = equityStyle())
                }
            },
        )
        if (!ui.seat.folded) {
            val odds = ui.odds
            EquityBar(
                win = ((odds?.winPct ?: 0.0) / PERCENT).toFloat(),
                tie = ((odds?.let { it.equityPct - it.winPct } ?: 0.0) / PERCENT).toFloat(),
                estimate = odds != null && !ui.exact,
            )
        }
        ui.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk) }
    }
}

/**
 * Cards, name and equity on one line when they fit; otherwise (narrow screens, large text) the name
 * drops below the cards and the equity stays beside them.
 */
@Composable
internal fun SeatHeader(
    cards: @Composable () -> Unit,
    info: @Composable () -> Unit,
    equity: @Composable () -> Unit,
) {
    Layout(contents = listOf(cards, info, equity), modifier = Modifier.fillMaxWidth()) { (c, i, e), constraints ->
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val cardsP = c.first().measure(loose)
        val equityP = e.firstOrNull()?.measure(loose)
        val equityW = equityP?.width ?: 0
        val room = width - cardsP.width - equityW - 2 * gap
        val inline = room >= i.first().minIntrinsicWidth(Constraints.Infinity)
        val infoP = i.first().measure(Constraints(minWidth = if (inline) room else 0, maxWidth = if (inline) room else width))
        val top = maxOf(cardsP.height, equityP?.height ?: 0, if (inline) infoP.height else 0)
        val height = if (inline) top else top + gap / 2 + infoP.height
        layout(width, height) {
            cardsP.place(0, (top - cardsP.height) / 2)
            equityP?.place(width - equityW, (top - equityP.height) / 2)
            if (inline) infoP.place(cardsP.width + gap, (top - infoP.height) / 2) else infoP.place(0, top + gap / 2)
        }
    }
}

/** The seat's name (tap for its menu) and the status line under it. */
@Composable
private fun SeatInfo(ui: SeatUi, table: OddsTable, onIntent: (OddsCalculatorIntent) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val options = stringResource(R.string.odds_seat_options, ui.name)
    Box {
        Column(
            modifier = Modifier
                .heightIn(min = PokerDimens.MinTouch)
                .widthIn(min = PokerDimens.MinTouch)
                .clickable(role = Role.Button) { menuOpen = true }
                .semantics { contentDescription = options },
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ui.name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
                Icon(PokerIcons.ChevronDown, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
            }
            when (val status = ui.status) {
                SeatStatus.Favourite -> PokerPill(stringResource(R.string.odds_favourite), tone = PokerPillTone.Gold)
                SeatStatus.Folded -> PokerPill(stringResource(R.string.odds_folded), tone = PokerPillTone.Muted)
                SeatStatus.AheadNow -> Text(
                    text = stringResource(R.string.odds_ahead_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Live,
                )
                is SeatStatus.Hint -> Text(status.text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
                SeatStatus.None -> Unit
            }
        }
        SeatMenu(expanded = menuOpen, ui = ui, table = table, onDismiss = { menuOpen = false }, onIntent = onIntent)
    }
}

/** Fold or bring back, swap with another seat, random hand, clear the cards, remove the seat. */
@Composable
private fun SeatMenu(
    expanded: Boolean,
    ui: SeatUi,
    table: OddsTable,
    onDismiss: () -> Unit,
    onIntent: (OddsCalculatorIntent) -> Unit,
) {
    val labels = rememberOddsLabels()
    val items = buildList {
        val canFold = ui.seat.folded || table.contestants.size > OddsTable.MIN_SEATS
        if (canFold) {
            val text = stringResource(if (ui.seat.folded) R.string.odds_menu_unfold else R.string.odds_menu_fold)
            add(text to listOf(OddsCalculatorIntent.Fold(ui.index, folded = !ui.seat.folded)))
        }
        table.seats.indices.filter { it != ui.index }.forEach { other ->
            val swap = stringResource(R.string.odds_menu_swap, labels.player(other))
            add(swap to listOf(OddsCalculatorIntent.Swap(ui.index, other)))
        }
        val open = ui.seat.cards.indexOfFirst { it == SlotValue.Empty }
        if (open >= 0) {
            // Aim the keypad at the seat, leave its missing cards random, and put the keypad away.
            val random = listOf(
                OddsCalculatorIntent.SelectSlot(SlotRef.Hole(ui.index, open)),
                OddsCalculatorIntent.RandomHand,
                OddsCalculatorIntent.CloseKeypad,
            )
            add(stringResource(R.string.odds_menu_random) to random)
        }
        if (ui.seat.known.isNotEmpty() || ui.seat.cards.any { it == SlotValue.Random }) {
            add(stringResource(R.string.odds_menu_clear) to listOf(OddsCalculatorIntent.ClearHand(ui.index)))
        }
        if (table.seats.size > OddsTable.MIN_SEATS) {
            add(stringResource(R.string.odds_menu_remove, ui.name) to listOf(OddsCalculatorIntent.RemovePlayer(ui.index)))
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = PokerColors.FeltGreen) {
        items.forEach { (text, intents) ->
            DropdownMenuItem(
                text = { Text(text, color = PokerColors.CardWhite) },
                onClick = {
                    onDismiss()
                    intents.forEach(onIntent)
                },
            )
        }
    }
}

private const val PERCENT = 100.0
