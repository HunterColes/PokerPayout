package com.huntercoles.pokerpayout.bank.presentation

/**
 * [players] in the order the Bank lists them: those still in, in seat order, then those out, the
 * latest out first ([eliminationOrder] is oldest first). Places come from the settlement, not from here.
 */
internal fun bankOrder(players: List<PlayerData>, eliminationOrder: List<Int>): List<PlayerData> {
    val outAt = eliminationOrder.withIndex().associate { (index, id) -> id to index }
    // A stable sort: the players still in keep their seat order
    return players.sortedWith(compareBy<PlayerData> { it.out }.thenByDescending { outAt[it.id] ?: -1 })
}
