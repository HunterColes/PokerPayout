package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What a knockout pays the player credited with it, as the snackbar says it (PP-035). */
sealed interface KnockoutPay {
    /** The game has no bounty. */
    data object NoBounty : KnockoutPay

    /** The knocked-out player's whole bounty (standard), or nothing yet (a mystery pool with no envelopes). */
    data object Bounty : KnockoutPay

    /**
     * Progressive: [cashCents] now, and the eliminator's bounty grows to [newBountyCents]; null when
     * the eliminator is already out, and so takes the whole bounty in cash.
     */
    data class Progressive(val cashCents: Long, val newBountyCents: Long?) : KnockoutPay

    /** Mystery: the envelope drawn, worth [cents]. */
    data class Mystery(val cents: Long) : KnockoutPay
}

/**
 * What the Bank says after an action ("Rita is out in 8th · bounty to Marcus"), on the app's one
 * snackbar, with Undo.
 */
@Suppress("TooManyFunctions") // one small function per message
class BankFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snackbars: SnackbarController
) {
    /** Shows [message] with Undo; true if the player pressed Undo before it went away. */
    suspend fun showUndo(message: String): Boolean =
        snackbars.showUndo(message, context.getString(R.string.bank_undo))

    fun buyIn(name: String, cents: Long): String =
        context.getString(R.string.bank_done_buy_in, name, FormatUtils.formatMoney(cents))

    fun buyInCleared(name: String): String = context.getString(R.string.bank_done_buy_in_cleared, name)

    fun purchase(kind: Purchase, name: String, cents: Long): String = context.getString(
        if (kind == Purchase.REBUY) R.string.bank_done_rebuy else R.string.bank_done_add_on,
        name,
        FormatUtils.formatMoney(cents)
    )

    fun count(kind: Purchase, name: String, count: Int): String = context.resources.getQuantityString(
        if (kind == Purchase.REBUY) R.plurals.bank_done_rebuys_set else R.plurals.bank_done_add_ons_set,
        count,
        name,
        count
    )

    /**
     * "Rita is out in 8th · bounty to Marcus". [eliminator] null: nobody was credited. [pay]: what
     * the knockout pays (PP-035), or [KnockoutPay.NoBounty] when there is no bounty to mention.
     */
    fun knockout(name: String, place: Int, eliminator: String?, pay: KnockoutPay): String = when {
        pay == KnockoutPay.NoBounty -> context.getString(R.string.bank_done_knockout, name, ordinalOf(place))
        eliminator == null -> context.getString(R.string.bank_done_knockout_unclaimed, name, ordinalOf(place))
        else -> credited(name, ordinalOf(place), eliminator, pay)
    }

    /** "… · Marcus takes $2.50, bounty now $7.50" (progressive), "… · Marcus draws $20" (mystery). */
    private fun credited(name: String, place: String, eliminator: String, pay: KnockoutPay): String = when (pay) {
        is KnockoutPay.Progressive -> if (pay.newBountyCents != null) {
            context.getString(
                R.string.bank_done_knockout_pko,
                name,
                place,
                eliminator,
                FormatUtils.formatMoney(pay.cashCents),
                FormatUtils.formatMoney(pay.newBountyCents)
            )
        } else {
            val cash = FormatUtils.formatMoney(pay.cashCents)
            context.getString(R.string.bank_done_knockout_pko_all, name, place, eliminator, cash)
        }
        is KnockoutPay.Mystery ->
            context.getString(R.string.bank_done_knockout_mystery, name, place, eliminator, FormatUtils.formatMoney(pay.cents))
        else -> context.getString(R.string.bank_done_knockout_bounty, name, place, eliminator)
    }

    fun backIn(name: String): String = context.getString(R.string.bank_done_back_in, name)

    /** "Sam joins late · $50 paid" (PP-116). */
    fun lateEntry(name: String, cents: Long): String =
        context.getString(R.string.bank_done_late_entry, name, FormatUtils.formatMoney(cents))

    /** "Rita re-enters · $50 paid" (PP-116). */
    fun reEntry(name: String, cents: Long): String =
        context.getString(R.string.bank_done_re_entry, name, FormatUtils.formatMoney(cents))

    fun paid(name: String, cents: Long): String =
        context.getString(R.string.bank_done_paid, name, FormatUtils.formatMoney(cents))

    fun unpaid(name: String): String = context.getString(R.string.bank_done_unpaid, name)

    /** "Sam paid Dana $45", "The bank paid Dana $45", "Sam hasn't paid the bank yet"; null is the Bank. */
    fun settlePayment(from: String?, to: String?, cents: Long, paid: Boolean): String {
        val payer = from ?: context.getString(R.string.bank_settle_the_bank_first)
        val payee = to ?: context.getString(R.string.bank_settle_the_bank)
        return if (paid) {
            context.getString(R.string.bank_done_settle_paid, payer, payee, FormatUtils.formatMoney(cents))
        } else {
            context.getString(R.string.bank_done_settle_unpaid, payer, payee)
        }
    }

    fun square(): String = context.getString(R.string.bank_done_square)
}
