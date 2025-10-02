package com.huntercoles.fatline.portfoliofeature.presentation

internal data class PlayerDisplayModel(
    val player: PlayerData,
    val placement: Int?
)

internal fun buildPlayerDisplayModels(
    players: List<PlayerData>,
    eliminationOrder: List<Int>
): List<PlayerDisplayModel> {
    if (players.isEmpty()) return emptyList()

    val eliminationIndexLookup = eliminationOrder
        .withIndex()
        .associate { indexedValue -> indexedValue.value to indexedValue.index }
    val originalIndexLookup = players
        .mapIndexed { index, player -> player.id to index }
        .toMap()
    val totalPlayers = players.size

    val sortedPlayers = players.sortedWith(
        compareBy<PlayerData> { if (it.out) 1 else 0 }
            .thenByDescending { player -> eliminationIndexLookup[player.id] ?: Int.MIN_VALUE }
            .thenBy { player -> originalIndexLookup[player.id] ?: Int.MAX_VALUE }
    )

    return sortedPlayers.map { player ->
        val placement = eliminationIndexLookup[player.id]?.let { eliminationIndex ->
            (totalPlayers - eliminationIndex).coerceAtLeast(1)
        }
        PlayerDisplayModel(player = player, placement = placement)
    }
}
