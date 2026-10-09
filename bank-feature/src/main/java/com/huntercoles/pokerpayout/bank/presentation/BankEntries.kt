package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer

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
}
