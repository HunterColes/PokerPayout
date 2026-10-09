package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import com.huntercoles.pokerpayout.core.domain.players.PlayerNames
import com.huntercoles.pokerpayout.core.domain.players.Regular

/** A regular in the tonight's players sheet, and the seat they hold tonight ([seatId]), if any. */
data class RegularRow(val regular: Regular, val seatId: Int?) {
    val seated: Boolean get() = seatId != null
}

/**
 * The tonight's players sheet (S26, PP-110): every regular, each at the table tonight or not, and how
 * many of the Bank's seats have a name. The rows keep the order they had when the sheet opened
 * ([BankSheet.Regulars.order]), so a tap never moves a name under the finger; a name new since then
 * (added, or typed in a row before 1.4 kept names) comes first.
 *
 * @property seats the Bank's seats: the Tournament tab's player count.
 * @property named the seats with a name ("Player 3" is a seat nobody named).
 * @property merges the names merged in History, so that a spelling finds its regular.
 */
data class RegularsModel(
    val rows: List<RegularRow>,
    val seats: Int,
    val named: Int,
    val merges: PlayerMerges = PlayerMerges.NONE,
) {
    /** Seats nobody named yet: a regular picked takes the first. */
    val open: Int get() = seats - named

    /** With every seat named, a regular picked adds a seat, up to the Tournament tab's most players. */
    val canAddSeat: Boolean get() = seats < TournamentConstants.MAX_PLAYERS

    /** True when someone can still sit down: an open seat, or room for one more. */
    val canSeat: Boolean get() = open > 0 || canAddSeat

    /** The rows whose name has [query] in it (all of them for a blank query), ignoring case. */
    fun matching(query: String): List<RegularRow> {
        val wanted = query.trim()
        return if (wanted.isEmpty()) rows else rows.filter { it.regular.name.contains(wanted, ignoreCase = true) }
    }

    /** The regular [query] names exactly, as names match (trimmed, ignoring case, merged names as one), if any. */
    fun exact(query: String): RegularRow? = merges.key(query).let { person -> rows.firstOrNull { it.regular.key == person } }

    /** True when Add would seat someone: a name, not one at the table already, and a seat for them. */
    fun canAdd(query: String): Boolean {
        val name = PlayerNames.clean(query)
        return !PlayerNames.isPlaceholder(name) && exact(name)?.seated != true && canSeat
    }

    companion object {
        /** The sheet for [state], its rows in [order] (the regulars' keys when it opened). */
        fun of(state: BankUiState, order: List<String>): RegularsModel {
            val merges = state.merges
            val named = state.players.filterNot { PlayerNames.isPlaceholder(it.name) }
            // A name twice at the table holds the later seat: that is the one a tap gives back
            val seats = named.associate { merges.key(it.name) to it.id }
            val known = state.roster.map { it.key }.toSet()
            val tonightOnly = named
                .distinctBy { merges.key(it.name) }
                .filter { merges.key(it.name) !in known }
                .map { player -> newcomer(player.name.trim(), merges.key(player.name)) }
            val position = order.withIndex().associate { (index, key) -> key to index }
            val rows = (tonightOnly + state.roster)
                .sortedBy { position[it.key] ?: -1 }
                .map { RegularRow(it, seats[it.key]) }
            return RegularsModel(rows, seats = state.players.size, named = named.size, merges = merges)
        }

        /** Someone at the table the regulars don't know yet: no saved night, never seen before tonight. */
        private fun newcomer(name: String, key: String) =
            Regular(name, key, nights = 0, recentNights = 0, lastPlayed = null, lastSeen = null)
    }
}
