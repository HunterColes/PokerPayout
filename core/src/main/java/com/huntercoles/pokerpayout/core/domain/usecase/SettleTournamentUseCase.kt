package com.huntercoles.pokerpayout.core.domain.usecase

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PlayerSettlement
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.model.Standings
import javax.inject.Inject

/**
 * Works out what every player is owed, from what the Bank recorded.
 *
 * - Places come from the elimination order ([Standings]) and pay their row of the one payout table
 *   ([CalculatePayoutsUseCase]).
 * - Each knocked-out player's bounty goes to whoever was credited with the knockout.
 * - The champion keeps their own bounty (the "King's Bounty") and also collects the bounty of every
 *   player knocked out with nobody credited, so no bounty is left in the box.
 */
class SettleTournamentUseCase @Inject constructor(
    private val calculatePayouts: CalculatePayoutsUseCase
) {

    operator fun invoke(
        players: List<BankPlayer>,
        eliminationOrder: List<Int>,
        money: MoneySettings,
        weights: List<Int>,
        rounding: PayoutRounding
    ): Settlement {
        val ids = players.map { it.id }
        val standings = Standings(ids, eliminationOrder)
        val pool = PoolBreakdown.of(
            money = money,
            playerCount = players.size,
            rebuyCount = players.sumOf { it.rebuys },
            addOnCount = players.sumOf { it.addOns }
        )
        val table = calculatePayouts(pool.prizePoolCents, weights, players.size, rounding)
        val champion = standings.championId

        // The champion was never knocked out, whatever a stale record says.
        val knockedOut = standings.eliminated.toSet() - setOfNotNull(champion)
        val credits = players
            .filter { it.id in knockedOut && it.eliminatedBy != it.id && it.eliminatedBy in ids }
            .mapNotNull { it.eliminatedBy }
            .groupingBy { it }
            .eachCount()
        val unclaimed = knockedOut.size - credits.values.sum()

        val settlements = players.map { player ->
            val place = standings.placeOf(player.id)
            val knockouts = credits[player.id] ?: 0
            val isChampion = player.id == champion
            PlayerSettlement(
                playerId = player.id,
                place = place,
                prizeCents = place?.let { table.amountFor(it) } ?: 0L,
                knockouts = knockouts,
                knockoutBountyCents = knockouts * money.bountyCents,
                kingsBountyCents = if (isChampion) money.bountyCents else 0L,
                unclaimedBountyCents = if (isChampion) unclaimed * money.bountyCents else 0L,
                costCents = money.entryCents + player.rebuys * money.rebuyCents + player.addOns * money.addOnCents,
                paidOut = player.paidOut
            )
        }

        val entriesPaid = players.count { it.boughtIn } * money.entryCents
        return Settlement(
            pool = pool,
            payoutTable = table,
            standings = standings,
            players = settlements,
            paidInCents = entriesPaid + pool.rebuyCents + pool.addOnCents
        )
    }
}
