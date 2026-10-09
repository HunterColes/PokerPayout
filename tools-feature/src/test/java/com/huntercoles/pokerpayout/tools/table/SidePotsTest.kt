package com.huntercoles.pokerpayout.tools.table

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Side pots in exact chips: hands worked out by hand (an all-in and two callers, several all-ins, a
 * fold, a bet nobody called), then every split of 2,000 random hands on a fixed seed keeps every
 * chip and gives each pot only to players still in who reached it.
 */
class SidePotsTest {

    private fun split(vararg chips: Long, folded: Set<Int> = emptySet()): PotSplit =
        SidePots.split(chips.mapIndexed { index, it -> Contribution(it, folded = index in folded) })

    private fun pots(vararg chips: Long, folded: Set<Int> = emptySet()): PotSplit.Pots =
        split(*chips, folded = folded) as PotSplit.Pots

    @Test
    fun `nothing in yet is no pot`() {
        assertEquals(PotSplit.Empty, split())
        assertEquals(PotSplit.Empty, split(0, 0, 0))
    }

    @Test
    fun `everyone with chips in folded is no winner`() {
        assertEquals(PotSplit.AllFolded, split(100, 100, 0, folded = setOf(0, 1)))
    }

    @Test
    fun `one all-in and two callers make a main pot for three and a side pot for two`() {
        // Dana all in for 100; Sam and Theo bet on to 300.
        val split = pots(100, 300, 300)
        assertEquals(
            listOf(Pot(300, 0, 100, listOf(0, 1, 2), listOf(0, 1, 2)), Pot(400, 100, 300, listOf(1, 2), listOf(1, 2))),
            split.pots,
        )
        assertNull(split.uncalled)
        assertEquals(700, split.total)
    }

    @Test
    fun `each all-in caps its own pot`() {
        // 50, 120 and 300 all in, and a caller for 300: 200 for four, 210 for three, 360 for two.
        val split = pots(50, 120, 300, 300)
        assertEquals(listOf(200L, 210L, 360L), split.pots.map { it.chips })
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(1, 2, 3), listOf(2, 3)), split.pots.map { it.eligible })
        assertEquals(listOf(0L to 50L, 50L to 120L, 120L to 300L), split.pots.map { it.from to it.upTo })
    }

    @Test
    fun `folded chips stay in the pot but folded players can't win it`() {
        // Jo put in 50 and folded; Sam and Theo went on to 200: one pot of 450 for the two of them.
        val split = pots(50, 200, 200, folded = setOf(0))
        assertEquals(listOf(Pot(450, 0, 200, listOf(0, 1, 2), listOf(1, 2))), split.pots)
    }

    @Test
    fun `a bet nobody matched goes back, and a folded caller's chips go to whoever is left`() {
        // Ana bets to 1,500 in all; Ben is all in for 300; Cy called 500 earlier, then folded.
        val split = pots(1_500, 300, 500, folded = setOf(2))
        assertEquals(
            listOf(Pot(900, 0, 300, listOf(0, 1, 2), listOf(0, 1)), Pot(400, 300, 500, listOf(0, 2), listOf(0))),
            split.pots,
        )
        assertEquals(Uncalled(player = 0, chips = 1_000), split.uncalled)
        assertEquals(2_300, split.total)
    }

    @Test
    fun `a bet with nobody else in comes straight back`() {
        val split = pots(400, 0, 0)
        assertEquals(emptyList<Pot>(), split.pots)
        assertEquals(Uncalled(player = 0, chips = 400), split.uncalled)
    }

    @Test
    fun `chips only folded players reached join the pot below`() {
        // Two players folded at 500 above two all-ins of 300: their 400 can't start a pot nobody can win.
        val split = pots(500, 300, 300, 500, folded = setOf(0, 3))
        assertEquals(listOf(Pot(1_600, 0, 500, listOf(0, 1, 2, 3), listOf(1, 2))), split.pots)
        assertNull(split.uncalled)
    }

    @Test
    fun `everyone in for the same is one pot`() {
        val split = pots(250, 250, 250, 250)
        assertEquals(listOf(Pot(1_000, 0, 250, listOf(0, 1, 2, 3), listOf(0, 1, 2, 3))), split.pots)
    }

    @Test
    fun `players with nothing in aren't in the hand`() {
        val split = pots(0, 100, 0, 100)
        assertEquals(listOf(Pot(200, 0, 100, listOf(1, 3), listOf(1, 3))), split.pots)
    }

    @Test
    fun `every chip is in a pot or goes back, and only players still in who reached a pot can win it`() {
        val random = Random(SEED)
        repeat(RANDOM_HANDS) {
            val players = random.nextInt(SidePots.MIN_PLAYERS, SidePots.MAX_PLAYERS + 1)
            val hand = List(players) {
                Contribution(chips = random.nextLong(0, MAX_BET) / STEP * STEP, folded = random.nextInt(4) == 0)
            }
            val split = SidePots.split(hand)
            if (split !is PotSplit.Pots) return@repeat
            assertEquals(hand.sumOf { it.chips }, split.pots.sumOf { it.chips } + (split.uncalled?.chips ?: 0L), "$hand")
            split.pots.forEach { pot ->
                assertTrue(pot.chips > 0 && pot.eligible.isNotEmpty(), "$hand: $pot")
                pot.eligible.forEach { assertTrue(!hand[it].folded && hand[it].chips > pot.from, "$hand: $pot") }
                val inRange = hand.sumOf { (minOf(it.chips, pot.upTo) - minOf(it.chips, pot.from)) }
                assertEquals(inRange, pot.chips, "$hand: $pot is every player's chips from ${pot.from} to ${pot.upTo}")
            }
            split.pots.zipWithNext().forEach { (lower, upper) ->
                assertEquals(lower.upTo, upper.from, "$hand: pots stack")
                assertTrue(lower.eligible.containsAll(upper.eligible), "$hand: a side pot's players are in the pot below")
                assertTrue(lower.eligible != upper.eligible, "$hand: pots the same players can win are one")
            }
        }
    }

    private companion object {
        const val SEED = 20261008L
        const val RANDOM_HANDS = 2_000
        const val MAX_BET = 5_000L
        const val STEP = 25L
    }
}
