package com.huntercoles.pokerpayout.core.domain.players

import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.history.Season
import java.time.LocalDate

/** Players' names as the app keeps them (PP-110). */
object PlayerNames {
    /** Longer than any name at a home game. */
    const val MAX_LENGTH = 40

    private val Placeholder = Regex("^Player \\d+$")
    private val Spaces = Regex("\\s+")

    /** "Player 3", or nothing at all: a seat nobody named, never anyone's name. */
    fun isPlaceholder(name: String): Boolean = name.isBlank() || Placeholder.matches(name.trim())

    /** [name] as saved: trimmed, single spaces, at most [MAX_LENGTH] characters. */
    fun clean(name: String): String = name.trim().replace(Spaces, " ").take(MAX_LENGTH).trim()
}

/** A name the Bank has used (typed, or picked for tonight), and the last day it did. */
data class KnownPlayer(val name: String, val lastSeen: LocalDate)

/**
 * Someone the host has played with (PP-110): from the saved nights in History and the names used in
 * the Bank, with the names merged in History as one person.
 *
 * @property name the name shown: the latest spelling of the name kept.
 * @property key who they are ([PlayerMerges.key]): a name with this key is them.
 * @property nights the saved nights they played, all time.
 * @property recentNights how many of the latest [Roster.RECENT_NIGHTS] saved nights they played.
 * @property lastPlayed the day of the latest saved night they played; null when none.
 * @property lastSeen the latest day they played or the Bank used their name; null when neither.
 */
data class Regular(
    val name: String,
    val key: String,
    val nights: Int,
    val recentNights: Int,
    val lastPlayed: LocalDate?,
    val lastSeen: LocalDate?,
)

/**
 * The roster of regulars (PP-110): everyone in [SavedNight]s and [KnownPlayer]s, as one person per
 * merged name, regulars first. Seats nobody named ("Player 3") are nobody.
 *
 * The order: who played most of the latest [RECENT_NIGHTS] saved nights, then who was seen last
 * (played, or named in the Bank), then who played most nights of all, then by name. So the people who
 * come most weeks lead, a newcomer from last week comes before someone who stopped coming, and a name
 * only ever typed in the Bank comes after everyone who has a saved night lately.
 */
object Roster {
    const val RECENT_NIGHTS = 10

    private val order = compareByDescending<Regular> { it.recentNights }
        .thenByDescending { it.lastSeen }
        .thenByDescending { it.nights }
        .thenBy { it.key }

    fun of(nights: List<SavedNight>, known: List<KnownPlayer>, merges: PlayerMerges = PlayerMerges.NONE): List<Regular> {
        val tallies = LinkedHashMap<String, Tally>()
        Season.latestFirst(nights).forEachIndexed { index, night ->
            val people = Season.people(night, merges)
            night.players.forEachIndexed { at, player ->
                if (!PlayerNames.isPlaceholder(player.name)) {
                    tallies.getOrPut(people[at]) { Tally(people[at], merges.resolve(player.name)) }
                        .played(player.name, night.date, index)
                }
            }
        }
        known.sortedByDescending { it.lastSeen }.forEach { player ->
            if (!PlayerNames.isPlaceholder(player.name)) {
                val person = merges.key(player.name)
                tallies.getOrPut(person) { Tally(person, merges.resolve(player.name)) }.seen(player.name, player.lastSeen)
            }
        }
        return tallies.values.filterNot { PlayerNames.isPlaceholder(it.name) }.map { it.regular() }.sortedWith(order)
    }

    /**
     * The latest saved night [a] and [b] both played, as [merges] has them; null when they never
     * played the same night, and so could be one person.
     */
    fun sharedNight(nights: List<SavedNight>, a: String, b: String, merges: PlayerMerges = PlayerMerges.NONE): SavedNight? {
        val first = merges.key(a)
        val second = merges.key(b)
        if (first == second) return null
        return Season.latestFirst(nights).firstOrNull { night ->
            val people = night.players.map { merges.key(it.name) }
            first in people && second in people
        }
    }

    /**
     * True when [a] and [b] look like two spellings of one name: one starts the other ("Mike",
     * "Mike R."), they share a first word, or they are a letter or two apart ("Danny", "Dany").
     */
    fun looksAlike(a: String, b: String): Boolean {
        val first = Season.key(a)
        val second = Season.key(b)
        if (first.isEmpty() || second.isEmpty() || first == second) return false
        val sameStart = first.startsWith(second) || second.startsWith(first)
        val sameFirstWord = first.substringBefore(' ') == second.substringBefore(' ')
        val close = minOf(first.length, second.length) >= MIN_CLOSE_LENGTH && distance(first, second) <= MAX_TYPOS
        return sameStart || sameFirstWord || close
    }

    /** Letters added, removed or changed to turn [a] into [b] (Levenshtein). */
    internal fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, x ->
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            b.forEachIndexed { j, y ->
                current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + if (x == y) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    private const val MIN_CLOSE_LENGTH = 3
    private const val MAX_TYPOS = 2

    private class Tally(val key: String, var name: String) {
        private var spelled = false
        private var lastNight = -1
        var nights = 0
        var recent = 0
        var lastPlayed: LocalDate? = null
        var lastSeen: LocalDate? = null

        /**
         * They played the [night]th night, the latest first (0), so the first spelling of the name kept
         * is the latest. A name twice in one night is one night.
         */
        fun played(spelling: String, day: LocalDate, night: Int) {
            if (night != lastNight) {
                nights++
                if (night < RECENT_NIGHTS) recent++
                lastNight = night
            }
            lastPlayed = lastPlayed?.let { maxOf(it, day) } ?: day
            seenOn(day)
            spell(spelling)
        }

        fun seen(spelling: String, day: LocalDate) {
            seenOn(day)
            spell(spelling)
        }

        private fun seenOn(day: LocalDate) {
            lastSeen = lastSeen?.let { maxOf(it, day) } ?: day
        }

        private fun spell(spelling: String) {
            if (!spelled && Season.key(spelling) == key) {
                name = spelling.trim()
                spelled = true
            }
        }

        fun regular() = Regular(name, key, nights, recent, lastPlayed, lastSeen)
    }
}
