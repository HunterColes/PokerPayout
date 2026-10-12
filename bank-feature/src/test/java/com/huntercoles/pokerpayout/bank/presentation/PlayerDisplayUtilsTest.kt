package com.huntercoles.pokerpayout.bank.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/** The order of the Bank's rows ([bankOrder]): players still in by seat, then the latest out first. */
class PlayerDisplayUtilsTest {

    @Test
    fun newestKnockoutAppearsAboveEarlierEliminations() {
        val players = (1..5).map { id -> PlayerData(id = id, name = "Player $id", out = id in listOf(5, 3)) }

        assertEquals(listOf(1, 2, 4, 3, 5), bankOrder(players, eliminationOrder = listOf(5, 3)).map { it.id })
    }

    @Test
    fun playersStillInKeepTheirSeatOrder() {
        val players = listOf(
            PlayerData(id = 10, name = "Player 10"),
            PlayerData(id = 2, name = "Player 2"),
            PlayerData(id = 7, name = "Player 7"),
            PlayerData(id = 4, name = "Player 4", out = true)
        )

        assertEquals(listOf(10, 2, 7, 4), bankOrder(players, eliminationOrder = listOf(4)).map { it.id })
    }

    @Test
    fun aPlayerOutButMissingFromTheOrderComesLast() {
        val players = (1..4).map { id -> PlayerData(id = id, name = "Player $id", out = id != 1) }

        assertEquals(listOf(1, 3, 2, 4), bankOrder(players, eliminationOrder = listOf(2, 3)).map { it.id })
    }
}
