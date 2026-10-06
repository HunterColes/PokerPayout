package com.huntercoles.pokerpayout.tools.seats

import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.rankOf
import com.huntercoles.pokerpayout.tools.poker.suitOf
import kotlin.random.Random

/**
 * A seat draw (PP-036): every player at a table and a seat, and, once dealt, the cards each table
 * drew for the button. Seat 1 is the first seat; the numbers go round the table clockwise.
 */
data class SeatDraw(val tables: List<DrawnTable>) {
    /** Everyone seated, table by table, seat by seat. */
    val players: List<String> get() = tables.flatMap { it.seats }

    /** Whether every table has drawn for the button. */
    val buttonDealt: Boolean get() = tables.isNotEmpty() && tables.all { it.buttonCards != null }
}

/**
 * One table: [seats] holds who sits in seat 1, 2, ...; [buttonCards] the card dealt face up to each
 * seat for the button, in the same order, once dealt (see [Cards] for the encoding).
 */
data class DrawnTable(val number: Int, val seats: List<String>, val buttonCards: List<Int>? = null) {
    init {
        require(seats.size >= SeatDrawer.MIN_PLAYERS) { "a table needs at least ${SeatDrawer.MIN_PLAYERS} players" }
        require(buttonCards == null || buttonCards.size == seats.size) { "one button card per seat" }
    }

    /** Who has the button and the blinds, once the cards are dealt. */
    val button: ButtonResult? get() = buttonCards?.let(ButtonDraw::resultFor)
}

/**
 * The high-card draw at one table. Seats are numbered from 1.
 *
 * @property winningCard the card that took the button.
 * @property tiedOnRank whether another seat drew the same rank, so the suit decided.
 */
data class ButtonResult(
    val buttonSeat: Int,
    val smallBlindSeat: Int,
    val bigBlindSeat: Int,
    val winningCard: Int,
    val tiedOnRank: Boolean,
)

/** Random seats across as many tables as the players need, with the tables balanced. */
object SeatDrawer {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 60

    /**
     * Seats a table. Three at least: with two a side, an odd count would leave someone alone at a
     * table. Ten at most, the most a poker table seats.
     */
    const val MIN_SEATS_PER_TABLE = 3
    const val MAX_SEATS_PER_TABLE = 10

    /** Nine-handed, the usual full table for a tournament. */
    const val DEFAULT_SEATS_PER_TABLE = 9

    /** As few tables as hold [players] at [seatsPerTable] a table. */
    fun tableCount(players: Int, seatsPerTable: Int): Int {
        require(seatsPerTable > 0) { "seatsPerTable must be positive, was $seatsPerTable" }
        return (players.coerceAtLeast(0) + seatsPerTable - 1) / seatsPerTable
    }

    /**
     * How many sit at each table, largest first, balanced so no two tables differ by more than
     * one: 14 players at nine a table make 7 and 7, 19 make 7, 6 and 6.
     */
    fun tableSizes(players: Int, seatsPerTable: Int): List<Int> {
        val tables = tableCount(players, seatsPerTable)
        if (tables == 0) return emptyList()
        val base = players / tables
        val bigger = players % tables
        return List(tables) { if (it < bigger) base + 1 else base }
    }

    /**
     * Seats [players] at random: a fair shuffle, dealt into balanced tables ([tableSizes]) in order.
     * Every arrangement for those table sizes is equally likely, and the same [random] (the same
     * seed) always gives the same draw. Duplicate names are fine: each entry is one player.
     */
    fun drawSeats(players: List<String>, seatsPerTable: Int, random: Random): SeatDraw {
        require(players.size in MIN_PLAYERS..MAX_PLAYERS) { "players must be $MIN_PLAYERS..$MAX_PLAYERS, was ${players.size}" }
        require(seatsPerTable in MIN_SEATS_PER_TABLE..MAX_SEATS_PER_TABLE) { "seatsPerTable was $seatsPerTable" }
        val shuffled = players.shuffled(random)
        var next = 0
        val tables = tableSizes(players.size, seatsPerTable).mapIndexed { index, size ->
            DrawnTable(number = index + 1, seats = shuffled.subList(next, next + size).toList()).also { next += size }
        }
        return SeatDraw(tables)
    }

    /** Every table of [draw] deals for the button again, each from its own fresh deck. */
    fun dealButtons(draw: SeatDraw, random: Random): SeatDraw =
        SeatDraw(draw.tables.map { it.copy(buttonCards = ButtonDraw.deal(it.seats.size, random)) })
}

/**
 * The classic draw for the button: one card face up to each player, and the high card takes it.
 * On the same rank the suit decides, by the common house rule spades, hearts, diamonds, clubs.
 */
object ButtonDraw {
    /** The house order of suits, lowest first, as [suitOf] numbers them: clubs, diamonds, hearts, spades. */
    private val SUITS_LOW_TO_HIGH = listOf(CLUBS, DIAMONDS, HEARTS, SPADES)

    /** Higher rank first; on the same rank, ♠ > ♥ > ♦ > ♣. No two cards of a deck compare equal. */
    val highCard: Comparator<Int> = compareBy<Int>({ rankOf(it) }, { SUITS_LOW_TO_HIGH.indexOf(suitOf(it)) })

    /** [seats] cards off the top of a freshly shuffled deck, seat 1 first. */
    fun deal(seats: Int, random: Random): List<Int> {
        require(seats in 1..Cards.COUNT) { "seats must be 1..${Cards.COUNT}, was $seats" }
        return (0 until Cards.COUNT).shuffled(random).take(seats)
    }

    /**
     * The button goes to the high card. The two seats after it clockwise post the small and big
     * blinds; heads-up, the button posts the small blind and the other player the big blind.
     */
    fun resultFor(cards: List<Int>): ButtonResult {
        require(cards.size >= SeatDrawer.MIN_PLAYERS) { "the button needs at least ${SeatDrawer.MIN_PLAYERS} players" }
        require(cards.toSet().size == cards.size) { "the cards come from one deck: $cards" }
        val winner = cards.indices.maxWith(compareBy(highCard) { cards[it] })
        val seats = cards.size
        val smallBlind = if (seats == 2) winner else (winner + 1) % seats
        val bigBlind = (smallBlind + 1) % seats
        return ButtonResult(
            buttonSeat = winner + 1,
            smallBlindSeat = smallBlind + 1,
            bigBlindSeat = bigBlind + 1,
            winningCard = cards[winner],
            tiedOnRank = cards.count { rankOf(it) == rankOf(cards[winner]) } > 1,
        )
    }
}

private const val CLUBS = 0
private const val DIAMONDS = 1
private const val HEARTS = 2
private const val SPADES = 3
