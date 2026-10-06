package com.huntercoles.pokerpayout.tools.seats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

/**
 * The seat draw (PP-036): balanced tables, a real permutation of the players, the same draw for
 * the same seed, and every arrangement about as likely as any other. Seeds are fixed, so the
 * statistical checks are deterministic: they can't flake.
 */
class SeatDrawerTest {

    private fun names(count: Int) = List(count) { "P$it" }

    @Test
    fun `table sizes for the usual nights`() {
        assertEquals(listOf(9), SeatDrawer.tableSizes(9, 9))
        assertEquals(listOf(5, 5), SeatDrawer.tableSizes(10, 9), "10 at nine a table: two tables of 5, not 9 and 1")
        assertEquals(listOf(10), SeatDrawer.tableSizes(10, 10))
        assertEquals(listOf(7, 7), SeatDrawer.tableSizes(14, 9))
        assertEquals(listOf(7, 6, 6), SeatDrawer.tableSizes(19, 9))
        assertEquals(listOf(6, 5), SeatDrawer.tableSizes(11, 10))
        assertEquals(listOf(2), SeatDrawer.tableSizes(2, 9))
        assertEquals(List(6) { 10 }, SeatDrawer.tableSizes(60, 10))
        assertEquals(emptyList<Int>(), SeatDrawer.tableSizes(0, 9))
    }

    @Test
    fun `every player count and table size gives as few tables as fit, balanced to within one`() {
        for (players in SeatDrawer.MIN_PLAYERS..SeatDrawer.MAX_PLAYERS) {
            for (seats in SeatDrawer.MIN_SEATS_PER_TABLE..SeatDrawer.MAX_SEATS_PER_TABLE) {
                val sizes = SeatDrawer.tableSizes(players, seats)
                val where = "$players players at $seats a table: $sizes"
                assertEquals(players, sizes.sum(), where)
                assertEquals((players + seats - 1) / seats, sizes.size, "$where: as few tables as fit")
                assertTrue(sizes.max() <= seats, "$where: no table over its seats")
                assertTrue(sizes.max() - sizes.min() <= 1, "$where: balanced")
                assertTrue(sizes.min() >= SeatDrawer.MIN_PLAYERS, "$where: nobody alone at a table")
                assertEquals(sizes.sortedDescending(), sizes, "$where: largest first")
            }
        }
    }

    @Test
    fun `a draw seats every player exactly once, at balanced tables numbered from 1`() {
        for (seed in 0L until 300L) {
            val random = Random(seed)
            val players = names(random.nextInt(SeatDrawer.MIN_PLAYERS, SeatDrawer.MAX_PLAYERS + 1))
            val seats = random.nextInt(SeatDrawer.MIN_SEATS_PER_TABLE, SeatDrawer.MAX_SEATS_PER_TABLE + 1)
            val draw = SeatDrawer.drawSeats(players, seats, Random(seed))
            val where = "seed $seed, ${players.size} players at $seats a table"

            assertEquals(players.sorted(), draw.players.sorted(), "$where: a permutation")
            assertEquals(SeatDrawer.tableSizes(players.size, seats), draw.tables.map { it.seats.size }, where)
            assertEquals((1..draw.tables.size).toList(), draw.tables.map { it.number }, where)
            assertTrue(draw.tables.all { it.buttonCards == null }, "$where: no button until it's dealt")
        }
    }

    @Test
    fun `the same seed always gives the same draw, and different seeds different ones`() {
        val players = names(14)
        assertEquals(SeatDrawer.drawSeats(players, 9, Random(42)), SeatDrawer.drawSeats(players, 9, Random(42)))
        val draws = (0L until 200L).map { SeatDrawer.drawSeats(players, 9, Random(it)) }.toSet()
        assertTrue(draws.size >= 199, "200 seeds gave only ${draws.size} different draws")
        assertNotEquals(players, SeatDrawer.drawSeats(players, 9, Random(7)).players, "the draw shuffles")
    }

    @Test
    fun `every seating of four players at one table comes up`() {
        val players = names(4)
        val seen = (0L until 2_000L).map { SeatDrawer.drawSeats(players, 4, Random(it)).players }.toSet()
        assertEquals(24, seen.size, "all 4! seatings")
    }

    @Test
    fun `each player is about as likely to get any seat (chi-square, 10,000 seeds)`() {
        val players = names(10)
        val counts = IntArray(10)
        for (seed in 0L until 10_000L) {
            val draw = SeatDrawer.drawSeats(players, 10, Random(seed))
            counts[draw.tables.single().seats.indexOf("P0")]++
        }
        // 9 degrees of freedom: 27.9 is the 0.1% tail. The bound is loose; the seeds are fixed.
        val chi = chiSquare(counts)
        assertTrue(chi < LOOSE_BOUND_9_DF, "P0's seats ${counts.toList()}: chi-square $chi")
    }

    @Test
    fun `each player is about as likely to land at either table (chi-square, 10,000 seeds)`() {
        val players = names(10)
        val counts = IntArray(2)
        for (seed in 0L until 10_000L) {
            val draw = SeatDrawer.drawSeats(players, 9, Random(seed))
            counts[draw.tables.indexOfFirst { "P3" in it.seats }]++
        }
        val chi = chiSquare(counts)
        assertTrue(chi < LOOSE_BOUND_1_DF, "P3's tables ${counts.toList()}: chi-square $chi")
    }

    @Test
    fun `players who share a name are each seated`() {
        val draw = SeatDrawer.drawSeats(listOf("Sam", "Sam", "Jo"), 9, Random(1))
        assertEquals(listOf("Jo", "Sam", "Sam"), draw.players.sorted())
    }

    @Test
    fun `too few or too many players, or a table size no table has, is refused`() {
        assertThrows<IllegalArgumentException> { SeatDrawer.drawSeats(names(1), 9, Random(0)) }
        assertThrows<IllegalArgumentException> { SeatDrawer.drawSeats(names(SeatDrawer.MAX_PLAYERS + 1), 9, Random(0)) }
        assertThrows<IllegalArgumentException> { SeatDrawer.drawSeats(names(9), 2, Random(0)) }
        assertThrows<IllegalArgumentException> { SeatDrawer.drawSeats(names(9), 11, Random(0)) }
    }

    @Test
    fun `dealing for the button gives every seat of every table a card from its table's own deck`() {
        val seated = SeatDrawer.drawSeats(names(19), 9, Random(3))
        var sharedACard = false
        for (seed in 0L until 200L) {
            val dealt = SeatDrawer.dealButtons(seated, Random(seed))
            assertEquals(seated.players, dealt.players, "the seats stay as drawn")
            assertTrue(dealt.buttonDealt)
            dealt.tables.forEach { table ->
                val cards = table.buttonCards!!
                assertEquals(table.seats.size, cards.size)
                assertEquals(cards.size, cards.toSet().size, "seed $seed table ${table.number}: one deck, no repeats")
            }
            val all = dealt.tables.flatMap { it.buttonCards!! }
            if (all.toSet().size < all.size) sharedACard = true
        }
        assertTrue(sharedACard, "each table deals from a fresh deck, so two tables can draw the same card")
    }

    companion object {
        /** Far beyond the 0.1% tail (27.9 for 9 degrees of freedom, 10.8 for 1). */
        const val LOOSE_BOUND_9_DF = 40.0
        const val LOOSE_BOUND_1_DF = 15.0

        fun chiSquare(counts: IntArray): Double {
            val expected = counts.sum().toDouble() / counts.size
            return counts.sumOf { (it - expected) * (it - expected) / expected }
        }
    }
}
