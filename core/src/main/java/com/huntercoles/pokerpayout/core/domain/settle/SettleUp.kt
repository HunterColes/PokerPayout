package com.huntercoles.pokerpayout.core.domain.settle

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import javax.inject.Inject

/**
 * Who pays whom at the end of the night, so that everyone is square (it took over the cash game's
 * settle-up when that page went in 1.4).
 *
 * - [balancesCents]: what each party is still owed (positive) or still owes (negative), the players
 *   in seat order and then the Bank, under [BANK_ID]. They add up to 0.
 * - [transfers]: the fewest payments that square them ([MinimumPayments]).
 */
data class SettleUp(val balancesCents: Map<Int, Long>, val transfers: List<Transfer>) {
    /** Nobody owes anybody. */
    val isSquare: Boolean get() = transfers.isEmpty()

    companion object {
        /** The Bank itself, as a party: it holds what was paid in and pays the winners. Players start at 1. */
        const val BANK_ID = 0
    }
}

/**
 * The settle-up from what the Bank recorded, once the night is over (there is a champion, so every
 * prize and bounty has an owner).
 *
 * Each player is owed their winnings until marked paid, and owes their entry (buy-in, food and
 * bounty) until it is marked paid; rebuys and add-ons are paid when they are recorded. The Bank is
 * the party in the middle: it holds what came in, pays what went out, and keeps the food money, so
 * its balance is whatever squares the rest. When everyone paid in and the Bank pays the winners, the
 * settle-up is just "the Bank pays each winner"; when nobody paid in, the players pay each other.
 */
class SettleUpUseCase @Inject constructor() {

    /** Null until [settlement] has a champion. */
    operator fun invoke(settlement: Settlement, players: List<BankPlayer>, money: MoneySettings): SettleUp? {
        if (!settlement.isComplete) return null
        val balances = LinkedHashMap<Int, Long>()
        players.forEach { player ->
            val owed = settlement.forPlayer(player.id)?.winningsCents ?: 0L
            val toReceive = if (player.paidOut) 0L else owed
            val toPay = if (player.boughtIn) 0L else money.entryCents
            balances[player.id] = toReceive - toPay
        }
        balances[SettleUp.BANK_ID] = -balances.values.sum()
        return SettleUp(balances, MinimumPayments.of(balances))
    }
}
