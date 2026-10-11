package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.PlayerSettlement
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
     *
     * A player who re-entered (PP-116) is one line, under their first entry's name: their best place,
     * and everything their entries paid and won added together. The places stay the entries' own, so
     * the line of an entry that re-entered is not there.
     */
    fun of(settlement: Settlement, bank: List<BankPlayer>, names: Map<Int, String>): List<NightPlayer>? {
        val settled = settlement.players.all { it.paidOut || it.winningsCents == 0L }
        if (!settlement.isComplete || !settled) return null
        val recorded = bank.associateBy { it.id }
        val person = BankPlayer.people(bank)
        return settlement.players
            .filter { it.place != null }
            .groupBy { person[it.playerId] ?: it.playerId }
            .map { (personId, entries) -> line(personId, entries, recorded, names) }
            .sortedBy { it.place }
            .takeIf { it.isNotEmpty() }
    }

    /** One player's entries (one, unless they re-entered) as their line of the night. */
    private fun line(
        personId: Int,
        entries: List<PlayerSettlement>,
        recorded: Map<Int, BankPlayer>,
        names: Map<Int, String>,
    ): NightPlayer = NightPlayer(
        name = names[personId]?.trim().orEmpty().ifEmpty { "Player $personId" },
        place = entries.minOf { it.place ?: Int.MAX_VALUE },
        entryCents = entries.sumOf { it.costCents - it.rebuyCostCents - it.addOnCostCents },
        rebuys = entries.sumOf { entry -> recorded[entry.playerId].let { it?.rebuyPricesCents?.size ?: it?.rebuys ?: 0 } },
        rebuyCents = entries.sumOf { it.rebuyCostCents },
        addOns = entries.sumOf { entry -> recorded[entry.playerId].let { it?.addOnPricesCents?.size ?: it?.addOns ?: 0 } },
        addOnCents = entries.sumOf { it.addOnCostCents },
        prizeCents = entries.sumOf { it.prizeCents },
        knockouts = entries.sumOf { it.knockouts },
        bountyCents = entries.sumOf { it.knockoutBountyCents + it.kingsBountyCents + it.unclaimedBountyCents },
    )
}
