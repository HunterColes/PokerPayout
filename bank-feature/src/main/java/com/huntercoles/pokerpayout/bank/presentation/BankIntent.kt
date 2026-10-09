package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.settle.Transfer

/**
 * What the Bank can be asked to do. Routine actions apply at once and can be undone ([Undo], or the
 * snackbar); only Reset asks first.
 */
sealed interface BankIntent {
    // Rows ----------------------------------------------------------------------------------------

    /** Rename; blank goes back to "Player N". Not an undoable action. */
    data class PlayerNameChanged(val playerId: Int, val name: String) : BankIntent

    /** Buy-in cell: paid, or not paid any more. */
    data class BuyInToggled(val playerId: Int) : BankIntent

    /** Tap Rebuy or Add-on: one more, at today's price, while the column is open. */
    data class AddPurchase(val playerId: Int, val kind: Purchase) : BankIntent

    /** Hold Rebuy or Add-on: the count sheet. */
    data class OpenCount(val playerId: Int, val kind: Purchase) : BankIntent

    /** The count sheet's answer: removes the newest first, adds at today's price. */
    data class SetCount(val playerId: Int, val kind: Purchase, val count: Int) : BankIntent

    /** Out cell of a player still in: the knockout sheet (S5b). */
    data class OpenKnockout(val playerId: Int) : BankIntent

    /** The knockout sheet's answer: applies at once, crediting [eliminatorId] (null: nobody). */
    data class KnockOut(val playerId: Int, val eliminatorId: Int?) : BankIntent

    /** Out cell of a player who is out (the place disc): back in the game. */
    data class BringBack(val playerId: Int) : BankIntent

    /** Paid cell: the pay-out sheet (S5c). */
    data class OpenPayOut(val playerId: Int) : BankIntent

    /** The pay-out sheet's answer. */
    data class SetPaid(val playerId: Int, val paid: Boolean) : BankIntent

    // Late entries and re-entries (PP-116) ---------------------------------------------------------

    /** Late entry, under the list (or Re-entry on the clock's knockout panel): the late entry sheet (S27). */
    data object OpenLateEntry : BankIntent

    /**
     * A player who arrived late joins as a new entry: [name] (blank: "Player N"), buy-in paid at
     * today's price, a starting stack. While late entry is open; Undo takes it back.
     */
    data class AddLateEntry(val name: String) : BankIntent

    /**
     * [playerId], who is out, buys back in as a new entry: a new stack and a new buy-in, paid at
     * today's price. Their knockout stays where it was. While late entry is open; Undo takes it back.
     */
    data class ReEnter(val playerId: Int) : BankIntent

    // The rest of the screen ----------------------------------------------------------------------

    data object ShowPoolBreakdown : BankIntent

    data object ShowPayoutStructure : BankIntent

    data class UpdatePayoutSettings(val settings: PayoutSettings) : BankIntent

    /** The settle-up sheet, once the night is over. */
    data object ShowSettleUp : BankIntent

    /**
     * Ticks one settle-up payment as paid, or not. Ticking the last one records everyone square:
     * every buy-in and every payout marked paid, in one action Undo takes back.
     */
    data class SetSettlePaid(val transfer: Transfer, val paid: Boolean) : BankIntent

    data object ShowResetConfirm : BankIntent

    data object ConfirmReset : BankIntent

    /** Closes whichever sheet is open. */
    data object DismissSheet : BankIntent

    /** Takes back the newest action (up to the last 20). */
    data object Undo : BankIntent

    /** The top bar's bell: the clock's chime on or off. */
    data object ToggleMute : BankIntent
}
