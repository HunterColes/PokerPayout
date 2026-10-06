package com.huntercoles.pokerpayout.bank.presentation.cash

import android.content.Context
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.utils.FormatUtils.formatMoney
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.absoluteValue

/** What the cash game says after a change ("Dana topped up · $20"), on the app's snackbar, with Undo. */
class CashFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snackbars: SnackbarController,
) {
    /** Shows [message] with Undo; true if the player pressed Undo before it went away. */
    suspend fun showUndo(message: String): Boolean = snackbars.showUndo(message, context.getString(R.string.cash_undo))

    fun defaultName(seat: Int): String = context.getString(R.string.cash_default_name, seat)

    fun boughtIn(name: String, cents: Long): String = context.getString(R.string.cash_done_bought_in, name, formatMoney(cents))

    fun toppedUp(name: String, cents: Long): String = context.getString(R.string.cash_done_topped_up, name, formatMoney(cents))

    fun buyInRemoved(name: String, cents: Long): String =
        context.getString(R.string.cash_done_buy_in_removed, name, formatMoney(cents))

    fun cashedOut(name: String, cents: Long?): String = if (cents == null) {
        context.getString(R.string.cash_done_count_cleared, name)
    } else {
        context.getString(R.string.cash_done_cashed_out, name, formatMoney(cents))
    }

    fun playerRemoved(name: String): String = context.getString(R.string.cash_done_player_removed, name)

    /** The difference split, or (null) the split taken back. */
    fun split(differenceCents: Long?): String = if (differenceCents == null) {
        context.getString(R.string.cash_done_recount)
    } else {
        context.getString(R.string.cash_done_split, formatMoney(differenceCents.absoluteValue))
    }

    fun paid(from: String, to: String, cents: Long, paid: Boolean): String = if (paid) {
        context.getString(R.string.cash_done_paid, from, to, formatMoney(cents))
    } else {
        context.getString(R.string.cash_done_unpaid, from, to)
    }

    fun cleared(players: Int): String = context.resources.getQuantityString(R.plurals.cash_done_cleared, players, players)
}
