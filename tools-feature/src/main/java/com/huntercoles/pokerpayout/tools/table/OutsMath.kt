package com.huntercoles.pokerpayout.tools.table

/** Where the hand is: after the flop two cards are to come, after the turn one. */
enum class Street(val unseen: Int, val cardsToCome: Int) {
    /** 52 cards less your 2 and the 3 on the board. */
    Flop(unseen = 47, cardsToCome = 2),

    /** Less the turn too. */
    Turn(unseen = 46, cardsToCome = 1),
}

/**
 * [hits] chances out of [of], exactly: the ways an out comes, out of all the ways the cards can
 * come. [of] is never 0.
 */
data class Chance(val hits: Long, val of: Long) {
    val percent: Double get() = PERCENT * hits / of

    /** The odds against, "x to 1": misses for each hit; null when it can't miss or can't hit. */
    val againstToOne: Double? get() = if (hits in 1 until of) (of - hits).toDouble() / hits else null

    private companion object {
        const val PERCENT = 100.0
    }
}

/**
 * Outs as exact chances, and the rules of thumb next to them.
 *
 * With `u` unseen cards and `o` outs, one card hits `o` times in `u`. Two cards miss when both come
 * from the `u - o` blanks: `C(u-o, 2)` of the `C(u, 2)` pairs, so they hit in the rest.
 */
object OutsMath {
    const val MIN_OUTS = 1
    const val MAX_OUTS = 25

    /** The chance of at least one out in the next [cards] (1 or 2) of [unseen] cards. */
    fun hit(outs: Int, unseen: Int, cards: Int): Chance {
        require(outs in 0..unseen) { "outs must be 0..$unseen, was $outs" }
        require(cards in 1..2) { "one or two cards to come, not $cards" }
        if (cards == 1) return Chance(outs.toLong(), unseen.toLong())
        val all = pairs(unseen)
        return Chance(all - pairs(unseen - outs), all)
    }

    /** By the river: both cards from the flop, the one from the turn. */
    fun byRiver(outs: Int, street: Street): Chance = hit(outs, street.unseen, street.cardsToCome)

    /** The very next card. */
    fun nextCard(outs: Int, street: Street): Chance = hit(outs, street.unseen, cards = 1)

    /** The rule of 4 (two cards to come) or of 2 (one): outs times that, in percent. */
    fun ruleOfThumb(outs: Int, cards: Int): Int = outs * if (cards == 2) RULE_OF_FOUR else RULE_OF_TWO

    private fun pairs(n: Int): Long = n.toLong() * (n - 1) / 2

    private const val RULE_OF_FOUR = 4
    private const val RULE_OF_TWO = 2
}

/** Pot odds: what share of the pot a call has to win to pay for itself. */
object PotOdds {
    /**
     * The equity a call needs, in percent: the call's share of the pot it plays for, the [pot]
     * (with the bet you face) plus the [call]. Null without a call or a pot.
     */
    fun equityNeeded(pot: Long, call: Long): Double? =
        if (pot > 0L && call > 0L) PERCENT * call / (pot + call) else null

    /** The pot odds "x to 1": the [pot] for each chip you [call]. Null without a call or a pot. */
    fun toOne(pot: Long, call: Long): Double? = if (pot > 0L && call > 0L) pot.toDouble() / call else null

    private const val PERCENT = 100.0
}
