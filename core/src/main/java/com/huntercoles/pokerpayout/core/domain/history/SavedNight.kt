package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import java.time.LocalDate

/**
 * A finished tournament night as History keeps it (PP-037): the day it was played, the name of the
 * setup it was played with, the prize pool, and every player in finishing order with what they paid
 * and what they took home. Amounts are whole cents. A saved night is never edited.
 *
 * @property id stable while the night is kept; 0 until [NightStore] gives it one.
 * @property structureName the preset the setup came from, or null when it matched none.
 * @property players 1st first; never empty.
 */
data class SavedNight(
    val id: Long,
    val date: LocalDate,
    val structureName: String?,
    val prizePoolCents: Long,
    val players: List<NightPlayer>,
) {
    init {
        require(players.isNotEmpty()) { "A night has players" }
    }

    val winner: NightPlayer get() = players.first()
}

/**
 * One player's night.
 *
 * @property entryCents what they paid to sit down: the buy-in, food and bounty.
 * @property rebuyCents their rebuys, at the prices paid; [addOnCents] the same for add-ons.
 * @property prizeCents what their place paid.
 * @property bountyCents the bounties they won: their knockouts' and, for the champion, their own and the unclaimed ones.
 */
data class NightPlayer(
    val name: String,
    val place: Int,
    val entryCents: Long,
    val rebuys: Int,
    val rebuyCents: Long,
    val addOns: Int,
    val addOnCents: Long,
    val prizeCents: Long,
    val knockouts: Int,
    val bountyCents: Long,
) {
    val paidInCents: Long get() = entryCents + rebuyCents + addOnCents
    val wonCents: Long get() = prizeCents + bountyCents
    val netCents: Long get() = wonCents - paidInCents
}

/** Tonight's results from the Bank, for History. */
object NightResults {
    /**
     * Every player in finishing order, or null until the night is over: the Bank has a champion and
     * everyone owed money (a prize or bounties) has been marked paid. [names] are the Bank's; a blank
     * one is "Player N", as everywhere else.
     */
    fun of(settlement: Settlement, bank: List<BankPlayer>, names: Map<Int, String>): List<NightPlayer>? {
        val settled = settlement.players.all { it.paidOut || it.winningsCents == 0L }
        if (!settlement.isComplete || !settled) return null
        val recorded = bank.associateBy { it.id }
        return settlement.players
            .mapNotNull { player -> player.place?.let { place -> place to player } }
            .sortedBy { (place, _) -> place }
            .map { (place, player) ->
                val purchases = recorded[player.playerId]
                NightPlayer(
                    name = names[player.playerId]?.trim().orEmpty().ifEmpty { "Player ${player.playerId}" },
                    place = place,
                    entryCents = player.costCents - player.rebuyCostCents - player.addOnCostCents,
                    rebuys = purchases?.rebuyPricesCents?.size ?: purchases?.rebuys ?: 0,
                    rebuyCents = player.rebuyCostCents,
                    addOns = purchases?.addOnPricesCents?.size ?: purchases?.addOns ?: 0,
                    addOnCents = player.addOnCostCents,
                    prizeCents = player.prizeCents,
                    knockouts = player.knockouts,
                    bountyCents = player.knockoutBountyCents + player.kingsBountyCents + player.unclaimedBountyCents,
                )
            }
            .takeIf { it.isNotEmpty() }
    }
}
