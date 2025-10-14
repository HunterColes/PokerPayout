package com.huntercoles.pokerpayout.portfoliofeature.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDisplayUtilsTest {

    @Test
    fun newestKnockoutAppearsAboveEarlierEliminations() {
        val players = (1..5).map { id ->
            val isOut = id in listOf(5, 3)
            PlayerData(id = id, name = "Player $id", out = isOut)
        }
        val eliminationOrder = listOf(5, 3)

        val displayModels = buildPlayerDisplayModels(players, eliminationOrder)

        assertEquals(listOf(1, 2, 4, 3, 5), displayModels.map { it.player.id })
        assertEquals(listOf(null, null, null, 4, 5), displayModels.map { it.placement })
    }

    @Test
    fun placementIsNullForActiveAndCalculatesForEliminated() {
        val players = (1..4).map { id ->
            when (id) {
                2 -> PlayerData(id = id, name = "Player $id", out = true)
                else -> PlayerData(id = id, name = "Player $id")
            }
        }
        val eliminationOrder = listOf(2)

        val displayModels = buildPlayerDisplayModels(players, eliminationOrder)

        val placementsById = displayModels.associate { it.player.id to it.placement }

        assertEquals(null, placementsById[1])
        assertEquals(4, placementsById[2])
        assertEquals(null, placementsById[3])
        assertEquals(null, placementsById[4])
    }

    @Test
    fun activePlayersHonorOriginalOrdering() {
        val players = listOf(
            PlayerData(id = 10, name = "Player 10"),
            PlayerData(id = 2, name = "Player 2"),
            PlayerData(id = 7, name = "Player 7"),
            PlayerData(id = 4, name = "Player 4", out = true)
        )
        val eliminationOrder = listOf(4)

        val displayModels = buildPlayerDisplayModels(players, eliminationOrder)

        assertEquals(listOf(10, 2, 7, 4), displayModels.map { it.player.id })
    }
}
