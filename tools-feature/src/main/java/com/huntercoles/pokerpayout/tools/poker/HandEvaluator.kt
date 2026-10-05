package com.huntercoles.pokerpayout.tools.poker

import java.lang.Integer.bitCount
import java.lang.Integer.numberOfLeadingZeros

/** Hold'em hand categories, weakest first; [ordinal] is the category number in a strength. */
enum class HandCategory(val displayName: String) {
    HIGH_CARD("High card"),
    PAIR("Pair"),
    TWO_PAIR("Two pair"),
    TRIPS("Three of a kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full house"),
    QUADS("Four of a kind"),
    STRAIGHT_FLUSH("Straight flush"),
}

/**
 * Allocation-free evaluator for 5 to 7 cards.
 *
 * A **strength** is an `Int` with a total order: higher wins, equal splits. Layout:
 * ```
 * bits 20..23  category (HandCategory.ordinal)
 * bits 16..19  1st rank  (0 = deuce .. 12 = ace)
 * bits 12..15  2nd rank
 * bits  8..11  3rd rank
 * bits  4.. 7  4th rank
 * bits  0.. 3  5th rank
 * ```
 * The ranks are the tie-breakers in significance order, so a plain `Int` comparison is a
 * lexicographic comparison of (category, r1, r2, ...). Per category they are:
 * straight flush / straight: top card (a wheel's top card is the five);
 * quads: quad rank, kicker; full house: trips rank, pair rank; flush / high card: the five
 * cards high to low; trips: trips rank, two kickers; two pair: high pair, low pair, kicker;
 * pair: pair rank, three kickers. Unused positions are zero.
 *
 * The work is a handful of bit operations on the four 13-bit suit masks plus two table
 * lookups: no branches on individual cards, no allocation.
 */
@Suppress("MagicNumber") // the shifts and masks are the strength layout documented above, not tunables
object HandEvaluator {

    /** Bit position of the category in a strength; `strength ushr CATEGORY_SHIFT` is its ordinal. */
    internal const val CATEGORY_SHIFT = 20

    private const val RANK_MASK = 0x1FFF

    /** Top card of the best straight in a 13-bit rank mask, or -1. Index: rank mask. */
    private val STRAIGHT_TOP = IntArray(1 shl 13) { straightTopSlow(it) }

    /**
     * The five highest ranks of a rank mask, packed as nibbles with the highest in bits
     * 16..19. Masks with fewer than five bits leave the low nibbles zero.
     */
    private val TOP5 = IntArray(1 shl 13) { topRanksSlow(it, 5) }

    /** Strength of the best five-card hand in [hand], a card-set mask holding 5..7 cards. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod") // one early return per category, strongest first
    fun evaluate(hand: Long): Int {
        val c = hand.toInt() and RANK_MASK
        val d = (hand ushr 16).toInt() and RANK_MASK
        val h = (hand ushr 32).toInt() and RANK_MASK
        val s = (hand ushr 48).toInt() and RANK_MASK

        // With at most 7 cards, a flush rules out quads and full houses (each would need
        // 8+ cards), so the flush branch can return immediately.
        val flush = when {
            bitCount(c) >= 5 -> c
            bitCount(d) >= 5 -> d
            bitCount(h) >= 5 -> h
            bitCount(s) >= 5 -> s
            else -> 0
        }
        if (flush != 0) {
            val top = STRAIGHT_TOP[flush]
            return if (top >= 0) {
                (HandCategory.STRAIGHT_FLUSH.ordinal shl 20) or (top shl 16)
            } else {
                (HandCategory.FLUSH.ordinal shl 20) or TOP5[flush]
            }
        }

        val ranks = c or d or h or s
        val quads = c and d and h and s
        if (quads != 0) {
            val q = highest(quads)
            return (HandCategory.QUADS.ordinal shl 20) or (q shl 16) or (highest(ranks xor (1 shl q)) shl 12)
        }

        // Ranks held at least twice / at least three times (no quads past this point).
        val twoPlus = (c and d) or (h and s) or ((c or d) and (h or s))
        val trips = (c and d and (h or s)) or (h and s and (c or d))
        if (trips != 0) {
            val t = highest(trips)
            val fill = twoPlus xor (1 shl t) // other trips and pairs can fill the boat
            if (fill != 0) {
                return (HandCategory.FULL_HOUSE.ordinal shl 20) or (t shl 16) or (highest(fill) shl 12)
            }
        }

        val straight = STRAIGHT_TOP[ranks]
        if (straight >= 0) return (HandCategory.STRAIGHT.ordinal shl 20) or (straight shl 16)

        if (trips != 0) {
            val t = highest(trips)
            // Two kickers: the top two nibbles of TOP5, moved to bits 8..15.
            return (HandCategory.TRIPS.ordinal shl 20) or (t shl 16) or ((TOP5[ranks xor (1 shl t)] ushr 4) and 0xFF00)
        }

        if (twoPlus != 0) {
            val p1 = highest(twoPlus)
            val rest = twoPlus xor (1 shl p1)
            if (rest != 0) {
                val p2 = highest(rest)
                val kicker = highest(ranks xor (1 shl p1) xor (1 shl p2))
                return (HandCategory.TWO_PAIR.ordinal shl 20) or (p1 shl 16) or (p2 shl 12) or (kicker shl 8)
            }
            // Three kickers: the top three nibbles of TOP5, moved to bits 4..15.
            return (HandCategory.PAIR.ordinal shl 20) or (p1 shl 16) or ((TOP5[ranks xor (1 shl p1)] ushr 4) and 0xFFF0)
        }

        return (HandCategory.HIGH_CARD.ordinal shl 20) or TOP5[ranks]
    }

    fun evaluate(cards: IntArray): Int = evaluate(checkedMask(cards.asList()))

    fun evaluate(cards: Collection<Int>): Int = evaluate(checkedMask(cards))

    fun category(strength: Int): HandCategory = HandCategory.entries[strength ushr CATEGORY_SHIFT]

    /** The tie-break ranks of [strength] that are in use for its category, most significant first. */
    fun ranks(strength: Int): List<Int> {
        val used = when (category(strength)) {
            HandCategory.STRAIGHT_FLUSH, HandCategory.STRAIGHT -> 1
            HandCategory.QUADS, HandCategory.FULL_HOUSE -> 2
            HandCategory.TRIPS, HandCategory.TWO_PAIR -> 3
            HandCategory.PAIR -> 4
            HandCategory.FLUSH, HandCategory.HIGH_CARD -> 5
        }
        return (0 until used).map { (strength ushr (16 - 4 * it)) and 0xF }
    }

    /** Short English description, e.g. "Two pair, kings and fives" or "Straight, nine high". */
    fun describe(strength: Int): String {
        val r = ranks(strength)
        return when (category(strength)) {
            HandCategory.STRAIGHT_FLUSH ->
                if (r[0] == Cards.ACE) "Royal flush" else "Straight flush, ${NAMES[r[0]]} high"
            HandCategory.QUADS -> "Four ${PLURALS[r[0]]}"
            HandCategory.FULL_HOUSE -> "Full house, ${PLURALS[r[0]]} full of ${PLURALS[r[1]]}"
            HandCategory.FLUSH -> "Flush, ${NAMES[r[0]]} high"
            HandCategory.STRAIGHT -> "Straight, ${NAMES[r[0]]} high"
            HandCategory.TRIPS -> "Three ${PLURALS[r[0]]}"
            HandCategory.TWO_PAIR -> "Two pair, ${PLURALS[r[0]]} and ${PLURALS[r[1]]}"
            HandCategory.PAIR -> "Pair of ${PLURALS[r[0]]}"
            HandCategory.HIGH_CARD -> "${NAMES[r[0]].replaceFirstChar { it.uppercaseChar() }} high"
        }
    }

    private fun highest(mask: Int): Int = 31 - numberOfLeadingZeros(mask)

    private fun checkedMask(cards: Collection<Int>): Long {
        require(cards.size in 5..7) { "Need 5 to 7 cards, got ${cards.size}" }
        val mask = Cards.mask(cards)
        require(Cards.count(mask) == cards.size) { "Duplicate card in ${cards.map(Cards::format)}" }
        return mask
    }

    private fun straightTopSlow(mask: Int): Int {
        for (top in Cards.ACE downTo 4) {
            if ((0..4).all { mask and (1 shl (top - it)) != 0 }) return top
        }
        val wheel = (1 shl Cards.ACE) or 0b1111 // A-2-3-4-5
        return if (mask and wheel == wheel) 3 else -1
    }

    private fun topRanksSlow(mask: Int, k: Int): Int {
        var m = mask
        var packed = 0
        repeat(k) {
            val r = if (m == 0) 0 else highest(m)
            packed = (packed shl 4) or r
            if (m != 0) m = m xor (1 shl r)
        }
        return packed
    }

    private val NAMES = listOf(
        "deuce", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "jack", "queen", "king", "ace",
    )
    private val PLURALS = listOf(
        "deuces", "threes", "fours", "fives", "sixes", "sevens", "eights", "nines", "tens", "jacks", "queens", "kings", "aces",
    )
}
