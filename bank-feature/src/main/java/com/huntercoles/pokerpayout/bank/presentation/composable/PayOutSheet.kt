package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankSheet
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/** The pay-out sheet (S5c) as a modal bottom sheet. */
@Composable
internal fun PayOutSheet(sheet: BankSheet.PayOut, onSetPaid: (Boolean) -> Unit, onDismiss: () -> Unit) {
    PokerSheet(onDismissRequest = onDismiss) {
        PayOutSheetContent(sheet, onSetPaid, onDismiss)
    }
}

/**
 * What to hand [BankSheet.PayOut.name], line for line as the old Pay-Out breakdown: the place prize,
 * knockout bounties, the King's Bounty and the unclaimed bounties (PP-055), then the one number the
 * host needs, **Hand over**. What they paid in and their net for the night are said in words, so a
 * negative net no longer reads like an amount to pay.
 */
@Composable
internal fun PayOutSheetContent(sheet: BankSheet.PayOut, onSetPaid: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val owed = sheet.owed
    val paid = owed.paidOut
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        SheetHeading(title = stringResource(R.string.bank_pay_title, sheet.name)) { StandingPill(sheet) }
        Column {
            val lines = breakdownLines(sheet)
            lines.forEachIndexed { index, (label, cents) ->
                if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
                BreakdownLine(label, formatMoney(cents))
            }
            if (lines.isNotEmpty()) HorizontalDivider(color = PokerColors.FeltLine)
            if (owed.winningsCents > 0L) {
                BreakdownLine(
                    label = stringResource(R.string.bank_pay_hand_over),
                    amount = formatMoney(owed.winningsCents),
                    total = true,
                )
            } else {
                Text(
                    text = stringResource(R.string.bank_pay_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.Chalk,
                )
            }
        }
        Text(text = paidInAndNet(sheet), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        if (paid) {
            SheetButtons(
                dismissLabel = stringResource(R.string.bank_close),
                onDismiss = onDismiss,
                confirmLabel = stringResource(R.string.bank_pay_mark_unpaid),
                onConfirm = { onSetPaid(false) },
                confirmVariant = PokerButtonVariant.DestructiveOutline,
            )
        } else {
            SheetButtons(
                dismissLabel = stringResource(R.string.bank_cancel),
                onDismiss = onDismiss,
                confirmLabel = stringResource(R.string.bank_pay_mark_paid, formatMoney(owed.winningsCents)),
                onConfirm = { onSetPaid(true) },
                confirmIcon = PokerIcons.Check,
            )
        }
    }
}

@Composable
private fun StandingPill(sheet: BankSheet.PayOut) {
    val place = sheet.owed.place
    when {
        sheet.owed.paidOut -> PokerPill(
            stringResource(R.string.bank_pay_already_paid),
            tone = PokerPillTone.Live,
            icon = PokerIcons.Check
        )
        sheet.isChampion -> PokerPill(stringResource(R.string.bank_pay_champion), icon = PokerIcons.Crown)
        place != null -> PokerPill(stringResource(R.string.bank_pay_place, ordinalOf(place)), tone = PokerPillTone.Outline)
        else -> PokerPill(stringResource(R.string.bank_pay_still_playing), tone = PokerPillTone.Muted)
    }
}

/** The lines that hold money, in the old breakdown's order. */
@Composable
private fun breakdownLines(sheet: BankSheet.PayOut): List<Pair<String, Long>> {
    val owed = sheet.owed
    val bounty = sheet.money.bountyCents
    return buildList {
        owed.place?.takeIf { owed.prizeCents > 0L }?.let {
            add(stringResource(R.string.bank_pay_place, ordinalOf(it)) to owed.prizeCents)
        }
        if (owed.knockouts > 0 && owed.knockoutBountyCents > 0L) {
            add(stringResource(R.string.bank_pay_knockouts, owed.knockouts, formatMoney(bounty)) to owed.knockoutBountyCents)
        }
        if (owed.kingsBountyCents > 0L) {
            add(stringResource(R.string.bank_pay_kings_bounty, formatMoney(owed.kingsBountyCents)) to owed.kingsBountyCents)
        }
        if (owed.unclaimedBountyCents > 0L) {
            val count = sheet.unclaimedKnockouts.coerceAtLeast(1)
            add(pluralStringResource(R.plurals.bank_pay_unclaimed, count, count) to owed.unclaimedBountyCents)
        }
    }
}

@Composable
private fun BreakdownLine(label: String, amount: String, total: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (total) 52.dp else 38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (total) FontWeight.Medium else FontWeight.Normal),
            color = if (total) PokerColors.CardWhite else PokerColors.Chalk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = PokerType.NumberM.copy(fontSize = if (total) 30.sp else 19.sp, lineHeight = if (total) 34.sp else 22.sp),
            color = if (total) PokerColors.PokerGold else PokerColors.CardWhite,
        )
    }
}

/** "Dana paid in $60 (buy-in $40, bounty $5, food $5, add-on $10). Up $195 for the night." */
@Composable
private fun paidInAndNet(sheet: BankSheet.PayOut) = buildAnnotatedString {
    val owed = sheet.owed
    val money = sheet.money
    val parts = buildList {
        if (money.buyInCents > 0L) add(stringResource(R.string.bank_part_buy_in, formatMoney(money.buyInCents)))
        if (money.bountyCents > 0L) add(stringResource(R.string.bank_part_bounty, formatMoney(money.bountyCents)))
        if (money.foodCents > 0L) add(stringResource(R.string.bank_part_food, formatMoney(money.foodCents)))
        when {
            sheet.rebuys == 1 -> add(stringResource(R.string.bank_part_rebuy, formatMoney(owed.rebuyCostCents)))
            sheet.rebuys > 1 ->
                add(pluralStringResource(
                    R.plurals.bank_part_rebuys,
                    sheet.rebuys,
                    sheet.rebuys,
                    formatMoney(owed.rebuyCostCents)
                ))
        }
        when {
            sheet.addOns == 1 -> add(stringResource(R.string.bank_part_add_on, formatMoney(owed.addOnCostCents)))
            sheet.addOns > 1 ->
                add(pluralStringResource(
                    R.plurals.bank_part_add_ons,
                    sheet.addOns,
                    sheet.addOns,
                    formatMoney(owed.addOnCostCents)
                ))
        }
    }
    append(
        stringResource(
            R.string.bank_pay_paid_in,
            sheet.name,
            formatMoney(owed.costCents),
            parts.joinToString(stringResource(R.string.bank_part_join)),
        )
    )
    append(" ")
    val net = owed.netCents
    val (text, color) = when {
        net > 0L -> stringResource(R.string.bank_pay_net_up, formatMoney(net)) to PokerColors.Live
        net < 0L -> stringResource(R.string.bank_pay_net_down, formatMoney(-net)) to PokerColors.CardWhite
        else -> stringResource(R.string.bank_pay_net_even) to PokerColors.Chalk
    }
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) { append(text) }
}
