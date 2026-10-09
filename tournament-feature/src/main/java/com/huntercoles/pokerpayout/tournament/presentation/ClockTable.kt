package com.huntercoles.pokerpayout.tournament.presentation

import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.model.TableSeats
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import com.huntercoles.pokerpayout.core.preferences.TimerPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences.TournamentConfigData
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCues
import com.huntercoles.pokerpayout.tournament.domain.moments.Field

/** What the clock counts of the Bank's records: who is out, and how many rebuys and add-ons. */
internal data class BankCounts(val eliminated: List<Int> = emptyList(), val rebuys: Int = 0, val addOns: Int = 0)

/**
 * The clock's table numbers, from the Tournament and Bank settings: the players left, the average
 * stack, the pool and the places it pays (the one payout calculation; PP-135: the bubble), the
 * champion; the purchases; and the night's big moments they bring (PP-111, [ClockMoments]).
 */
internal class ClockTable(
    private val bankPreferences: BankPreferences,
    private val tableSeats: TableSeats,
    timerPreferences: TimerPreferences,
    cues: ClockCues,
) {
    /** The one payout calculation (stateless), for how many places are paid. */
    private val payouts = CalculatePayoutsUseCase()

    private val moments = ClockMoments(timerPreferences, cues, bankPreferences::getPlayerName)

    /** [state] with the table of the night set up as [config], with [bank] recorded. */
    fun refreshed(state: TimerUiState, config: TournamentConfigData, bank: BankCounts): TimerUiState {
        val players = config.numPlayers
        val outIds = bank.eliminated.filter { it in 1..players }.toSet()
        val stillIn = (1..players).filter { it !in outIds }
        val left = stillIn.size
        val stacks = players.toLong() + bank.rebuys + bank.addOns
        val chips = stacks * state.config.startingChips
        val prizePool = PoolBreakdown.withRecordedPurchases(
            config.money,
            players,
            bankPreferences.getRecordedRebuyCents(),
            bankPreferences.getRecordedAddOnCents(),
        ).prizePoolCents
        val paid = payouts(prizePool, config.payoutWeights, players, config.payoutRounding).places
        val table = TableStats(
            playerCount = players,
            playersLeft = left,
            averageStack = if (left > 0) (chips / left).toInt() else 0,
            // The same prize pool the Payouts table splits (buy-ins, rebuys and add-ons at the prices
            // they were bought at; no food or bounty)
            prizePoolCents = prizePool,
            paidPlaces = paid.size,
            championName = stillIn.singleOrNull()?.takeIf { players > 1 }?.let(bankPreferences::getPlayerName),
        )
        val purchases = Purchases(
            rebuyCents = config.money.rebuyCents,
            addOnCents = config.money.addOnCents,
            rebuysTaken = bank.rebuys,
            addOnsTaken = bank.addOns,
        )
        val field = Field(players, left, paid.size, tableSeats.seatsPerTable())
        val lowestPrize = paid.minOfOrNull { it.amountCents } ?: 0L
        return moments.after(state.copy(table = table, purchases = purchases), field, stillIn, lowestPrize)
    }
}
