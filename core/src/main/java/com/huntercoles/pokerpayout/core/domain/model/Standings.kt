package com.huntercoles.pokerpayout.core.domain.model

/**
 * Finishing places from the order players were knocked out.
 *
 * With n players, the first player out finishes n-th, the next (n-1)-th, and so on. 1st place is
 * the last player standing, known once n-1 players are out. Places that aren't decided yet are
 * simply absent: nobody is "1st" while three players are still in.
 *
 * @param playerIds everyone in the tournament
 * @param eliminationOrder player ids, first out first; unknown ids and repeats are ignored
 */
class Standings(playerIds: List<Int>, eliminationOrder: List<Int>) {
    private val ids = playerIds.distinct()
    private val order = eliminationOrder.distinct().filter { it in ids }

    /** The last player standing, or null while more than one player is still in. */
    val championId: Int? = run {
        val remaining = ids - order.toSet()
        when {
            remaining.size == 1 -> remaining.single()
            remaining.isEmpty() -> order.lastOrNull()
            else -> null
        }
    }

    /** Player id to finishing place, for every player whose place is decided. */
    val placeByPlayer: Map<Int, Int> = buildMap {
        order.forEachIndexed { index, playerId -> put(playerId, ids.size - index) }
        championId?.let { put(it, 1) }
    }

    /** Players knocked out so far, first out first. */
    val eliminated: List<Int> get() = order

    fun placeOf(playerId: Int): Int? = placeByPlayer[playerId]

    fun playerAt(place: Int): Int? = placeByPlayer.entries.firstOrNull { it.value == place }?.key
}
