package com.huntercoles.pokerpayout.tools.poker

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The next-card grid and outs (PP-028). The fixture is the mockups' hand: A♠K♠ against Q♥Q♦ on
 * J♠10♠2♣. Expected values are cross-checked against [ReferenceEvaluator], a brute-force
 * best-of-21 evaluator that shares no code with the engine.
 */
class NextCardBreakdownTest {

    private val engine = OddsEngine()

    private fun request(vararg seats: String, board: String = "", dead: String = "") =
        OddsRequest(seats.map { Seat.of(it) }, Cards.parseAll(board), Cards.parseAll(dead))

    private fun breakdown(request: OddsRequest) = runBlocking { engine.nextCardBreakdown(request) }

    private val fixture = request("As Ks", "Qh Qd", board = "Js Ts 2c")

    /** Player 1's outs on the flop: nine spades (one of them the royal), three aces, three kings and the Q♣. */
    private val flopOuts = Cards.parseAll("Qs 9s 8s 7s 6s 5s 4s 3s 2s Ah Ad Ac Kh Kd Kc Qc").toSet()

    @Test
    fun `on the flop 16 of the 45 live cards put Player 1 ahead`() {
        val b = breakdown(fixture)
        assertEquals(45, b.cards.size)
        assertEquals(1, b.currentLeader, "Q♥Q♦ is ahead right now")
        assertEquals(16, b.leadCount(0))
        assertEquals(29, b.leadCount(1))
        assertEquals(flopOuts, b.outs(0).toSet())
        assertTrue(b.cards.all { it.leader != null }, "no turn card leaves the two hands level")
    }

    @Test
    fun `the 7 of hearts on the turn leaves Player 1 exactly 16 of 44 rivers`() {
        val turn = breakdown(request("As Ks", "Qh Qd", board = "Js Ts 2c 7h"))
        assertEquals(44, turn.cards.size)
        assertEquals(16, turn.leadCount(0))
        assertEquals(flopOuts, turn.outs(0).toSet(), "the 7♥ is a blank: the outs don't change")
        // On the river the leader is the winner: 100% or 0%.
        assertTrue(turn.cards.all { it.equityPct.toSet() == setOf(0.0, 100.0) })

        val sevenOfHearts = breakdown(fixture).of(Cards.parse("7h"))!!
        assertEquals(100.0 * 16 / 44, sevenOfHearts.equityPct[0], 1e-9) // 36.36%
        assertEquals(100.0 * 28 / 44, sevenOfHearts.equityPct[1], 1e-9) // 63.64%
        assertEquals(1, sevenOfHearts.leader)

        val result = runBlocking { engine.finalResult(request("As Ks", "Qh Qd", board = "Js Ts 2c 7h")) }
        assertEquals(36.36, result.players[0].equityPct, 0.005)
    }

    @Test
    fun `next-card equities match a brute-force reference and average to the flop equity`() {
        val b = breakdown(fixture)
        val hole = listOf(Cards.parseAll("As Ks"), Cards.parseAll("Qh Qd"))
        val flop = Cards.parseAll("Js Ts 2c")
        for (next in b.cards) {
            val rivers = (0 until Cards.COUNT).filter { it !in hole.flatten() + flop + next.card }
            val p1 = rivers.sumOf { river ->
                val board = flop + next.card + river
                val cmp = ReferenceEvaluator.compareKeys(
                    ReferenceEvaluator.best(hole[0] + board),
                    ReferenceEvaluator.best(hole[1] + board),
                )
                if (cmp > 0) 1.0 else if (cmp == 0) 0.5 else 0.0
            }
            assertEquals(100.0 * p1 / rivers.size, next.equityPct[0], 1e-9, "after ${Cards.format(next.card)}")
            assertEquals(100.0, next.equityPct.sum(), 1e-9)
        }
        // The flop equity is the average over every turn card: 555 of 990 runouts.
        assertEquals(100.0 * 555 / 990, b.cards.sumOf { it.equityPct[0] } / b.cards.size, 1e-9)
    }

    @Test
    fun `outs is the same list the breakdown gives`() {
        val outs = runBlocking { engine.outs(fixture, seat = 0) }
        assertEquals(flopOuts, outs.toSet())
        assertEquals(16, outs.size)
    }

    @Test
    fun `a folded seat is never the leader and its cards can't come`() {
        val req = OddsRequest(
            seats = listOf(Seat.of("As Ks"), Seat.of("Ah Kh", folded = true), Seat.of("Qh Qd")),
            board = Cards.parseAll("Js Ts 2c"),
        )
        val b = breakdown(req)
        assertEquals(43, b.cards.size, "52 - 7 known cards")
        assertTrue(b.cards.none { it.leader == 1 })
        assertTrue(b.cards.all { it.equityPct[1] == 0.0 })
        assertEquals(2, b.currentLeader)
        assertTrue(Cards.parse("Ah") !in b.cards.map { it.card })
    }

    @Test
    fun `three-way equities add up for every next card`() {
        val b = breakdown(request("As Ks", "Qh Qd", "8c 9c", board = "Js Ts 2c"))
        assertEquals(43, b.cards.size)
        b.cards.forEach { assertEquals(100.0, it.equityPct.sum(), 1e-9) }
        // 8♣9♣'s open-ended draw: a red seven makes its straight and puts it in front. (The Q♣
        // gives A♠K♠ broadway and the 7♠ gives it the flush, so those aren't 8♣9♣'s.)
        assertTrue(b.outs(2).containsAll(Cards.parseAll("7d 7h")), "outs ${Cards.format(b.outs(2))}")
        assertTrue(Cards.parse("7s") in b.outs(0))
    }

    @Test
    fun `a random hand works within the budget`() {
        val b = breakdown(request("As Ks", "", board = "Js Ts 2c 7h"))
        assertEquals(46, b.cards.size, "the random hand's cards could be any of the 46 unseen")
        assertNull(b.currentLeader, "the random hand isn't known, so nobody is ahead 'right now'")
        b.cards.forEach { assertEquals(100.0, it.equityPct.sum(), 1e-9) }
    }

    @Test
    fun `ties at the top have no leader`() {
        // The board plays on any river that doesn't help either hand: broadway on the board.
        val b = breakdown(request("2c 3d", "2h 3s", board = "As Ks Qd Jc"))
        assertNull(b.currentLeader)
        val ten = b.of(Cards.parse("Td"))!!
        assertNull(ten.leader)
        assertEquals(listOf(50.0, 50.0), ten.equityPct)
    }

    @Test
    fun `next-card odds need a flop or a turn, and a budget`() {
        fun error(req: OddsRequest, budget: Long = OddsSettings.DEFAULT_EXACT_BUDGET): String =
            assertThrows(OddsInputException::class.java) { runBlocking { engine.nextCardBreakdown(req, budget) } }.message!!

        assertEquals("Next-card odds need a flop or a turn.", error(request("As Ks", "Qh Qd")))
        assertEquals("Next-card odds need a flop or a turn.", error(request("As Ks", "Qh Qd", board = "Js Ts 2c 7h 3d")))
        assertEquals(
            "Too many unknown cards to work out every next card.",
            error(request("As Ks", "", board = "Js Ts 2c"), budget = 1_000),
        )
        assertEquals("As is used twice.", error(request("As Ks", "As Qd", board = "Js Ts 2c")))
    }
}
