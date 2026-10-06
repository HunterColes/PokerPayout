package com.huntercoles.pokerpayout.core.domain.cash

/**
 * Which game the Bank shows (D4, PP-076): the tournament's bank, or the cash game's ledger. The two
 * are kept apart: neither one's actions, resets or undo reach the other.
 */
enum class BankMode { TOURNAMENT, CASH }

/**
 * Everything a cash game records: the [ledger], the settle-up payments ticked as [paid], and the
 * chip-check difference the players chose to split ([splitCents], counted out minus cash in; null
 * when nobody chose to). A split holds only for the difference it was chosen for: a recount that
 * changes the difference asks again.
 */
data class CashGame(
    val ledger: CashLedger = CashLedger(),
    val paid: Set<CashTransfer> = emptySet(),
    val splitCents: Long? = null,
) {
    /** Nothing recorded: no players, no ticks, no split. */
    val isEmpty: Boolean get() = ledger.players.isEmpty() && paid.isEmpty() && splitCents == null

    /** The id for the next player to sit down. */
    val nextPlayerId: Int get() = (ledger.players.maxOfOrNull { it.id } ?: 0) + 1

    /** True when the players chose to split exactly the difference the chip check shows now. */
    val splitChosen: Boolean
        get() = splitCents != null && (ledger.chipCheck as? ChipCheck.Off)?.differenceCents == splitCents
}
