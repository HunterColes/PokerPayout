package com.huntercoles.pokerpayout.core.domain.history

import java.util.Locale

/** One player's line in the season's standings. [rank] is shared by players level on points. */
data class Standing(
    val rank: Int,
    val name: String,
    val points: Int,
    val nights: Int,
    val wins: Int,
)

/**
 * The season's points standings (PP-037), with one fixed rule: in a night of N players, 1st scores N
 * points, 2nd N - 1, and so on down to 1 point for the first player out (players minus place, plus 1).
 * Everyone who plays scores, and a bigger night is worth more.
 *
 * A player is the same player on every night their name is, after trimming and ignoring case
 * ("dana " is Dana); the standings show the latest night's spelling. Players level on points share a
 * rank; the list puts more wins first, then the name.
 */
object Season {

    /** Points for finishing [place] of [players]. */
    fun points(place: Int, players: Int): Int = (players - place + 1).coerceAtLeast(1)

    /** How a name matches across nights. */
    fun key(name: String): String = name.trim().lowercase(Locale.ROOT)

    /** The years with a saved night, the latest first. */
    fun years(nights: List<SavedNight>): List<Int> = nights.map { it.date.year }.distinct().sortedDescending()

    /** The standings over the nights played in [year], or over every night when it is null. */
    fun standings(nights: List<SavedNight>, year: Int? = null): List<Standing> {
        val tallies = LinkedHashMap<String, Tally>()
        nights.filter { year == null || it.date.year == year }
            .sortedWith(compareByDescending<SavedNight> { it.date }.thenByDescending { it.id })
            .forEachIndexed { index, night ->
                night.players.forEach { player ->
                    // The latest night is read first, so its spelling is the one kept
                    val tally = tallies.getOrPut(key(player.name)) { Tally(player.name.trim()) }
                    tally.points += points(player.place, night.players.size)
                    if (player.place == 1) tally.wins++
                    if (tally.lastNight != index) tally.nights++
                    tally.lastNight = index
                }
            }
        val sorted = tallies.values.sortedWith(
            compareByDescending<Tally> { it.points }.thenByDescending { it.wins }.thenBy { key(it.name) },
        )
        var rank = 0
        return sorted.mapIndexed { index, tally ->
            if (index == 0 || tally.points != sorted[index - 1].points) rank = index + 1
            Standing(rank = rank, name = tally.name, points = tally.points, nights = tally.nights, wins = tally.wins)
        }
    }

    /** The top of [standings]: the player of the year, or several when they are level on points. */
    fun leaders(standings: List<Standing>): List<Standing> = standings.takeWhile { it.rank == 1 }

    private class Tally(val name: String) {
        var points = 0
        var nights = 0
        var wins = 0
        var lastNight = -1
    }
}
