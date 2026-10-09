package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.huntercoles.pokerpayout.bank.presentation.BankUiState
import com.huntercoles.pokerpayout.bank.presentation.SettleUpModel
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/** The settle-up sheet as a modal bottom sheet. */
@Composable
internal fun SettleUpSheet(
    state: BankUiState,
    onSetPaid: (Transfer, Boolean) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settleUp = state.settleUp ?: return
    PokerSheet(onDismissRequest = onDismiss) {
        SettleUpSheetContent(settleUp, state, onSetPaid, onShare, onDismiss)
    }
}

/**
 * Who pays whom once the night is over (it took over the cash game's settle-up in 1.4): the fewest
 * payments that square everyone, largest debts first, the Bank one side of some ("The bank pays
 * Dana"), each with a 48 dp tick for paid. Ticking the last one records everyone square in the Bank.
 * Then Share as text, for the group chat.
 */
@Composable
internal fun SettleUpSheetContent(
    settleUp: SettleUpModel,
    state: BankUiState,
    onSetPaid: (Transfer, Boolean) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transfers = settleUp.transfers
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetHeading(title = stringResource(R.string.bank_settle_title)) {
            if (transfers.isEmpty()) {
                PokerPill(stringResource(R.string.bank_settle_square_pill), tone = PokerPillTone.Live, icon = PokerIcons.Check)
            }
        }
        if (transfers.isEmpty()) {
            SettleNote(stringResource(R.string.bank_settle_square))
        } else {
            SettleNote(stringResource(R.string.bank_settle_note))
            Column {
                PaymentsHeader(transfers.size)
                val names = settleNames(state)
                transfers.forEachIndexed { index, transfer ->
                    PaymentRow(
                        transfer = transfer,
                        line = names.line(transfer),
                        paid = state.isPaid(transfer),
                        rule = index > 0,
                        onPaid = { onSetPaid(transfer, it) },
                    )
                }
            }
        }
        SheetButtons(
            dismissLabel = stringResource(R.string.bank_close),
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.bank_settle_share),
            onConfirm = onShare,
            confirmVariant = PokerButtonVariant.Secondary,
            confirmIcon = PokerIcons.Share,
        )
    }
}

/** "3 PAYMENTS", with the hint to tick them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentsHeader(payments: Int) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PokerEyebrow(
            text = pluralStringResource(R.plurals.bank_settle_count, payments, payments),
            color = PokerColors.PokerGold,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .semantics { heading() },
        )
        Text(
            text = stringResource(R.string.bank_settle_hint),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

/** "Theo pays Dana   $47   [✓]": the whole row toggles; TalkBack reads it as one checkbox. */
@Composable
private fun PaymentRow(transfer: Transfer, line: AnnotatedString, paid: Boolean, rule: Boolean, onPaid: (Boolean) -> Unit) {
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
        Box(Modifier.size(PokerDimens.MinTouch), contentAlignment = Alignment.Center) { PaidBox(paid) }
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

@Composable
private fun SettleNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
}

/** A payment as a line, "Sam pays Dana": the players' names, and the Bank's ("The bank" first, "the bank" after). */
private class SettleNames(
    private val state: BankUiState,
    private val template: String,
    private val bankFirst: String,
    private val bank: String,
) {
    fun line(transfer: Transfer): AnnotatedString {
        val from = if (transfer.fromId == SettleUp.BANK_ID) bankFirst else nameOf(transfer.fromId)
        val to = if (transfer.toId == SettleUp.BANK_ID) bank else nameOf(transfer.toId)
        return payerLine(template, from, to)
    }

    private fun nameOf(playerId: Int): String = state.players.firstOrNull { it.id == playerId }?.name.orEmpty()
}

@Composable
private fun settleNames(state: BankUiState) = SettleNames(
    state = state,
    template = stringResource(R.string.bank_settle_line),
    bankFirst = stringResource(R.string.bank_settle_the_bank_first),
    bank = stringResource(R.string.bank_settle_the_bank),
)

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
