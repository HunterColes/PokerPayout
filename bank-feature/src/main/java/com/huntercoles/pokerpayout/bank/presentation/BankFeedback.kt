package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * What the Bank says after an action ("Rita is out in 8th · bounty to Marcus"), on the app's one
 * snackbar, with Undo.
 */
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

    /** [eliminator] null: nobody was credited. [hasBounty] false: there is no bounty to mention. */
    fun knockout(name: String, place: Int, eliminator: String?, hasBounty: Boolean): String = when {
        !hasBounty -> context.getString(R.string.bank_done_knockout, name, ordinalOf(place))
        eliminator == null -> context.getString(R.string.bank_done_knockout_unclaimed, name, ordinalOf(place))
        else -> context.getString(R.string.bank_done_knockout_bounty, name, ordinalOf(place), eliminator)
    }

    fun backIn(name: String): String = context.getString(R.string.bank_done_back_in, name)

    fun paid(name: String, cents: Long): String =
        context.getString(R.string.bank_done_paid, name, FormatUtils.formatMoney(cents))

    fun unpaid(name: String): String = context.getString(R.string.bank_done_unpaid, name)
}
