package com.huntercoles.pokerpayout.core.domain.players

import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The roster of regulars (PP-110): everyone from the saved nights and the Bank's names, as one person
 * per merged name; who came to most of the latest ten nights first, then who was seen last, then who
 * played most nights, then the name; seats nobody named are nobody.
 */
class RosterTest {

    private fun day(text: String) = LocalDate.parse(text)

    private fun List<Regular>.names() = map { it.name }

    @Test
    fun `who came to most of the latest nights leads, then who came last, then who played most, then the name`() {
        val nights = listOf(
            night("2026-01-10", listOf("Old Al", "Dana", "Priya"), id = 1),
            night("2026-01-17", listOf("Old Al", "Dana"), id = 2),
            night("2026-09-05", listOf("Dana", "Marcus", "Priya"), id = 3),
            night("2026-09-12", listOf("Marcus", "Dana", "Theo"), id = 4),
            night("2026-09-19", listOf("Priya", "Dana", "Theo"), id = 5),
            night("2026-10-03", listOf("Zoe", "Dana", "Marcus"), id = 6),
        )
        // Recent nights: Dana 6, Marcus 3, Priya 3, Theo 2, Old Al 2, Zoe 1. Marcus came last (Oct 3),
        // before Priya (Sep 19); Theo (Sep 19) before Old Al (Jan 17), both on two.
        assertEquals(listOf("Dana", "Marcus", "Priya", "Theo", "Old Al", "Zoe"), Roster.of(nights, emptyList()).names())

        val dana = Roster.of(nights, emptyList()).first()
        assertEquals(Regular("Dana", "dana", 6, 6, day("2026-10-03"), day("2026-10-03")), dana)
    }

    @Test
    fun `only the latest ten nights count as recent`() {
        // Ann played the first two of twelve nights, Ben only the latest
        val nights = (1..12).map { n ->
            val players = when (n) {
                1, 2 -> listOf("Ann", "Cy")
                12 -> listOf("Ben", "Cy")
                else -> listOf("Cy", "Di")
            }
            night("2026-01-%02d".format(n), players, id = n.toLong())
        }
        val roster = Roster.of(nights, emptyList())
        assertEquals(listOf("Cy", "Di", "Ben", "Ann"), roster.names())
        assertEquals(0, roster.first { it.name == "Ann" }.recentNights)
        assertEquals(2, roster.first { it.name == "Ann" }.nights)
        assertEquals(10, roster.first { it.name == "Cy" }.recentNights)
        assertEquals(12, roster.first { it.name == "Cy" }.nights)
    }

    @Test
    fun `names used in the Bank join the roster after everyone who played lately, the latest seen first`() {
        val nights = listOf(night("2026-10-03", listOf("Dana", "Marcus"), id = 1))
        val known = listOf(
            KnownPlayer("Bea", day("2026-10-01")),
            KnownPlayer("Cal", day("2026-10-08")),
            KnownPlayer("dana", day("2026-10-09")),
        )
        val roster = Roster.of(nights, known)
        assertEquals(listOf("Dana", "Marcus", "Cal", "Bea"), roster.names())
        // Dana keeps the saved night's spelling, seen in the Bank since
        assertEquals(day("2026-10-09"), roster.first().lastSeen)
        assertEquals(day("2026-10-03"), roster.first().lastPlayed)
        assertEquals(Regular("Cal", "cal", 0, 0, null, day("2026-10-08")), roster[2])
    }

    @Test
    fun `merged names are one regular, under the latest spelling of the name kept`() {
        val nights = listOf(
            night("2026-09-05", listOf("Mike R.", "Dana"), id = 1),
            night("2026-09-12", listOf("Dana", "mike"), id = 2),
            night("2026-09-19", listOf("Mike", "Dana"), id = 3),
        )
        val merges = PlayerMerges.NONE.merge("Mike R.", "Mike")
        val roster = Roster.of(nights, listOf(KnownPlayer("Mike R.", day("2026-10-01"))), merges)
        // Three nights each; Mike was seen last, as Mike R. in the Bank
        assertEquals(listOf("Mike", "Dana"), roster.names())
        val mike = roster.first()
        assertEquals(3, mike.nights)
        assertEquals(day("2026-10-01"), mike.lastSeen)
        assertEquals("mike", mike.key)
        // Without the merge they are two
        assertEquals(listOf("Dana", "Mike", "Mike R."), Roster.of(nights, emptyList()).names())
    }

    @Test
    fun `a name kept that only the Bank knows is shown as kept`() {
        val nights = listOf(night("2026-09-05", listOf("Mikey", "Dana"), id = 1))
        val merges = PlayerMerges.NONE.merge("Mikey", "Michael")
        assertEquals(listOf("Dana", "Michael"), Roster.of(nights, emptyList(), merges).names().sorted())
    }

    @Test
    fun `seats nobody named are nobody`() {
        val nights = listOf(night("2026-09-05", listOf("Dana", "Player 2", "Player 3"), id = 1))
        val known = listOf(KnownPlayer("Player 4", day("2026-10-01")), KnownPlayer(" ", day("2026-10-01")))
        assertEquals(listOf("Dana"), Roster.of(nights, known).names())
        assertTrue(PlayerNames.isPlaceholder("Player 12"))
        assertTrue(PlayerNames.isPlaceholder(" "))
        assertFalse(PlayerNames.isPlaceholder("Player One"))
        assertEquals("Mike R.", PlayerNames.clean("  Mike   R. "))
        assertEquals(PlayerNames.MAX_LENGTH, PlayerNames.clean("x".repeat(60)).length)
    }

    @Test
    fun `two names that played one night can't be one person`() {
        val nights = listOf(
            night("2026-09-05", listOf("Mike", "Dana"), id = 1),
            night("2026-09-12", listOf("Mike R.", "Mike", "Dana"), id = 2),
            night("2026-09-19", listOf("Mike R.", "Jo"), id = 3),
        )
        assertEquals(day("2026-09-12"), Roster.sharedNight(nights, "Mike", "mike r.")?.date)
        assertNull(Roster.sharedNight(nights, "Jo", "Mike"))
        assertNull(Roster.sharedNight(nights, "Mike", "Mike"))
        // Jo merged into Joanne: Joanne played with Mike R. (as Jo), never with Mike
        val merges = PlayerMerges.NONE.merge("Jo", "Joanne")
        assertNull(Roster.sharedNight(nights, "Joanne", "Mike", merges))
        assertEquals(day("2026-09-19"), Roster.sharedNight(nights, "Joanne", "Mike R.", merges)?.date)
    }

    @Test
    fun `names that look alike are told apart from names that don't`() {
        assertTrue(Roster.looksAlike("Mike", "Mike R."))
        assertTrue(Roster.looksAlike("Dan", "Danny"))
        assertTrue(Roster.looksAlike("Danny", "Dany"))
        assertTrue(Roster.looksAlike("Priya S", "priya k"))
        assertFalse(Roster.looksAlike("Dana", "Marcus"))
        assertFalse(Roster.looksAlike("Jo", "Al"))
        assertFalse(Roster.looksAlike("Dana", "dana"))
        assertEquals(3, Roster.distance("kitten", "sitting"))
    }
}
