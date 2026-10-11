package com.huntercoles.pokerpayout.tournament.domain.moments

import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment.BUBBLE
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment.CHAMPION
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment.FINAL_TABLE
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment.HEADS_UP
import com.huntercoles.pokerpayout.tournament.domain.moments.BigMoment.IN_THE_MONEY
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * PP-111: which of the night's big moments a field has reached. Only who is still in counts,
 * against the places paid and the seat draw's tables; a moment the night started at is no moment.
 */
class BigMomentsTest {

    private fun reached(players: Int, left: Int, paid: Int = 3, seats: Int = 9) =
        BigMoments.reached(Field(players, left, paid, seats))

    @Test
    fun `the mockups' night of nine, three paid, on one table`() {
        assertEquals(emptySet(), reached(9, left = 9))
        assertEquals(emptySet(), reached(9, left = 5))
        assertEquals(setOf(BUBBLE), reached(9, left = 4))
        assertEquals(setOf(BUBBLE, IN_THE_MONEY), reached(9, left = 3))
        assertEquals(setOf(BUBBLE, IN_THE_MONEY, HEADS_UP), reached(9, left = 2))
        assertEquals(BigMoment.entries.toSet() - FINAL_TABLE, reached(9, left = 1))
    }

    @Test
    fun `a night on two tables reaches the final table when everyone left fits one`() {
        assertEquals(emptySet(), reached(14, left = 10))
        assertEquals(setOf(FINAL_TABLE), reached(14, left = 9))
        assertEquals(setOf(FINAL_TABLE), reached(14, left = 6, seats = 6))
        assertEquals(emptySet(), reached(14, left = 7, seats = 6))
        assertEquals(BigMoment.entries.toSet(), reached(14, left = 1))
    }

    @Test
    fun `a moment the night started at is no moment`() {
        assertEquals(emptySet(), reached(4, left = 4), "four players, three paid: the bubble from the first deal")
        assertEquals(setOf(IN_THE_MONEY), reached(4, left = 3), "so the first one out bursts it")
        assertEquals(emptySet(), reached(9, left = 9, seats = 9), "nine at a table of nine: the final table from the start")
        assertEquals(emptySet(), reached(2, left = 2, paid = 1), "two players are heads-up from the start")
        assertEquals(setOf(IN_THE_MONEY, CHAMPION), reached(2, left = 1, paid = 1))
        assertEquals(emptySet(), reached(3, left = 3, paid = 3), "everyone is paid anyway")
    }

    @Test
    fun `nothing paid, so no bubble and no money, but heads-up and a champion`() {
        assertEquals(setOf(HEADS_UP), reached(6, left = 2, paid = 0))
        assertEquals(setOf(HEADS_UP, CHAMPION), reached(6, left = 1, paid = 0))
    }

    @Test
    fun `when one knockout reaches two moments the bigger one shows`() {
        // Two paid: heads-up is in the money too
        assertEquals(HEADS_UP, BigMoments.headline(reached(6, left = 2, paid = 2) - reached(6, left = 3, paid = 2)))
        // Winner takes all: the bubble is heads-up, the money is the champion
        assertEquals(HEADS_UP, BigMoments.headline(reached(6, left = 2, paid = 1) - reached(6, left = 3, paid = 1)))
        assertEquals(CHAMPION, BigMoments.headline(reached(6, left = 1, paid = 1) - reached(6, left = 2, paid = 1)))
        // Eight paid at a table of nine: the bubble and the final table together
        assertEquals(FINAL_TABLE, BigMoments.headline(setOf(BUBBLE, FINAL_TABLE)))
        assertEquals(IN_THE_MONEY, BigMoments.headline(setOf(FINAL_TABLE, IN_THE_MONEY)))
        assertNull(BigMoments.headline(emptySet()))
    }

    @Test
    fun `the champion's cue is the champion's, every other moment shares one`() {
        assertEquals(setOf(CueEvent.BIG_MOMENT), (BigMoment.entries - CHAMPION).map { it.cue }.toSet())
        assertEquals(CueEvent.CHAMPION, CHAMPION.cue)
    }

    private data class Night(val players: Int, val paid: Int, val seats: Int)

    /**
     * Any night, one knockout at a time down to a champion: every moment that applies to it is
     * reached once and stays reached, in the order the field gets there; walked back up (Undo after
     * Undo) each goes again, and nothing is new on the way up.
     */
    @Test
    fun `one knockout at a time, each moment comes once, and Undo takes them back in turn`() =
        forAll(seed = 2026_1009_111L, iterations = 300, gen = nights) { night ->
            val field = { left: Int -> Field(night.players, left, night.paid, night.seats) }
            var before = BigMoments.reached(field(night.players))
            expect(before.isEmpty()) { "at the start: $before" }
            val seen = mutableListOf<BigMoment>()
            for (left in night.players - 1 downTo 1) {
                val now = BigMoments.reached(field(left))
                expect(now.containsAll(before)) { "a knockout to $left left lost ${before - now}" }
                seen += now - before
                before = now
            }
            expect(seen.size == seen.toSet().size) { "a moment came twice: $seen" }
            expect(CHAMPION in seen && (night.players <= 2 || HEADS_UP in seen)) { "no champion or no heads-up: $seen" }
            for (left in 2..night.players) {
                val now = BigMoments.reached(field(left))
                expect(before.containsAll(now)) { "an Undo to $left left brought ${now - before}" }
                before = now
            }
            expect(before.isEmpty()) { "everyone back in, still reached: $before" }
        }

    private companion object {
        val nights = Arb.bind(Arb.int(2..30), Arb.int(0..30), Arb.int(3..10)) { players, paid, seats ->
            Night(players, paid.coerceAtMost(players), seats)
        }
    }
}
