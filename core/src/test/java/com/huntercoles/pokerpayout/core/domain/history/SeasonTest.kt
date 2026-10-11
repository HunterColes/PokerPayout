package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.domain.history.Nights.night
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The season's points (PP-037): players minus place plus 1 a night, added up per player across the
 * nights of the season; names match after trimming and ignoring case; players level on points share
 * a rank; the player of the year is the top of the year. Names merged in History (PP-110) add up under
 * the name kept, never twice in one night, and taken apart again they are as they were.
 */
class SeasonTest {

    private fun List<Standing>.lines() = map { "${it.rank} ${it.name} ${it.points}p ${it.nights}n ${it.wins}w" }

    @Test
    fun `points are the players in the night minus the place, plus 1`() {
        assertEquals(9, Season.points(place = 1, players = 9))
        assertEquals(8, Season.points(place = 2, players = 9))
        assertEquals(1, Season.points(place = 9, players = 9))
        assertEquals(2, Season.points(place = 1, players = 2))
        assertEquals(1, Season.points(place = 2, players = 2))
    }

    @Test
    fun `a player's points add up over every night they played`() {
        val nights = listOf(
            night("2026-03-01", listOf("Dana", "Marcus", "Priya", "Theo"), id = 1),
            night("2026-04-01", listOf("Priya", "Dana", "Marcus"), id = 2),
            night("2026-05-01", listOf("Dana", "Theo"), id = 3),
        )
        // Dana 4 + 2 + 2, Priya 2 + 3, Marcus 3 + 1, Theo 1 + 1
        assertEquals(
            listOf("1 Dana 8p 3n 2w", "2 Priya 5p 2n 1w", "3 Marcus 4p 2n 0w", "4 Theo 2p 2n 0w"),
            Season.standings(nights).lines(),
        )
    }

    @Test
    fun `players level on points share a rank, and the next rank counts them both`() {
        val nights = listOf(
            night("2026-03-01", listOf("Dana", "Marcus", "Priya"), id = 1),
            night("2026-04-01", listOf("Marcus", "Dana", "Priya"), id = 2),
            night("2026-05-01", listOf("Theo", "Priya", "Jo"), id = 3),
        )
        // Dana 3 + 2 = 5, Marcus 2 + 3 = 5, Priya 1 + 1 + 2 = 4, Theo 3, Jo 1
        assertEquals(
            listOf("1 Dana 5p 2n 1w", "1 Marcus 5p 2n 1w", "3 Priya 4p 3n 0w", "4 Theo 3p 1n 1w", "5 Jo 1p 1n 0w"),
            Season.standings(nights).lines(),
        )
    }

    @Test
    fun `level on points, more wins are listed first, then the name, all at the same rank`() {
        val nights = listOf(
            night("2026-03-01", listOf("Ann", "Ben", "Cy"), id = 1),
            night("2026-04-01", listOf("Cy", "Ben", "Ann"), id = 2),
        )
        // 4 points each: Ann 3 + 1 and Cy 1 + 3 with a win each, Ben 2 + 2 with none
        assertEquals(listOf("1 Ann 4p 2n 1w", "1 Cy 4p 2n 1w", "1 Ben 4p 2n 0w"), Season.standings(nights).lines())
    }

    @Test
    fun `names match after trimming and ignoring case, shown as the latest night spells them`() {
        val nights = listOf(
            night("2026-05-01", listOf("DANA", "marcus "), id = 3),
            night("2026-03-01", listOf(" dana", "Marcus"), id = 1),
            night("2026-04-01", listOf("Marcus", "Dana"), id = 2),
        )
        assertEquals(listOf("1 DANA 5p 3n 2w", "2 marcus 4p 3n 1w"), Season.standings(nights).lines())
        // Exactly, though: a different spelling is a different player
        val other = listOf(night("2026-03-01", listOf("Dana", "Dana B."), id = 1))
        assertEquals(listOf("Dana", "Dana B."), Season.standings(other).map { it.name })
    }

    @Test
    fun `a name twice in one night counts both places but one night`() {
        val twice = night("2026-03-01", listOf("Sam", "Jo", "sam"), id = 1)
        assertEquals(listOf("1 Sam 4p 1n 1w", "2 Jo 2p 1n 0w"), Season.standings(listOf(twice)).lines())
    }

    @Test
    fun `a year counts its own nights only, and all time counts every night`() {
        val nights = listOf(
            night("2025-11-20", listOf("Priya", "Dana", "Theo"), id = 1),
            night("2025-12-31", listOf("Priya", "Theo"), id = 2),
            night("2026-01-01", listOf("Dana", "Priya"), id = 3),
        )
        assertEquals(listOf(2026, 2025), Season.years(nights))
        assertEquals(listOf("1 Dana 2p 1n 1w", "2 Priya 1p 1n 0w"), Season.standings(nights, 2026).lines())
        // Dana and Theo level on 2 points, neither with a win: by name
        assertEquals(listOf("1 Priya 5p 2n 2w", "2 Dana 2p 1n 0w", "2 Theo 2p 2n 0w"), Season.standings(nights, 2025).lines())
        assertEquals(listOf("1 Priya 6p 3n 2w", "2 Dana 4p 2n 1w", "3 Theo 2p 2n 0w"), Season.standings(nights).lines())
        assertEquals(emptyList<Standing>(), Season.standings(nights, 2024))
    }

    @Test
    fun `the player of the year is the top of the year, or everyone level there`() {
        val nights = listOf(
            night("2026-03-01", listOf("Dana", "Marcus", "Priya"), id = 1),
            night("2026-04-01", listOf("Marcus", "Dana", "Priya"), id = 2),
            night("2025-04-01", listOf("Priya", "Dana"), id = 3),
        )
        assertEquals(listOf("Dana", "Marcus"), Season.leaders(Season.standings(nights, 2026)).map { it.name })
        assertEquals(listOf("Priya"), Season.leaders(Season.standings(nights, 2025)).map { it.name })
        assertEquals(listOf("Dana"), Season.leaders(Season.standings(nights)).map { it.name })
        assertEquals(emptyList<Standing>(), Season.leaders(emptyList()))
    }

    private val mikes = listOf(
        night("2026-03-01", listOf("Mike R.", "Dana", "Priya"), id = 1),
        night("2026-04-01", listOf("Dana", "Mike", "Priya"), id = 2),
        night("2026-05-01", listOf("Priya", "Dana", "Mike"), id = 3),
    )

    /** Mike R. 3 points and a win in March; Mike 2 in April and 1 in May; Dana 2 + 3 + 2; Priya 1 + 1 + 3. */
    private val apart = listOf("1 Dana 7p 3n 1w", "2 Priya 5p 3n 1w", "3 Mike R. 3p 1n 1w", "3 Mike 3p 2n 0w")

    @Test
    fun `merged spellings add up, points, nights, wins and places counting for the name kept (PP-110)`() {
        assertEquals(apart, Season.standings(mikes).lines())
        val merged = PlayerMerges.NONE.merge("Mike R.", "Mike")
        assertEquals(
            listOf("1 Dana 7p 3n 1w", "2 Mike 6p 3n 1w", "3 Priya 5p 3n 1w"),
            Season.standings(mikes, merges = merged).lines(),
        )
        // Kept the other way round, the name shown is Mike R.
        val other = PlayerMerges.NONE.merge("Mike", "Mike R.")
        assertEquals("2 Mike R. 6p 3n 1w", Season.standings(mikes, merges = other).lines()[1])
        // A year counts its own nights, merged the same way
        assertEquals(Season.standings(mikes, merges = other), Season.standings(mikes, 2026, other))
        assertEquals(emptyList<Standing>(), Season.standings(mikes, 2025, other))
    }

    @Test
    fun `a merge made twice counts once, and taken back the standings are as they were`() {
        val merged = PlayerMerges.NONE.merge("Mike R.", "Mike")
        val twice = merged.merge("Mike R.", "Mike").merge("mike r.", "MIKE")
        assertEquals(Season.standings(mikes, merges = merged), Season.standings(mikes, merges = twice))
        assertEquals(apart, Season.standings(mikes, merges = merged.separate("Mike R.")).lines())
        assertEquals(apart, Season.standings(mikes, merges = PlayerMerges.NONE).lines())
    }

    @Test
    fun `two names merged that played one night still count once each that night`() {
        val nights = listOf(
            night("2026-03-01", listOf("Mike", "Mike R.", "Dana"), id = 1),
            night("2026-04-01", listOf("Mike R.", "Dana"), id = 2),
        )
        val merged = PlayerMerges.NONE.merge("Mike R.", "Mike")
        // March: both played, so each counts as themselves; April: Mike R. counts as Mike
        assertEquals(
            listOf("1 Mike 5p 2n 2w", "2 Dana 2p 2n 0w", "2 Mike R. 2p 1n 0w"),
            Season.standings(nights, merges = merged).lines(),
        )
        assertEquals(listOf("mike", "mike r.", "dana"), Season.people(nights[0], merged))
        assertEquals(listOf("mike", "dana"), Season.people(nights[1], merged))
    }
}
