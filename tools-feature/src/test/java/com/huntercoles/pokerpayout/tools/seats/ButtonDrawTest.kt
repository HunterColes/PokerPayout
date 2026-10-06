package com.huntercoles.pokerpayout.tools.seats

import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.seats.SeatDrawerTest.Companion.chiSquare
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

/**
 * The draw for the button (PP-036): high card wins, a tie on rank goes by suit (♠ > ♥ > ♦ > ♣),
 * the two seats after the button post the blinds (heads-up the button posts the small one), every
 * seat can win, and no seat is favoured.
 */
class ButtonDrawTest {

    private fun cards(text: String) = Cards.parseAll(text)

    @Test
    fun `high card takes the button`() {
        val result = ButtonDraw.resultFor(cards("7c Qd 2s Ah 9s"))
        assertEquals(4, result.buttonSeat)
        assertEquals(Cards.parse("Ah"), result.winningCard)
        assertFalse(result.tiedOnRank)
    }

    @Test
    fun `a tie on rank goes by suit, spades then hearts then diamonds then clubs`() {
        val result = ButtonDraw.resultFor(cards("Kh 3c Ks Kd"))
        assertEquals(3, result.buttonSeat, "K♠ beats K♥ and K♦")
        assertTrue(result.tiedOnRank)

        for (rank in Cards.RANK_CHARS) {
            val bySuit = "cdhs".map { Cards.parse("$rank$it") }
            for (low in 0 until 4) {
                for (high in low + 1 until 4) {
                    val seats = listOf(bySuit[high], bySuit[low])
                    assertEquals(1, ButtonDraw.resultFor(seats).buttonSeat, "${Cards.format(seats)}: suit order")
                    assertEquals(2, ButtonDraw.resultFor(seats.reversed()).buttonSeat, "${Cards.format(seats.reversed())}")
                }
            }
        }
    }

    @Test
    fun `any rank beats every lower rank, whatever the suits`() {
        assertEquals(2, ButtonDraw.resultFor(cards("2s 3c")).buttonSeat)
        assertEquals(1, ButtonDraw.resultFor(cards("Ac Ks")).buttonSeat)
        val sorted = (0 until Cards.COUNT).sortedWith(ButtonDraw.highCard)
        assertEquals(Cards.parse("2c"), sorted.first())
        assertEquals(Cards.parse("As"), sorted.last())
        assertEquals(listOf("Kc", "Kd", "Kh", "Ks"), sorted.filter { Cards.format(it)[0] == 'K' }.map(Cards::format))
    }

    @Test
    fun `the two seats after the button post the blinds, going round the table`() {
        val middle = ButtonDraw.resultFor(cards("2c As 3c 4c 5c"))
        assertEquals(Triple(2, 3, 4), middle.seats())
        val last = ButtonDraw.resultFor(cards("2c 3c 4c 5c As"))
        assertEquals(Triple(5, 1, 2), last.seats(), "past the last seat comes seat 1")
        val nextToLast = ButtonDraw.resultFor(cards("2c 3c 4c As 5c"))
        assertEquals(Triple(4, 5, 1), nextToLast.seats())
        val threeHanded = ButtonDraw.resultFor(cards("2c 3c As"))
        assertEquals(Triple(3, 1, 2), threeHanded.seats())
    }

    @Test
    fun `heads-up the button posts the small blind and the other player the big blind`() {
        assertEquals(Triple(1, 1, 2), ButtonDraw.resultFor(cards("As Kd")).seats())
        assertEquals(Triple(2, 2, 1), ButtonDraw.resultFor(cards("Kd As")).seats())
    }

    @Test
    fun `at every table size and every button seat the blinds are the next two seats`() {
        for (size in SeatDrawer.MIN_PLAYERS..SeatDrawer.MAX_SEATS_PER_TABLE) {
            for (button in 1..size) {
                // The ace of spades at the button seat, deuces and threes elsewhere.
                val dealt = (1..size).map { seat -> if (seat == button) Cards.parse("As") else seat - 1 }
                val result = ButtonDraw.resultFor(dealt)
                val where = "$size seats, button $button"
                assertEquals(button, result.buttonSeat, where)
                if (size == 2) {
                    assertEquals(button, result.smallBlindSeat, where)
                } else {
                    assertEquals(button % size + 1, result.smallBlindSeat, where)
                }
                assertEquals(result.smallBlindSeat % size + 1, result.bigBlindSeat, where)
                assertTrue(result.bigBlindSeat != result.buttonSeat, where)
            }
        }
    }

    @Test
    fun `every seat at every table size can win the button`() {
        for (size in SeatDrawer.MIN_PLAYERS..SeatDrawer.MAX_SEATS_PER_TABLE) {
            val winners = (0L until 500L).map { ButtonDraw.resultFor(ButtonDraw.deal(size, Random(it))).buttonSeat }.toSet()
            assertEquals((1..size).toSet(), winners, "$size seats")
        }
    }

    @Test
    fun `no seat is favoured (chi-square over 9,000 nine-handed deals)`() {
        val counts = IntArray(9)
        for (seed in 0L until 9_000L) counts[ButtonDraw.resultFor(ButtonDraw.deal(9, Random(seed))).buttonSeat - 1]++
        // 8 degrees of freedom: 26.1 is the 0.1% tail. A loose bound, on fixed seeds: no flakiness.
        val chi = chiSquare(counts)
        assertTrue(chi < LOOSE_BOUND_8_DF, "button seats ${counts.toList()}: chi-square $chi")
    }

    @Test
    fun `a deal is distinct cards from one deck, the same for the same seed, and any card can come first`() {
        assertEquals(ButtonDraw.deal(9, Random(5)), ButtonDraw.deal(9, Random(5)))
        val firsts = mutableSetOf<Int>()
        for (seed in 0L until 2_000L) {
            val dealt = ButtonDraw.deal(10, Random(seed))
            assertEquals(10, dealt.toSet().size)
            assertTrue(dealt.all { it in 0 until Cards.COUNT })
            firsts += dealt.first()
        }
        assertEquals(Cards.COUNT, firsts.size, "every card reaches seat 1")
    }

    @Test
    fun `a draw needs two players and cards from one deck`() {
        assertThrows<IllegalArgumentException> { ButtonDraw.resultFor(cards("As")) }
        assertThrows<IllegalArgumentException> { ButtonDraw.resultFor(cards("As As 2c")) }
    }

    private fun ButtonResult.seats() = Triple(buttonSeat, smallBlindSeat, bigBlindSeat)

    private companion object {
        const val LOOSE_BOUND_8_DF = 40.0
    }
}
