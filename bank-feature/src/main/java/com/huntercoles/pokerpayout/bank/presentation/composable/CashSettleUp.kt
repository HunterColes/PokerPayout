package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.cash.CashIntent
import com.huntercoles.pokerpayout.bank.presentation.cash.CashUiState
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/**
 * The settle-up (S13): who pays whom, largest debts first, each with a 48 dp tick for "paid". Until
 * every chip is counted and the chip check balances (or the difference is split), it says what it
 * is waiting for.
 */
@Composable
internal fun SettleUpCard(state: CashUiState, onIntent: (CashIntent) -> Unit) {
    val settlement = state.settlement
    CashCard(spacing = 0.dp) {
        if (settlement !is CashSettlement.Settled) {
            Waiting(settlement)
            return@CashCard
        }
        val transfers = settlement.transfers
        SettledHeader(payments = transfers.size, allPaid = state.allPaid)
        if (transfers.isEmpty()) {
            CashNote(stringResource(R.string.cash_settle_even), Modifier.padding(bottom = 6.dp))
        }
        transfers.forEachIndexed { index, transfer ->
            PaymentRow(
                transfer = transfer,
                from = state.nameOf(transfer.fromId),
                to = state.nameOf(transfer.toId),
                paid = state.isPaid(transfer),
                rule = index > 0,
                onPaid = { onIntent(CashIntent.SetPaid(transfer, it)) },
            )
        }
    }
}

/** Before there is a settle-up: what it waits for. */
@Composable
private fun Waiting(settlement: CashSettlement) {
    PokerEyebrow(
        text = stringResource(R.string.cash_settle_title_plain),
        color = PokerColors.PokerGold,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .semantics { heading() },
    )
    val reason = when (settlement) {
        is CashSettlement.Counting -> R.string.cash_settle_waiting_count
        is CashSettlement.Unbalanced -> R.string.cash_settle_waiting_check
        else -> R.string.cash_settle_waiting_empty
    }
    CashNote(stringResource(reason), Modifier.padding(bottom = 4.dp))
}

/** "SETTLE UP · 5 PAYMENTS", with the hint to tick them, or "All paid" once they are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettledHeader(payments: Int, allPaid: Boolean) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PokerEyebrow(
            text = pluralStringResource(R.plurals.cash_settle_title, payments, payments),
            color = PokerColors.PokerGold,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .semantics { heading() },
        )
        when {
            allPaid -> PokerPill(
                text = stringResource(R.string.cash_settle_all_paid),
                tone = PokerPillTone.Live,
                icon = PokerIcons.Check,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            payments > 0 -> Text(
                text = stringResource(R.string.cash_settle_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** "Theo pays Dana   $47   [✓]": the whole row toggles; TalkBack reads it as one checkbox. */
@Suppress("LongParameterList") // the payment, its two names, its state and its action
@Composable
private fun PaymentRow(
    transfer: CashTransfer,
    from: String,
    to: String,
    paid: Boolean,
    rule: Boolean,
    onPaid: (Boolean) -> Unit,
) {
    val line = payerLine(stringResource(R.string.cash_settle_line), from, to)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rule) Modifier.topRule() else Modifier)
            // One checkbox: the toggle merges the row, read as "Theo pays Dana, $47, checked"
            .toggleable(value = paid, role = Role.Checkbox, onValueChange = onPaid)
            .heightIn(min = 52.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(transfer.amountCents),
            style = PokerType.NumberM.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = PokerColors.CardWhite,
            softWrap = false,
        )
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { PaidBox(paid) }
    }
}

/** The tick: a FeltEdge outline, gold with a check once paid. */
@Composable
private fun PaidBox(paid: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (paid) {
                    Modifier.background(PokerColors.PokerGold, shape)
                } else {
                    Modifier.border(2.dp, PokerColors.FeltEdge, shape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (paid) Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.FeltDeep, modifier = Modifier.size(18.dp))
    }
}

/** "%1$s pays %2$s" with both names in bold white, the verb in Chalk. */
private fun payerLine(template: String, from: String, to: String): AnnotatedString = buildAnnotatedString {
    val names = mapOf(FROM to from, TO to to)
    var rest = template
    while (rest.isNotEmpty()) {
        val next = names.keys
            .mapNotNull { key -> rest.indexOf(key).takeIf { it >= 0 }?.let { it to key } }
            .minByOrNull { it.first }
        if (next == null) {
            append(rest)
            break
        }
        append(rest.substring(0, next.first))
        withStyle(NameSpan) { append(names.getValue(next.second)) }
        rest = rest.substring(next.first + next.second.length)
    }
}

private val NameSpan = SpanStyle(color = PokerColors.CardWhite, fontWeight = FontWeight.SemiBold)
private const val FROM = "%1\$s"
private const val TO = "%2\$s"
