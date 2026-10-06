package com.huntercoles.pokerpayout.bank.presentation.cash

import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.domain.cash.CashGame
import com.huntercoles.pokerpayout.core.domain.cash.CashLedger
import com.huntercoles.pokerpayout.core.domain.cash.CashPlayer
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer
import com.huntercoles.pokerpayout.core.domain.cash.ChipCheck

/** At most this many players sit in one cash game. */
const val MAX_CASH_PLAYERS = 30

/** At most this many buy-ins (the first plus top-ups) per player. */
const val MAX_CASH_BUY_INS = 50

/**
 * The cash game (S13, PP-029): the ledger as recorded, and the settle-up computed from it
 * (`SettleCashUseCase`). [mode] is which game the Bank shows; the tournament's state lives in the
 * Bank's own ViewModel, untouched by anything here.
 */
data class CashUiState(
    val mode: BankMode = BankMode.TOURNAMENT,
    val game: CashGame = CashGame(),
    val settlement: CashSettlement = CashSettlement.Empty,
    /** The sheet on screen, if any. */
    val sheet: CashSheet? = null,
    /** What Undo would take back; null when there is nothing to undo. */
    val undoLabel: String? = null,
) {
    val ledger: CashLedger get() = game.ledger
    val players: List<CashPlayer> get() = ledger.players
    val chipCheck: ChipCheck get() = ledger.chipCheck
    val canUndo: Boolean get() = undoLabel != null
    val canClear: Boolean get() = !game.isEmpty
    val canAddPlayer: Boolean get() = players.size < MAX_CASH_PLAYERS

    /** The payments, once the settle-up can be worked out. */
    val transfers: List<CashTransfer> get() = (settlement as? CashSettlement.Settled)?.transfers.orEmpty()

    /** True once every payment is ticked as paid. */
    val allPaid: Boolean get() = transfers.isNotEmpty() && transfers.all { it in game.paid }

    fun isPaid(transfer: CashTransfer): Boolean = transfer in game.paid

    fun nameOf(playerId: Int): String = ledger.player(playerId)?.name.orEmpty()

    /** [player]'s result: after any split once settled, otherwise out minus in; null until counted. */
    fun netFor(player: CashPlayer): Long? = (settlement as? CashSettlement.Settled)?.netsCents?.get(player.id) ?: player.netCents

    /** Where a new player's buy-in starts: what the last player to sit down bought in for. */
    val suggestedBuyInCents: Long? get() = players.lastOrNull()?.buyInsCents?.firstOrNull()
}

/** The cash game's sheets; one at a time. */
sealed interface CashSheet {
    /** A name and a buy-in for the next player. */
    data object AddPlayer : CashSheet

    /** One player: their name, buy-ins and top-ups, and their count at the end. */
    data class Player(val playerId: Int) : CashSheet
}
