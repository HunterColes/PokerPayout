package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.huntercoles.pokerpayout.tools.poker.rankOf
import com.huntercoles.pokerpayout.tools.presentation.OddsTable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.NextCardBreakdown
import com.huntercoles.pokerpayout.tools.poker.OddsInsights
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.OddsResult
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState

/**
 * The insight panel (S9, PP-028), under the seats: the next-card grid and a plain-English "Why"
 * (on a flop or turn with every hand known), and "By the river" (before the river).
 */
@Composable
internal fun InsightPanel(state: OddsCalculatorUiState, modifier: Modifier = Modifier) {
    val result = state.result ?: return
    val request = state.table.toRequest() ?: return
    val labels = rememberOddsLabels()
    val equity = result.players.map { it.equityPct }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val breakdown = state.breakdown
        val focus = breakdown?.let { OddsInsights.focusSeat(it, request, equity) }
        if (breakdown != null && focus != null) {
            InsightCard { NextCardGrid(breakdown, focus, request, labels) }
            InsightCard { WhyText(WhyFacts(request, equity, breakdown, focus), labels) }
        }
        if (request.board.size < OddsTable.BOARD_SLOTS) InsightCard { RiverOutlook(request, result, labels) }
    }
}

/** A FeltGreen section card. */
@Composable
private fun InsightCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltGreen, RoundedCornerShape(PokerDimens.CornerCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** An eyebrow on the left and a short Chalk note on the right, wrapping under it if it must. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionHeader(title: String, note: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PokerEyebrow(title, color = PokerColors.PokerGold, modifier = Modifier.align(Alignment.CenterVertically))
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Every card that can come next, by suit and rank: gold when it puts the [focus] seat in the lead,
 * outlined when it doesn't, struck through when it's already on the table. Fill versus outline
 * versus strike, so it reads without colour. TalkBack reads each cell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextCardGrid(breakdown: NextCardBreakdown, focus: Int, request: OddsRequest, labels: OddsLabels) {
    val focusName = labels.player(focus)
    val leads = breakdown.leadCount(focus)
    val summary = if (leads == 0) {
        stringResource(R.string.odds_next_card_none, focusName)
    } else {
        pluralStringResource(R.plurals.odds_next_card_summary, leads, leads, breakdown.cards.size, focusName)
    }
    SectionHeader(stringResource(R.string.odds_next_card), summary)
    val known = (request.seats.flatMap { it.cards } + request.board + request.dead).toSet()
    val ranks = (Cards.ACE downTo Cards.DEUCE).toList()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.clearAndSetSemantics { }) {
            Box(Modifier.width(SUIT_COLUMN))
            ranks.forEach { rank -> GridText(rankText(rank), PokerColors.Chalk, Modifier.weight(1f).height(16.dp)) }
        }
        GRID_SUITS.forEach { suit ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(SUIT_COLUMN).height(CELL_HEIGHT), contentAlignment = Alignment.Center) {
                    val red = suit == Cards.SUIT_CHARS.indexOf('h') || suit == Cards.SUIT_CHARS.indexOf('d')
                    val tint = if (red) PokerColors.Danger else PokerColors.Chalk
                    Icon(GRID_SUIT_ICONS[suit], contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                }
                ranks.forEach { rank ->
                    val card = Cards.of(rank, suit)
                    val kind = when {
                        card in known -> CellKind.OnTable
                        breakdown.of(card)?.leader == focus -> CellKind.TakesLead
                        else -> CellKind.Stays
                    }
                    GridCell(card, kind, focusName, labels, Modifier.weight(1f))
                }
            }
        }
    }
    val others = request.seats.indices.filter { it != focus && !request.seats[it].folded }
    val stays = others.singleOrNull()?.takeIf { it == breakdown.currentLeader }
    val staysText = if (stays != null) {
        stringResource(R.string.odds_legend_stays_ahead, labels.player(stays))
    } else {
        stringResource(R.string.odds_legend_stays_behind, focusName)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendItem(CellKind.TakesLead, stringResource(R.string.odds_legend_takes_lead, focusName))
        LegendItem(CellKind.Stays, staysText)
        LegendItem(CellKind.OnTable, stringResource(R.string.odds_legend_on_table))
    }
}

private enum class CellKind { TakesLead, Stays, OnTable }

@Composable
private fun GridCell(card: Int, kind: CellKind, focusName: String, labels: OddsLabels, modifier: Modifier) {
    val name = labels.cardName(card)
    val description = when (kind) {
        CellKind.TakesLead -> stringResource(R.string.odds_cell_takes_lead, name, focusName)
        CellKind.Stays -> stringResource(R.string.odds_cell_stays, name, focusName)
        CellKind.OnTable -> stringResource(R.string.odds_cell_on_table, name)
    }
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .cellLook(kind)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val (ink, strike) = when (kind) {
            CellKind.TakesLead -> PokerColors.FeltDeep to false
            CellKind.Stays -> PokerColors.Chalk to false
            // Chalk, struck through: ChalkDim is for disabled controls, and at 4.2:1 it was under AA
            CellKind.OnTable -> PokerColors.Chalk to true
        }
        GridText(rankText(rankOf(card)), ink, Modifier.clearAndSetSemantics { }, strike)
    }
}

/** Gold fill, a FeltEdge outline, or the FeltDeep "on the table" tile. */
private fun Modifier.cellLook(kind: CellKind): Modifier {
    val shape = RoundedCornerShape(4.dp)
    return when (kind) {
        CellKind.TakesLead -> background(PokerColors.PokerGold, shape)
        CellKind.Stays -> border(1.dp, PokerColors.FeltEdge, shape)
        CellKind.OnTable -> background(PokerColors.FeltDeep, shape)
    }
}

/** Grid lettering is drawn at a fixed size, like the cards: the cells can't grow with the font scale. */
@Composable
private fun GridText(text: String, color: Color, modifier: Modifier = Modifier, strike: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color,
            style = PokerType.NumberS.copy(fontSize = GRID_GLYPH.fixedSp(), lineHeight = GRID_GLYPH.fixedSp()),
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun LegendItem(kind: CellKind, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).cellLook(kind))
        Text(text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
}

/**
 * "By the river": for each seat in the hand, the hands it most often ends with, as bars. The
 * strongest rare ones merge into one "Flush+" row.
 */
@Composable
private fun RiverOutlook(request: OddsRequest, result: OddsResult, labels: OddsLabels) {
    SectionHeader(stringResource(R.string.odds_by_the_river), stringResource(R.string.odds_how_often))
    val categories = stringArrayResource(R.array.odds_category_names)
    val res = LocalContext.current.resources
    request.seats.indices.filter { !request.seats[it].folded }.forEach { seat ->
        val cards = request.seats[seat].cards
        val hand = cards.joinToString("", transform = ::cardText).ifEmpty { res.getString(R.string.odds_random_hand) }
        Text(
            text = res.getString(R.string.odds_river_seat, labels.player(seat), hand),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.CardWhite,
        )
        val rows = OddsInsights.riverOutlook(result.players[seat].handCategoryPct).map { row ->
            val name = categories[row.weakest.ordinal]
            (if (row.isRange) res.getString(R.string.odds_category_plus, name) else name) to row.pct
        }
        OutlookBars(rows)
    }
}

/**
 * Label | bar | "36%" rows. The label column is as wide as the longest label (72 dp to 45% of the
 * width) and the number column as wide as "100%", so the bars line up at any font size.
 */
@Composable
private fun OutlookBars(rows: List<Pair<String, Double>>) {
    val labelStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp)
    val numberStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = with(density) { rows.maxOfOrNull { measurer.measure(it.first, labelStyle).size.width }?.toDp() ?: LABEL_MIN }
    val widestNumber = stringResource(R.string.odds_percent_int, PERCENT.toInt())
    val numberWidth = with(density) { measurer.measure(widestNumber, numberStyle).size.width.toDp() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = widest.coerceIn(LABEL_MIN, maxOf(LABEL_MIN, maxWidth * LABEL_SHARE))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, pct) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = labelStyle, color = PokerColors.Chalk, modifier = Modifier.width(labelWidth))
                    Box(Modifier.weight(1f).height(BAR_HEIGHT).background(PokerColors.FeltDeep, RoundedCornerShape(3.dp))) {
                        Box(
                            Modifier
                                .fillMaxWidth((pct / PERCENT).toFloat().coerceIn(0f, 1f))
                                .height(BAR_HEIGHT)
                                .background(PokerColors.PokerGold, RoundedCornerShape(3.dp)),
                        )
                    }
                    Text(
                        text = stringResource(R.string.odds_percent_int, Math.round(pct).toInt()),
                        style = numberStyle,
                        color = PokerColors.CardWhite,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(numberWidth + 1.dp),
                    )
                }
            }
        }
    }
}

private val SUIT_COLUMN = 16.dp
private val CELL_HEIGHT = 24.dp
private val GRID_GLYPH = 12.dp
private val LABEL_MIN = 72.dp
private val BAR_HEIGHT = 6.dp
private const val LABEL_SHARE = 0.45f
private const val PERCENT = 100.0

/** Spades, hearts, diamonds, clubs: the grid's rows, as in the mockup. Indexed by engine suit below. */
private val GRID_SUITS = listOf(3, 2, 1, 0)
private val GRID_SUIT_ICONS = listOf(PokerIcons.Club, PokerIcons.Diamond, PokerIcons.Heart, PokerIcons.Spade)
