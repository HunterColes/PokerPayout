package com.huntercoles.pokerpayout.bank.presentation.cash

import android.content.res.Resources
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import kotlin.math.absoluteValue

/**
 * The settle-up as plain text, for the group chat (Share, `ACTION_SEND` text/plain; no permission):
 * ```
 * Poker night: cash game
 * Cash in $260 · counted out $260 · balanced
 *
 * Dana: in $40, out $112, up $72
 * Sam: in $20, out $0, down $20
 *
 * Settle up, 5 payments:
 * Theo pays Dana $47
 * ```
 * Null until there is a settle-up to share (every chip counted, and the count balanced or split).
 */
object CashShareText {
    fun build(resources: Resources, state: CashUiState): String? {
        val settled = state.settlement as? CashSettlement.Settled ?: return null
        val ledger = state.ledger
        val cashIn = formatMoney(ledger.cashInCents)
        val countedOut = formatMoney(ledger.countedOutCents)
        val header = if (settled.splitCents == 0L) {
            resources.getString(R.string.cash_share_balanced, cashIn, countedOut)
        } else {
            resources.getString(R.string.cash_share_split, cashIn, countedOut, formatMoney(settled.splitCents.absoluteValue))
        }
        val players = ledger.players.map { player ->
            val net = settled.netsCents[player.id] ?: 0L
            val counted = player.cashOutCents ?: 0L
            val adjustment = settled.adjustmentFor(player.id)
            val out = if (adjustment == 0L) {
                formatMoney(counted)
            } else {
                resources.getString(R.string.cash_share_out_split, formatMoney(counted), formatMoney(counted + adjustment))
            }
            val paidIn = formatMoney(player.inCents)
            when {
                net > 0L -> resources.getString(R.string.cash_share_up, player.name, paidIn, out, formatMoney(net))
                net < 0L -> resources.getString(R.string.cash_share_down, player.name, paidIn, out, formatMoney(-net))
                else -> resources.getString(R.string.cash_share_even, player.name, paidIn, out)
            }
        }
        val payments = if (settled.transfers.isEmpty()) {
            listOf(resources.getString(R.string.cash_share_nobody_pays))
        } else {
            listOf(resources.getQuantityString(R.plurals.cash_share_settle, settled.transfers.size, settled.transfers.size)) +
                settled.transfers.map { transfer ->
                    resources.getString(
                        if (state.isPaid(transfer)) R.string.cash_share_payment_paid else R.string.cash_share_payment,
                        state.nameOf(transfer.fromId),
                        state.nameOf(transfer.toId),
                        formatMoney(transfer.amountCents),
                    )
                }
        }
        return (listOf(resources.getString(R.string.cash_share_title), header, "") + players + "" + payments)
            .joinToString("\n")
    }
}
