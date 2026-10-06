package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.bank.presentation.cash.ledgerAmount
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.cash.CashPlayer
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/** What TalkBack reads for each amount: "in $40", "out $112", "up $72"; null reads nothing. */
private class Spoken(val paidIn: String?, val out: String?, val net: String?)

/**
 * A line's three amounts, as shown: In, Out and Net (with the colour of its sign), and as spoken. The
 * line merges for TalkBack, so it reads "Dana, in $40, out $112, up $72", and each text stays in
 * the tree for the layout checks.
 */
private class LedgerAmounts(val paidIn: String, val out: String, val net: String, val netColor: Color, val spoken: Spoken) {
    companion object {
        /** Live up, Danger down, Chalk even or not counted. */
        fun colorOf(net: Long?): Color = when {
            net == null || net == 0L -> PokerColors.Chalk
            net > 0L -> PokerColors.Live
            else -> PokerColors.Danger
        }
    }
}

/** One player's line in the ledger, as text. */
private class LedgerLine(
    val playerId: Int,
    val name: String,
    /** "2 top-ups", or null. */
    val micro: String?,
    val amounts: LedgerAmounts,
    /** What a tap on the line does, for TalkBack. */
    val openLabel: String = "",
)

/** The column titles, in capitals. */
private class LedgerHeaders(val player: String, val paidIn: String, val out: String, val net: String)

/**
 * The ledger (S13): Player, In, Out and Net, a line per player, then the total. Each line opens
 * the player's sheet (top up, count their chips). Net carries its sign as well as its colour. When
 * the four columns don't fit (small phones at large font sizes) each player takes two lines: the
 * name, then In, Out and Net, which wrap.
 */
@Composable
internal fun LedgerCard(state: CashUiState, onIntent: (CashIntent) -> Unit) {
    val lines = ledgerLines(state)
    val settled = state.settlement is CashSettlement.Settled
    val total = LedgerLine(
        playerId = 0,
        name = stringResource(R.string.cash_total),
        micro = null,
        amounts = LedgerAmounts(
            paidIn = ledgerAmount(state.ledger.cashInCents),
            out = ledgerAmount(state.ledger.countedOutCents),
            net = if (settled) ledgerAmount(0L) else stringResource(R.string.cash_not_counted),
            netColor = PokerColors.Chalk,
            spoken = Spoken(
                paidIn = stringResource(R.string.cash_cell_in, formatMoney(state.ledger.cashInCents)),
                out = stringResource(R.string.cash_cell_out, formatMoney(state.ledger.countedOutCents)),
                net = null,
            ),
        ),
    )
    CashCard(spacing = 0.dp) {
        LedgerTable(lines, total, onOpen = { onIntent(CashIntent.OpenPlayer(it)) })
        CashNote(stringResource(R.string.cash_ledger_hint), Modifier.padding(top = 8.dp, bottom = 4.dp))
        if (state.canAddPlayer) {
            PokerButton(
                text = stringResource(R.string.cash_add_player),
                onClick = { onIntent(CashIntent.ShowAddPlayer) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Plus,
            )
        }
    }
}

@Composable
private fun ledgerLines(state: CashUiState): List<LedgerLine> {
    val notCounted = stringResource(R.string.cash_not_counted)
    val openLabel = stringResource(R.string.cash_row_open)
    return state.players.map { player ->
        val net = state.netFor(player)
        LedgerLine(
            playerId = player.id,
            name = player.name,
            micro = player.topUps.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.cash_top_ups, it, it) },
            amounts = LedgerAmounts(
                paidIn = ledgerAmount(player.inCents),
                out = player.cashOutCents?.let { ledgerAmount(it) } ?: notCounted,
                net = net?.let { ledgerAmount(it, signed = true) } ?: notCounted,
                netColor = LedgerAmounts.colorOf(net),
                spoken = spoken(player, net),
            ),
            openLabel = openLabel,
        )
    }
}

/** "in $40", "out $112", "up $72"; before the count, "not counted yet" and nothing for the net. */
@Composable
private fun spoken(player: CashPlayer, net: Long?): Spoken {
    val out = player.cashOutCents
    return Spoken(
        paidIn = stringResource(R.string.cash_cell_in, formatMoney(player.inCents)),
        out = if (out != null) {
            stringResource(R.string.cash_cell_out, formatMoney(out))
        } else {
            stringResource(R.string.cash_cell_not_counted)
        },
        net = when {
            net == null || out == null -> null
            net > 0L -> stringResource(R.string.cash_cell_up, formatMoney(net))
            net < 0L -> stringResource(R.string.cash_cell_down, formatMoney(-net))
            else -> stringResource(R.string.cash_cell_even)
        },
    )
}

/** The widths of the In, Out and Net columns. */
private class Columns(val paidIn: Dp, val out: Dp, val net: Dp) {
    /** All three, with the gaps between the four columns. */
    val total: Dp get() = paidIn + out + net + ColumnGap * GAPS

    private companion object {
        const val GAPS = 3
    }
}

@Composable
private fun LedgerTable(lines: List<LedgerLine>, total: LedgerLine, onOpen: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headers = LedgerHeaders(
        player = stringResource(R.string.cash_col_player).uppercase(),
        paidIn = stringResource(R.string.cash_col_in).uppercase(),
        out = stringResource(R.string.cash_col_out).uppercase(),
        net = stringResource(R.string.cash_col_net).uppercase(),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        fun widest(texts: List<String>, style: TextStyle): Dp = with(density) {
            texts.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }?.toDp() ?: 0.dp
        }
        fun column(values: List<String>, header: String, minimum: Dp) =
            maxOf(widest(values, LedgerNumber), widest(listOf(header), LedgerHeader), minimum) + Slack
        val amounts = (lines + total).map { it.amounts }
        val columns = Columns(
            paidIn = column(amounts.map { it.paidIn }, headers.paidIn, NumberMin),
            out = column(amounts.map { it.out }, headers.out, NumberMin),
            net = column(amounts.map { it.net }, headers.net, NetMin),
        )
        // The name column must hold its longest word, so no name breaks mid-word.
        val words = (lines.flatMap { it.name.split(" ") } + headers.player).filter { it.isNotBlank() }
        val microWords = lines.mapNotNull { it.micro }.flatMap { it.split(' ') }
        val nameMinimum = maxOf(widest(words, LedgerName), widest(microWords, LedgerMicro), NameMin) + Slack
        val fits = nameMinimum + columns.total <= maxWidth
        Column(Modifier.fillMaxWidth()) {
            if (fits) {
                HeaderRow(headers, columns)
                lines.forEach { line -> TableRow(line, columns, onOpen, rule = true) }
                TableRow(total, columns, onOpen = null, rule = true)
            } else {
                lines.forEachIndexed { index, line -> StackedRow(line, headers, onOpen, rule = index > 0) }
            }
        }
    }
}

@Composable
private fun HeaderRow(headers: LedgerHeaders, columns: Columns) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(headers.player, style = LedgerHeader, color = PokerColors.Chalk, modifier = Modifier.weight(1f))
        val cells = listOf(headers.paidIn to columns.paidIn, headers.out to columns.out, headers.net to columns.net)
        cells.forEach { (text, width) ->
            Text(
                text = text,
                style = LedgerHeader,
                color = PokerColors.Chalk,
                textAlign = TextAlign.End,
                modifier = Modifier.width(width),
            )
        }
    }
}

/** A player's line ([onOpen] set) or the total line (Chalk, not tappable). */
@Composable
private fun TableRow(line: LedgerLine, columns: Columns, onOpen: ((Int) -> Unit)?, rule: Boolean) {
    val isTotal = onOpen == null
    val ink = if (isTotal) PokerColors.Chalk else PokerColors.CardWhite
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rule) Modifier.topRule() else Modifier)
            .then(if (onOpen != null) Modifier.openable(line, onOpen) else Modifier)
            .heightIn(min = RowHeight)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, style = if (isTotal) MaterialTheme.typography.bodyMedium else LedgerName, color = ink)
            if (line.micro != null) Text(line.micro, style = LedgerMicro, color = PokerColors.Chalk)
        }
        val amounts = line.amounts
        Number(amounts.paidIn, amounts.spoken.paidIn, ink, Modifier.width(columns.paidIn), total = isTotal)
        Number(amounts.out, amounts.spoken.out, ink, Modifier.width(columns.out), total = isTotal)
        val netInk = if (isTotal) ink else amounts.netColor
        Number(amounts.net, amounts.spoken.net, netInk, Modifier.width(columns.net), total = isTotal)
    }
}

/** An amount, read as [spoken] ("in $40"); with no [spoken] (a dash) TalkBack skips it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Number(text: String, spoken: String?, color: Color, modifier: Modifier, total: Boolean = false) {
    Text(
        text = text,
        style = if (total) LedgerNumber.copy(fontWeight = FontWeight.Medium) else LedgerNumber,
        color = color,
        textAlign = TextAlign.End,
        softWrap = false,
        modifier = modifier.semantics {
            if (spoken != null) contentDescription = spoken else invisibleToUser()
        },
    )
}

/** Two lines a player: the name, then In, Out and Net (which wrap at large font sizes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackedRow(line: LedgerLine, headers: LedgerHeaders, onOpen: (Int) -> Unit, rule: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rule) Modifier.topRule() else Modifier)
            .openable(line, onOpen)
            .heightIn(min = RowHeight)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(line.name, style = LedgerName, color = PokerColors.CardWhite)
        if (line.micro != null) Text(line.micro, style = LedgerMicro, color = PokerColors.Chalk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val amounts = line.amounts
            Stat(headers.paidIn) { Number(amounts.paidIn, amounts.spoken.paidIn, PokerColors.CardWhite, Modifier) }
            Stat(headers.out) { Number(amounts.out, amounts.spoken.out, PokerColors.CardWhite, Modifier) }
            Stat(headers.net) { Number(amounts.net, amounts.spoken.net, amounts.netColor, Modifier) }
        }
    }
}

/** "IN 40": the label is for the eye; TalkBack hears the amount's own "in $40". */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Stat(label: String, value: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LedgerHeader, color = PokerColors.Chalk, modifier = Modifier.semantics { invisibleToUser() })
        value()
    }
}

/** The whole line opens the player's sheet; the click merges it into one button for TalkBack. */
private fun Modifier.openable(line: LedgerLine, onOpen: (Int) -> Unit): Modifier =
    clickable(onClickLabel = line.openLabel, role = Role.Button) { onOpen(line.playerId) }

private val LedgerHeader = PokerType.Eyebrow.copy(fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
private val LedgerNumber = PokerType.NumberM.copy(fontSize = 18.sp, lineHeight = 22.sp)
private val LedgerName = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
private val LedgerMicro = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
private val RowHeight = 48.dp
private val ColumnGap = 8.dp
private val NumberMin = 46.dp
private val NetMin = 58.dp
private val NameMin = 64.dp
private val Slack = 2.dp
