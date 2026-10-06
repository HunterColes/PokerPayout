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
 * - Progressive and mystery bounties (PP-035) follow their own rules ([BountyLedger]); every bounty
 *   still ends with exactly one player.
 * - Rebuys and add-ons count at the price each was bought at (PP-085), in the pool and in what each
 *   player paid, so changing the rebuy amount mid-game doesn't re-value earlier rebuys.
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
        val standings = Standings(players.map { it.id }, eliminationOrder)
        val pool = PoolBreakdown.withRecordedPurchases(
            money = money,
            playerCount = players.size,
            rebuyCents = players.sumOf { it.rebuyCostCents(money) },
            addOnCents = players.sumOf { it.addOnCostCents(money) }
        )
        val table = calculatePayouts(pool.prizePoolCents, weights, players.size, rounding)
        val champion = standings.championId
        val bounties = BountyLedger.of(players, standings, money)

        val settlements = players.map { player ->
            val place = standings.placeOf(player.id)
            val isChampion = player.id == champion
            val rebuyCost = player.rebuyCostCents(money)
            val addOnCost = player.addOnCostCents(money)
            PlayerSettlement(
                playerId = player.id,
                place = place,
                prizeCents = place?.let { table.amountFor(it) } ?: 0L,
                knockouts = bounties.knockouts[player.id] ?: 0,
                knockoutBountyCents = bounties.knockoutCents[player.id] ?: 0L,
                kingsBountyCents = if (isChampion) bounties.championCents else 0L,
                unclaimedBountyCents = if (isChampion) bounties.unclaimedCents else 0L,
                costCents = money.entryCents + rebuyCost + addOnCost,
                paidOut = player.paidOut,
                rebuyCostCents = rebuyCost,
                addOnCostCents = addOnCost,
                headBountyCents = bounties.heads[player.id] ?: 0L
            )
        }

        val entriesPaid = players.count { it.boughtIn } * money.entryCents
        return Settlement(
            pool = pool,
            payoutTable = table,
            standings = standings,
            players = settlements,
            paidInCents = entriesPaid + pool.rebuyCents + pool.addOnCents,
            bountyMode = money.bountyMode,
            envelopesLeft = bounties.envelopesLeft
        )
    }
}
