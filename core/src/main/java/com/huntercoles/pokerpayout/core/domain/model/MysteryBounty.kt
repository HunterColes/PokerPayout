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
        else -> deal(players, players * bountyCents, unitFor(bountyCents))
    }

    /** [envelopes] without the ones already [drawn] (each drawn amount takes one envelope out). */
    fun remaining(envelopes: List<Long>, drawn: List<Long>): List<Long> {
        val left = envelopes.toMutableList()
        drawn.forEach { cents -> left.remove(cents) }
        return left
    }

    /**
     * Late entries and re-entries (PP-116): one envelope each, holding that entry's own bounty
     * ([bountiesCents]), biggest first; an entry without a bounty adds none. The envelopes already
     * dealt are never dealt again for them.
     */
    fun lateEnvelopes(bountiesCents: List<Long>): List<Long> = bountiesCents.filter { it > 0L }.sortedDescending()

    /**
     * The envelopes still in the pool for [players] players at [bountyCents], plus one for each late
     * entry ([late], [lateEnvelopes]), once [drawn] have been drawn: the envelopes without the drawn
     * ones.
     *
     * A player added or removed by the player count after the first draw changes the deal, and an
     * envelope already drawn may not be in the new one. Then the money left (the new pool minus
     * everything drawn) is dealt again, the same way, into one envelope for each one not yet drawn
     * (envelopes minus envelopes drawn), so the envelopes left never hold more than the pool has left.
     */
    fun left(players: Int, bountyCents: Long, drawn: List<Long>, late: List<Long> = emptyList()): List<Long> {
        // A deal is biggest first already, so without late entries this is the deal itself
        val dealt = (envelopes(players, bountyCents) + lateEnvelopes(late)).sortedDescending()
        val rest = remaining(dealt, drawn)
        val everyDrawFromThisDeal = rest.size + drawn.size == dealt.size
        val count = dealt.size - drawn.size
        return when {
            everyDrawFromThisDeal -> rest
            count <= 0 -> emptyList()
            else -> {
                val unit = unitFor(bountyCents.takeIf { it > 0L } ?: dealt.first())
                deal(count, (dealt.sum() - drawn.sum()).coerceAtLeast(0L), unit)
            }
        }
    }

    /** One of [envelopesLeft], each as likely as the next; null when there are none. */
    fun draw(envelopesLeft: List<Long>, random: Random): Long? =
        if (envelopesLeft.isEmpty()) null else envelopesLeft[random.nextInt(envelopesLeft.size)]

    /** The envelopes as "1 × $15, 2 × $6, 6 × $3": each amount with how many hold it, biggest first. */
    fun groups(envelopes: List<Long>): List<EnvelopeGroup> =
        envelopes.groupingBy { it }.eachCount()
            .map { (cents, count) -> EnvelopeGroup(cents, count) }
            .sortedByDescending { it.cents }

    /**
     * [count] envelopes (at least one) holding [totalCents] together, in whole [unit]s: a few big, some
     * middling, many small, biggest first. Too little to split that way, it is split evenly. Units that
     * don't divide evenly go to the first envelopes, one each; cents short of a unit (never in a first
     * deal) go to the first.
     */
    private fun deal(count: Int, totalCents: Long, unit: Long): List<Long> {
        val totalUnits = totalCents / unit
        val big = ((count + BIG_EVERY / 2) / BIG_EVERY).coerceIn(1, count)
        val middle = ((count + MIDDLE_EVERY / 2) / MIDDLE_EVERY).coerceAtMost(count - big)
        val small = count - big - middle
        val smallUnits = totalUnits / (big * BIG_WEIGHT + middle * MIDDLE_WEIGHT + small)
        val units = if (smallUnits < 1L) {
            spread(totalUnits, count)
        } else {
            val middleUnits = smallUnits * MIDDLE_WEIGHT
            val bigUnits = totalUnits - middle * middleUnits - small * smallUnits
            spread(bigUnits, big) + List(middle) { middleUnits } + List(small) { smallUnits }
        }
        val oddCents = totalCents % unit
        return units.mapIndexed { index, share -> share * unit + if (index == 0) oddCents else 0L }
    }

    /** [total] split into [count] parts as evenly as can be, the bigger ones first. */
    private fun spread(total: Long, count: Int): List<Long> =
        List(count) { index -> total / count + if (index < total % count) 1 else 0 }

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
