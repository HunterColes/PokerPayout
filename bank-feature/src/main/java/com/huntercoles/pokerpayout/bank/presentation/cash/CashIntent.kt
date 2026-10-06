package com.huntercoles.pokerpayout.bank.presentation.cash

import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer

/**
 * What the cash game can be asked to do. Every change to the ledger applies at once and can be
 * undone ([Undo], or the snackbar), the clear included; renaming and switching mode aren't changes
 * to the money and aren't undone.
 */
sealed interface CashIntent {
    /** The Bank's Tournament / Cash game switch. Both games keep their state. */
    data class SwitchMode(val mode: BankMode) : CashIntent

    data object ShowAddPlayer : CashIntent

    /** A new player, buying in for [buyInCents]; a blank name becomes "Player N". */
    data class AddPlayer(val name: String, val buyInCents: Long) : CashIntent

    /** The player's sheet: name, buy-ins and top-ups, count at the end. */
    data class OpenPlayer(val playerId: Int) : CashIntent

    /** Rename; blank goes back to what it was. */
    data class Rename(val playerId: Int, val name: String) : CashIntent

    /** One more buy-in for [playerId]. */
    data class TopUp(val playerId: Int, val amountCents: Long) : CashIntent

    /** Removes one of [playerId]'s buy-ins (one recorded by mistake); the last one stays. */
    data class RemoveBuyIn(val playerId: Int, val index: Int) : CashIntent

    /** What [playerId]'s chips were worth at the end; null: not counted yet. */
    data class SetCashOut(val playerId: Int, val amountCents: Long?) : CashIntent

    data class RemovePlayer(val playerId: Int) : CashIntent

    /** The chip check is off: share the difference across the stacks instead of recounting. */
    data object SplitDifference : CashIntent

    /** Takes the split back: recount instead. */
    data object Recount : CashIntent

    /** Ticks one settle-up payment as paid, or not. */
    data class SetPaid(val transfer: CashTransfer, val paid: Boolean) : CashIntent

    /** Clears the cash game for a new night (Undo brings it back). */
    data object ClearGame : CashIntent

    data object DismissSheet : CashIntent

    /** Takes back the newest change (up to the last 20). */
    data object Undo : CashIntent
}
