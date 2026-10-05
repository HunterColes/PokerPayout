package com.huntercoles.pokerpayout.tools.poker

/**
 * Cards are plain `Int`s in `0..51`: `rank * 4 + suit`, where rank `0` is a deuce and `12`
 * an ace, and suit `0..3` is clubs, diamonds, hearts, spades.
 *
 * A *set* of cards is a `Long` bitmask with one bit per card at `suit * 16 + rank`. A hand is
 * the OR of its cards' bits, and the four 13-bit per-suit rank masks come out with shifts,
 * which is what [HandEvaluator] works on. Bits 13..15 of each 16-bit lane are always zero.
 */
object Cards {
    const val COUNT = 52
    const val RANK_CHARS = "23456789TJQKA"
    const val SUIT_CHARS = "cdhs"

    const val DEUCE = 0
    const val ACE = 12

    private val BITS = LongArray(COUNT) { 1L shl ((it and 3) * 16 + (it ushr 2)) }

    fun of(rank: Int, suit: Int): Int {
        require(rank in 0..12) { "rank must be 0..12, was $rank" }
        require(suit in 0..3) { "suit must be 0..3, was $suit" }
        return rank * 4 + suit
    }

    fun rank(card: Int): Int = card ushr 2

    fun suit(card: Int): Int = card and 3

    /** The bitmask bit of [card]. */
    fun bit(card: Int): Long = BITS[card]

    fun mask(cards: IntArray): Long {
        var m = 0L
        for (c in cards) m = m or BITS[c]
        return m
    }

    fun mask(cards: Iterable<Int>): Long {
        var m = 0L
        for (c in cards) m = m or BITS[c]
        return m
    }

    /** Number of cards in a card-set mask. */
    fun count(mask: Long): Int = java.lang.Long.bitCount(mask)

    /** The cards of a card-set mask, in ascending card order. */
    fun cardsOf(mask: Long): List<Int> = (0 until COUNT).filter { mask and BITS[it] != 0L }

    /**
     * Parses one card such as `"As"`, `"td"`, `"10h"` or `"Qc"`. Rank first, then suit
     * (`c`, `d`, `h`, `s`; case-insensitive).
     *
     * @throws IllegalArgumentException for anything else.
     */
    fun parse(text: String): Int {
        val t = text.trim()
        val rankText = t.dropLast(1)
        val suitChar = t.lastOrNull()?.lowercaseChar()
            ?: throw IllegalArgumentException("Empty card")
        val rank = when (rankText.uppercase()) {
            "10" -> 8
            else -> if (rankText.length == 1) RANK_CHARS.indexOf(rankText[0].uppercaseChar()) else -1
        }
        val suit = SUIT_CHARS.indexOf(suitChar)
        require(rank >= 0 && suit >= 0) { "Not a card: \"$text\"" }
        return rank * 4 + suit
    }

    /**
     * Parses a list of cards separated by spaces or commas (`"As Kd"`, `"As,Kd"`), or run
     * together (`"AsKd"`). An empty or blank string is an empty list.
     */
    fun parseAll(text: String): List<Int> {
        val tokens = text.split(' ', ',', '\t', '\n').filter { it.isNotBlank() }
        return tokens.flatMap { token ->
            if (token.length > 3 || (token.length == 3 && !token.startsWith("10"))) {
                token.chunked(2).map(::parse)
            } else {
                listOf(parse(token))
            }
        }
    }

    /** `"As"`, `"Td"`, ... */
    fun format(card: Int): String {
        require(card in 0 until COUNT) { "Not a card index: $card" }
        return "${RANK_CHARS[card ushr 2]}${SUIT_CHARS[card and 3]}"
    }

    fun format(cards: Iterable<Int>): String = cards.joinToString(" ") { format(it) }
}
