package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.Seat

/** What is in one card slot: nothing yet, a card left unknown on purpose, or a known card. */
sealed interface SlotValue {
    /** Still to fill. Counts as unknown for the odds. */
    data object Empty : SlotValue

    /** Deliberately unknown ("a random card"): the keypad skips it. Hole cards only. */
    data object Random : SlotValue

    data class Known(val card: Int) : SlotValue
}

/** A place a card can go: a seat's first or second hole card, or one of the five board slots. */
sealed interface SlotRef {
    data class Hole(val seat: Int, val index: Int) : SlotRef

    data class Board(val index: Int) : SlotRef
}

private const val FLOP_CARDS = 3
private const val TURN_CARDS = 4
private const val RIVER_CARDS = 5

/** The streets, named by how many board cards are out. */
enum class Street(val boardCards: Int) {
    PREFLOP(0),
    FLOP(FLOP_CARDS),
    TURN(TURN_CARDS),
    RIVER(RIVER_CARDS),
    ;

    companion object {
        /** The street a board of [cards] cards is on; a part-dealt flop (1 or 2 cards) is still preflop. */
        fun of(cards: Int): Street = entries.last { it.boardCards <= cards }
    }
}

/** One seat: two hole-card slots, and whether the player folded (a folded hand's cards are dead). */
data class SeatState(
    val cards: List<SlotValue> = listOf(SlotValue.Empty, SlotValue.Empty),
    val folded: Boolean = false,
) {
    /** The known hole cards, in slot order. */
    val known: List<Int> get() = cards.filterIsInstance<SlotValue.Known>().map { it.card }

    /** Both cards are known. */
    val isKnown: Boolean get() = known.size == HOLE_CARDS

    /** No card is known: the whole hand is unknown (typed as random, or not typed yet). */
    val isUnknown: Boolean get() = known.isEmpty()

    companion object {
        const val HOLE_CARDS = 2
    }
}

/**
 * Everything the odds screen knows about the hand: 2..10 seats and five board slots. Immutable;
 * every edit returns a new table. Cards are the engine's `Int`s ([Cards]).
 *
 * The keypad fills slots in [order]: each seat that hasn't folded, first card then second, then
 * the board left to right. The board fills in order: a turn can't go in before the flop.
 */
data class OddsTable(
    val seats: List<SeatState> = List(MIN_SEATS) { SeatState() },
    val board: List<SlotValue> = List(BOARD_SLOTS) { SlotValue.Empty },
) {
    init {
        require(seats.size in MIN_SEATS..MAX_SEATS) { "2 to 10 seats, got ${seats.size}" }
        require(board.size == BOARD_SLOTS) { "five board slots" }
        require(board.none { it == SlotValue.Random }) { "board cards are never random" }
    }

    /** The known board cards, left to right. */
    val boardCards: List<Int> get() = board.filterIsInstance<SlotValue.Known>().map { it.card }

    val street: Street get() = Street.of(boardCards.size)

    /** Seats still in the hand. */
    val contestants: List<Int> get() = seats.indices.filter { !seats[it].folded }

    /** Every known card on the table (hole cards, folded or not, and the board). */
    val usedCards: Set<Int> get() = (seats.flatMap { it.known } + boardCards).toSet()

    /** Nothing has been typed and nobody has folded. */
    val isEmpty: Boolean
        get() = seats.all { it.cards.all { c -> c == SlotValue.Empty } && !it.folded } && boardCards.isEmpty()

    /** Every seat still in the hand has both cards known. */
    val allHandsKnown: Boolean get() = contestants.all { seats[it].isKnown }

    fun slot(ref: SlotRef): SlotValue = when (ref) {
        is SlotRef.Hole -> seats[ref.seat].cards[ref.index]
        is SlotRef.Board -> board[ref.index]
    }

    /** Every slot the keypad visits, in order: live seats' hole cards, then the board. */
    fun order(): List<SlotRef> =
        contestants.flatMap { s -> List(SeatState.HOLE_CARDS) { SlotRef.Hole(s, it) } } +
            List(BOARD_SLOTS) { SlotRef.Board(it) }

    /**
     * The next empty slot after [after] in [order], wrapping round to the start; `null` when every
     * slot is filled (or random). With [after] `null` the search starts at the first slot.
     */
    fun nextOpen(after: SlotRef? = null): SlotRef? {
        val order = order()
        val start = after?.let { order.indexOf(it) + 1 } ?: 0
        return (order.drop(start) + order.take(start)).firstOrNull { slot(it) == SlotValue.Empty }
    }

    /**
     * Where a tap on [ref] should aim the keypad: the slot itself, except that an empty board
     * slot beyond the first empty one aims at that first one (the flop comes before the turn).
     */
    fun aim(ref: SlotRef): SlotRef {
        if (ref !is SlotRef.Board || board[ref.index] != SlotValue.Empty) return ref
        val firstOpen = board.indexOfFirst { it == SlotValue.Empty }
        return SlotRef.Board(minOf(firstOpen, ref.index))
    }

    /** True if [card] can go in [ref]: it isn't already somewhere else on the table. */
    fun canPlace(ref: SlotRef, card: Int): Boolean =
        card in 0 until Cards.COUNT && (card !in usedCards || slot(ref) == SlotValue.Known(card))

    fun with(ref: SlotRef, value: SlotValue): OddsTable = when (ref) {
        is SlotRef.Hole -> copy(
            seats = seats.mapIndexed { i, s ->
                if (i == ref.seat) s.copy(cards = s.cards.mapIndexed { j, c -> if (j == ref.index) value else c }) else s
            },
        )
        is SlotRef.Board -> {
            require(value != SlotValue.Random) { "board cards are never random" }
            copy(board = board.mapIndexed { i, c -> if (i == ref.index) value else c })
        }
    }

    /** Puts [card] in [ref], or returns this table unchanged if the card is already elsewhere. */
    fun place(ref: SlotRef, card: Int): OddsTable = if (canPlace(ref, card)) with(ref, SlotValue.Known(card)) else this

    /**
     * The engine request for this table, or `null` when there is nothing to work out: fewer than
     * two players in the hand, or no hole card known yet. Unknown cards (empty or random) are dealt
     * at random by the engine.
     */
    fun toRequest(): OddsRequest? {
        if (contestants.size < MIN_SEATS || contestants.all { seats[it].isUnknown }) return null
        return OddsRequest(seats = seats.map { Seat(it.known, it.folded) }, board = boardCards)
    }

    companion object {
        const val MIN_SEATS = 2
        const val MAX_SEATS = 10
        const val BOARD_SLOTS = 5
    }
}

/**
 * The docked keypad: which slot it is filling ([target]; `null` = keypad closed) and the rank
 * picked so far, waiting for a suit.
 */
data class KeypadState(val target: SlotRef? = null, val rank: Int? = null) {
    val isOpen: Boolean get() = target != null
}

/** Reads and writes [OddsTable] slots as the short strings the preferences store: "As", "?" (random), "-" (empty). */
internal object SlotCodec {
    private const val RANDOM = "?"
    private const val EMPTY = "-"

    fun encode(slots: List<SlotValue>): String = slots.joinToString(",") {
        when (it) {
            SlotValue.Empty -> EMPTY
            SlotValue.Random -> RANDOM
            is SlotValue.Known -> Cards.format(it.card)
        }
    }

    /** Parses [text] into exactly [size] slots. Unreadable tokens become empty; older saves ("As,Kd") still read. */
    fun decode(text: String, size: Int, allowRandom: Boolean): List<SlotValue> {
        val tokens = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val slots = tokens.map { token ->
            when (token) {
                RANDOM -> if (allowRandom) SlotValue.Random else SlotValue.Empty
                EMPTY -> SlotValue.Empty
                else -> runCatching { SlotValue.Known(Cards.parse(token)) }.getOrDefault(SlotValue.Empty)
            }
        }
        return (slots + List(size) { SlotValue.Empty }).take(size)
    }
}
