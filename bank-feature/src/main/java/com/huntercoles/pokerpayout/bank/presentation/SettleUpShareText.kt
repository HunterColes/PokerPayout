package com.huntercoles.pokerpayout.bank.presentation

import android.content.res.Resources
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney

/**
 * The settle-up as plain text, for the group chat (Share, `ACTION_SEND` text/plain; no permission),
 * as the cash game's was:
 * ```
 * Poker night: settle up
 * Dana: in $50, won $245, up $195
 * Sam: in $25, won $0, down $25
 * Food $45, kept by the bank.
 *
 * Settle up, 3 payments:
 * Sam pays Dana $25
 * The bank pays Dana $170 (paid)
 * ```
 * Null until the night is over.
 */
object SettleUpShareText {
    fun build(resources: Resources, state: BankUiState): String? {
        val settleUp = state.settleUp ?: return null
        val players = settleUp.nights.map { night ->
            val paidIn = formatMoney(night.inCents)
            val won = formatMoney(night.wonCents)
            when {
                night.netCents > 0L ->
                    resources.getString(R.string.bank_settle_share_up, night.name, paidIn, won, formatMoney(night.netCents))
                night.netCents < 0L ->
                    resources.getString(R.string.bank_settle_share_down, night.name, paidIn, won, formatMoney(-night.netCents))
                else -> resources.getString(R.string.bank_settle_share_even, night.name, paidIn, won)
            }
        }
        val food = listOfNotNull(
            settleUp.foodCents.takeIf { it > 0L }?.let { resources.getString(R.string.bank_settle_share_food, formatMoney(it)) }
        )
        return (listOf(resources.getString(R.string.bank_settle_share_title)) + players + food + "" + payments(resources, state))
            .joinToString("\n")
    }

    private fun payments(resources: Resources, state: BankUiState): List<String> {
        val transfers = state.settleUp?.transfers.orEmpty()
        if (transfers.isEmpty()) return listOf(resources.getString(R.string.bank_settle_share_square))
        val names = state.players.associate { it.id to it.name }
        fun name(id: Int, first: Boolean): String = when {
            id != SettleUp.BANK_ID -> names[id].orEmpty()
            first -> resources.getString(R.string.bank_settle_the_bank_first)
            else -> resources.getString(R.string.bank_settle_the_bank)
        }
        val header = resources.getQuantityString(R.plurals.bank_settle_share_payments, transfers.size, transfers.size)
        return listOf(header) + transfers.map { transfer ->
            resources.getString(
                if (state.isPaid(transfer)) R.string.bank_settle_share_payment_paid else R.string.bank_settle_share_payment,
                name(transfer.fromId, first = true),
                name(transfer.toId, first = false),
                formatMoney(transfer.amountCents),
            )
        }
    }
}
