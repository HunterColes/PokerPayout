package com.huntercoles.pokerpayout.core.domain.model

import kotlin.random.Random

/**
 * Mystery bounties (PP-035). Every player's bounty goes into one pool of envelopes, one envelope per
 * player, together worth players × bounty to the cent. Each knockout draws one envelope at random
 * from those left, and the eliminator wins what is in it.
 *
 * **The end of the night:** there is one envelope per player and a knockout draws at most one, so
 * envelopes never run out while two players are still in. The champion, whom nobody knocked out,
 * takes every envelope still in the pool: their own (the last one) and any left by knockouts
 * nobody was credited with, as the King's Bounty and the unclaimed bounties go to the champion in a
 * standard game (PP-055).
 */
object MysteryBounty {

    /**
     * The default envelopes for [players] players at [bountyCents] each, biggest first: a few big
     * prizes and many small ones. About one envelope in ten is big, two in ten are middling (two
     * small ones each) and the rest are small; the big ones hold what is left, at least five small
     * ones each. Amounts are whole "nice" units of at most a quarter of the bounty ($1 for a $5
     * bounty, $2.50 for $10), so nine players at $5 get 1 × $15, 2 × $6 and 6 × $3. A bounty too
     * small to split that way gives every envelope the bounty itself.
     */
    fun envelopes(players: Int, bountyCents: Long): List<Long> = when {
        players <= 0 || bountyCents <= 0L -> emptyList()
        else -> deal(players, bountyCents)
    }

    /** [envelopes] without the ones already [drawn] (each drawn amount takes one envelope out). */
    fun remaining(envelopes: List<Long>, drawn: List<Long>): List<Long> {
        val left = envelopes.toMutableList()
        drawn.forEach { cents -> left.remove(cents) }
        return left
    }

    /** One of [envelopesLeft], each as likely as the next; null when there are none. */
    fun draw(envelopesLeft: List<Long>, random: Random): Long? =
        if (envelopesLeft.isEmpty()) null else envelopesLeft[random.nextInt(envelopesLeft.size)]

    /** The envelopes as "1 × $15, 2 × $6, 6 × $3": each amount with how many hold it, biggest first. */
    fun groups(envelopes: List<Long>): List<EnvelopeGroup> =
        envelopes.groupingBy { it }.eachCount()
            .map { (cents, count) -> EnvelopeGroup(cents, count) }
            .sortedByDescending { it.cents }

    private fun deal(players: Int, bountyCents: Long): List<Long> {
        val unit = unitFor(bountyCents)
        val totalUnits = players * (bountyCents / unit)
        val big = ((players + BIG_EVERY / 2) / BIG_EVERY).coerceIn(1, players)
        val middle = ((players + MIDDLE_EVERY / 2) / MIDDLE_EVERY).coerceAtMost(players - big)
        val small = players - big - middle
        val smallUnits = totalUnits / (big * BIG_WEIGHT + middle * MIDDLE_WEIGHT + small)
        return if (smallUnits < 1L) {
            List(players) { bountyCents }
        } else {
            val middleUnits = smallUnits * MIDDLE_WEIGHT
            val bigUnits = totalUnits - middle * middleUnits - small * smallUnits
            // Units that don't divide evenly go to the first big envelopes, one each
            val bigs = List(big) { index -> bigUnits / big + if (index < bigUnits % big) 1 else 0 }
            (bigs + List(middle) { middleUnits } + List(small) { smallUnits }).map { it * unit }
        }
    }

    /** The biggest nice unit that divides the bounty and fits in it at least four times; else a cent. */
    private fun unitFor(bountyCents: Long): Long =
        NICE_UNITS.firstOrNull { unit -> unit * MIN_UNITS_PER_BOUNTY <= bountyCents && bountyCents % unit == 0L } ?: 1L

    /** [count] envelopes of [cents] each. */
    data class EnvelopeGroup(val cents: Long, val count: Int)

    /** One envelope in this many is a big one; one in [MIDDLE_EVERY] a middling one. */
    private const val BIG_EVERY = 10
    private const val MIDDLE_EVERY = 5

    /** A big envelope is worth at least this many small ones, a middling one exactly [MIDDLE_WEIGHT]. */
    private const val BIG_WEIGHT = 5
    private const val MIDDLE_WEIGHT = 2
    private const val MIN_UNITS_PER_BOUNTY = 4L
    private val NICE_UNITS = listOf(10_000L, 5_000L, 2_500L, 1_000L, 500L, 250L, 100L, 50L, 25L, 10L, 5L, 1L)
}
