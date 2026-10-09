package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.EntryPrice
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp

/**
 * Late entries and re-entries (PP-116), as the Bank's rows see them. Every row is one entry; a
 * re-entry names its player's first entry, so these work out who each row belongs to, which entry
 * of theirs it is, and who can buy back in. The rule for "who" is the settlement's own
 * ([BankPlayer.people]), so the rows, the settle-up and History agree.
 */
internal object BankEntries {

    /** Row id to the id of that player's first entry. */
    fun people(players: List<PlayerData>): Map<Int, Int> =
        BankPlayer.people(players.map { BankPlayer(it.id, reEntryOf = it.reEntryOf) })

    /** Each re-entry's number among its player's entries (2 for the first re-entry); first entries aren't listed. */
    fun numbers(players: List<PlayerData>): Map<Int, Int> {
        val person = people(players)
        return players.sortedBy { it.id }
            .groupBy { person.getValue(it.id) }
            .values
            .flatMap { entries -> entries.drop(1).mapIndexed { index, entry -> entry.id to index + 2 } }
            .toMap()
    }

    /**
     * Entries their player has re-entered after: out for good. Bringing one back would seat the
     * player twice, so their place disc doesn't (Undo takes a re-entry back).
     */
    fun replaced(players: List<PlayerData>): Set<Int> {
        val person = people(players)
        val latest = players.groupBy { person.getValue(it.id) }.mapValues { (_, entries) -> entries.maxOf { it.id } }
        return players.filter { latest[person.getValue(it.id)] != it.id }.map { it.id }.toSet()
    }

    /**
     * The players who can re-enter: every entry of theirs is out. Each by their latest entry, the
     * latest out first, with its place ([places]) and how many entries they have had.
     */
    fun reEntries(players: List<PlayerData>, eliminationOrder: List<Int>, places: Map<Int, Int>): List<ReEntryCandidate> {
        val person = people(players)
        val outAt = eliminationOrder.withIndex().associate { (index, id) -> id to index }
        return players.groupBy { person.getValue(it.id) }
            .values
            .filter { entries -> entries.all { it.out } }
            .map { entries ->
                val latest = entries.maxBy { it.id }
                ReEntryCandidate(latest.id, latest.name, places[latest.id], entries.size)
            }
            .sortedByDescending { outAt[it.playerId] ?: -1 }
    }

    /** A player who arrived late, as the newest row: [name] (blank: "Player N"), paid at [money]'s entry now. */
    fun lateArrival(players: List<PlayerData>, name: String, money: MoneySettings): PlayerData {
        val id = players.size + 1
        return PlayerData(
            id = id,
            name = name.trim().take(MAX_ENTRY_NAME_LENGTH).ifBlank { "Player $id" },
            buyIn = true,
            entryPrice = EntryPrice.of(money),
        )
    }

    /** [out]'s player buying back in, as the newest row: their name, paid at [money]'s entry now, theirs. */
    fun reEntry(players: List<PlayerData>, out: ReEntryCandidate, money: MoneySettings): PlayerData = PlayerData(
        id = players.size + 1,
        name = out.name,
        buyIn = true,
        entryPrice = EntryPrice.of(money),
        reEntryOf = people(players).getValue(out.playerId),
    )

    /**
     * Standard bounties: what each of [playerId]'s knockouts paid ([headBounty] of each player they
     * knocked out), when they all paid the same; null when they differ (a late entry's at its price).
     */
    fun knockoutEach(state: BankUiState, playerId: Int, headBounty: (Int) -> Long): Long? = state.players
        .filter { it.out && it.id != playerId && it.eliminatedBy == playerId && it.id != state.championId }
        .map { headBounty(it.id) }
        .distinct()
        .singleOrNull()

    /** The settle-up as the Bank shows it: one line per player, a re-entry's entries together under the first. */
    fun settleUpModel(plan: SettleUp, result: Settlement, players: List<PlayerData>, money: MoneySettings): SettleUpModel {
        val person = people(players)
        val names = players.associate { it.id to it.name }
        return SettleUpModel(
            transfers = plan.transfers,
            nights = players.groupBy { person.getValue(it.id) }.map { (personId, entries) ->
                PlayerNight(
                    playerId = personId,
                    name = names[personId].orEmpty(),
                    inCents = entries.sumOf { entry -> result.forPlayer(entry.id)?.costCents ?: entry.entryCents(money) },
                    wonCents = entries.sumOf { entry -> result.forPlayer(entry.id)?.winningsCents ?: 0L },
                )
            },
            foodCents = result.pool.foodCents,
        )
    }
}
