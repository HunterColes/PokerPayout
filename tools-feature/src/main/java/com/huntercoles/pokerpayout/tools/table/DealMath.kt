package com.huntercoles.pokerpayout.tools.table

import java.math.BigInteger
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * A deal at the end of a tournament (the chop): what each player still in takes now.
 *
 * @property icm by ICM, in cents, one per player, in the order the stacks were given.
 * @property chipChop by chips, in cents: everyone gets [floorCents], the rest goes by stack.
 * @property splitCents what is shared out now: the prizes left, less [forWinnerCents]. Both splits
 *   add up to it exactly.
 * @property forWinnerCents kept back and played for; the winner takes it on top of their share.
 * @property floorCents the smallest prize left (once [forWinnerCents] is taken off 1st).
 */
data class Deal(
    val icm: List<Long>,
    val chipChop: List<Long>,
    val splitCents: Long,
    val forWinnerCents: Long,
    val floorCents: Long,
)

/** Why a deal can't be worked out yet. */
sealed interface DealProblem {
    /** A player has no chips entered (or none at all). */
    data object MissingChips : DealProblem

    /** The prizes add up to nothing. */
    data object NoPrizes : DealProblem

    /** [place] pays more than the place above it. */
    data class PrizesGoUp(val place: Int) : DealProblem

    /** More kept back for the winner than 1st pays over 2nd ([maxCents]). */
    data class TooMuchForWinner(val maxCents: Long) : DealProblem
}

/**
 * The deal maker's maths, in whole cents: ICM (Malmuth-Harville) and a chip chop, side by side.
 *
 * - **ICM**: a player's chance of finishing 1st is their share of the chips; given who finished
 *   above, their chance of each next place is their share of the chips left. A player's ICM value
 *   is their expected prize over every finishing order.
 * - **Chip chop**: everyone takes the smallest prize left, which they'd win anyway; the rest is
 *   split in proportion to the chips.
 *
 * Saving some for the winner takes it off 1st before the split, so 1st can still pay no less
 * than 2nd. Both splits add up to the money shared out to the cent: each amount is rounded down
 * to the cent and the cents left over go to the largest remainders.
 */
object DealMath {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 10

    /** The most that can be kept back for the winner: what 1st pays over 2nd. */
    fun maxForWinner(prizes: List<Long>): Long =
        (prizes.getOrElse(0) { 0L } - prizes.getOrElse(1) { 0L }).coerceAtLeast(0L)

    /** What stops a deal for these [stacks] and [prizes] (one per place, 1st first), or null if none. */
    fun problem(stacks: List<Long?>, prizes: List<Long>, forWinnerCents: Long): DealProblem? {
        val goesUp = (1 until prizes.size).firstOrNull { prizes[it] > prizes[it - 1] }
        return when {
            stacks.any { it == null || it <= 0L } -> DealProblem.MissingChips
            prizes.sum() <= 0L -> DealProblem.NoPrizes
            goesUp != null -> DealProblem.PrizesGoUp(place = goesUp + 1)
            forWinnerCents > maxForWinner(prizes) -> DealProblem.TooMuchForWinner(maxForWinner(prizes))
            else -> null
        }
    }

    /**
     * The deal for [stacks] (chips, every one above 0) and [prizes] (cents, 1st first; places
     * past the end pay nothing, extra prizes are ignored), keeping [forWinnerCents] back.
     * Check [problem] first.
     */
    fun deal(stacks: List<Long>, prizes: List<Long>, forWinnerCents: Long = 0L): Deal {
        require(stacks.size in MIN_PLAYERS..MAX_PLAYERS) { "A deal needs $MIN_PLAYERS to $MAX_PLAYERS players" }
        require(stacks.all { it > 0L }) { "Every player needs chips" }
        val places = List(stacks.size) { prizes.getOrElse(it) { 0L } }
        val shared = places.mapIndexed { index, prize -> if (index == 0) prize - forWinnerCents else prize }
        val split = shared.sum()
        val floor = shared.min()
        val byChips = Apportion.byWeights(split - floor * stacks.size, stacks).map { it + floor }
        return Deal(
            icm = Apportion.byShares(split, Icm.expectedPrizes(stacks, shared)),
            chipChop = byChips,
            splitCents = split,
            forWinnerCents = forWinnerCents,
            floorCents = floor,
        )
    }
}

/** The Independent Chip Model, Malmuth-Harville. */
object Icm {
    /**
     * Each player's expected prize (in the prizes' units) for [stacks] (all above 0) and
     * [prizes] (1st first; places past the end pay nothing).
     *
     * Works through every set of players that can fill the top places, once each: the chance that
     * exactly the players in a set took the places above, then each other player's chance of the
     * next place. At most 2^10 sets for the 10 players a deal allows.
     */
    fun expectedPrizes(stacks: List<Long>, prizes: List<Long>): DoubleArray = Walk(stacks, prizes).expected()

    private class Walk(private val stacks: List<Long>, private val prizes: List<Long>) {
        private val sets = 1 shl stacks.size
        private val allChips = stacks.sum().toDouble()

        /** The chips of the players in each set. */
        private val chipsIn = DoubleArray(sets).also { chips ->
            for (set in 1 until sets) chips[set] = chips[set and (set - 1)] + stacks[Integer.numberOfTrailingZeros(set)]
        }

        /** The chance that exactly the players in each set took the places above everyone else. */
        private val chance = DoubleArray(sets).also { it[0] = 1.0 }
        private val expected = DoubleArray(stacks.size)

        fun expected(): DoubleArray {
            val places = minOf(prizes.size, stacks.size)
            for (set in 0 until sets) {
                val placed = Integer.bitCount(set)
                if (chance[set] > 0.0 && placed < places) nextPlace(set, prizes[placed].toDouble())
            }
            return expected
        }

        /** Spreads the chance of [set] over who finishes next, by their share of the chips left. */
        private fun nextPlace(set: Int, prize: Double) {
            val chipsLeft = allChips - chipsIn[set]
            for (player in stacks.indices) {
                val bit = 1 shl player
                if (set and bit == 0) {
                    val p = chance[set] * stacks[player] / chipsLeft
                    expected[player] += p * prize
                    chance[set or bit] += p
                }
            }
        }
    }
}

/** Splits whole cents in shares that add up to the total exactly. */
internal object Apportion {
    /**
     * [total] in proportion to [weights] (none below 0, not all 0), exactly: each part rounded
     * down, then a cent each to the largest remainders (ties: the larger weight, then the earlier).
     */
    fun byWeights(total: Long, weights: List<Long>): List<Long> {
        if (total == 0L) return List(weights.size) { 0L }
        val sum = BigInteger.valueOf(weights.sum())
        val exact = weights.map { BigInteger.valueOf(total).multiply(BigInteger.valueOf(it)).divideAndRemainder(sum) }
        val parts = exact.map { it[0].toLong() }.toLongArray()
        val order = weights.indices.sortedWith(
            compareByDescending<Int> { exact[it][1] }.thenByDescending { weights[it] }.thenBy { it },
        )
        handOut(parts, total - parts.sum(), order)
        return parts.toList()
    }

    /**
     * [total] in proportion to [shares] (none below 0), from floating-point values such as
     * expected prizes: each part rounded down, then a cent each to the largest remainders.
     * Remainders closer than a millionth of a cent count as equal, so equal shares worked out in a
     * different order still go to the earlier player first. All zero when the shares are.
     */
    fun byShares(total: Long, shares: DoubleArray): List<Long> {
        val sum = shares.sum()
        if (total == 0L || sum <= 0.0) return List(shares.size) { 0L }
        val exact = shares.map { total * (it / sum) }
        val parts = exact.map { floor(it).toLong() }.toLongArray()
        val order = shares.indices.sortedWith(
            compareByDescending<Int> { ((exact[it] - parts[it]) * MICRO).roundToLong() }.thenBy { it },
        )
        handOut(parts, total - parts.sum(), order)
        return parts.toList()
    }

    /** One cent each to the parts in [order], and round again if there are more cents than parts. */
    private fun handOut(parts: LongArray, cents: Long, order: List<Int>) {
        for (cent in 0 until cents) parts[order[(cent % order.size).toInt()]] += 1L
    }

    private const val MICRO = 1_000_000.0
}
