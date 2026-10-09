package com.huntercoles.pokerpayout.core.domain.model

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.utils.FormatUtils

/**
 * What the prize pool is rounded to. Every place below 1st gets a multiple of [unitCents], and 1st
 * gets whatever is left, so the table always adds up to the pool to the cent. The names are saved
 * data (they say dollars, but a unit is one of whatever the host's currency is, PP-114).
 */
enum class PayoutRounding(val unitCents: Long) {
    ONE_DOLLAR(unitCents = 100L),
    FIVE_DOLLARS(unitCents = 500L),
    TEN_DOLLARS(unitCents = 1_000L);

    /** "$1", "5 €", "¥10": the unit in the host's currency. */
    val label: String get() = FormatUtils.formatMoney(unitCents)

    companion object {
        val DEFAULT = ONE_DOLLAR

        fun fromUnitCents(unitCents: Long): PayoutRounding =
            entries.firstOrNull { it.unitCents == unitCents } ?: DEFAULT
    }
}

/** How many places to pay. */
object PayoutPlaces {
    /** The longest table the presets and the editor offer (the 9 default weights). */
    val MAX: Int = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.size

    private const val PLAYERS_PER_PAID_PLACE = 3

    /** From this many players, at least two places are paid (PP-086). */
    private const val TWO_PLACES_FROM = 5

    /**
     * About a third of the field: the usual home-game guideline ("pay roughly 1/3 at home"; hosts
     * pay 20-33% of the field, see reports/research.md section 1). 4 players -> 1, 9 -> 3, 30 -> 9.
     *
     * From 5 players at least two places are paid (PP-086). A third of 5 is 1.67, nearer two than
     * one, and winner-takes-all sent four of five players home with nothing. Only 5 changes: 6 to 8
     * already paid two, and every other count is the plain third it always was.
     */
    fun recommended(playerCount: Int): Int {
        val third = playerCount / PLAYERS_PER_PAID_PLACE
        val atLeast = if (playerCount >= TWO_PLACES_FROM) 2 else 1
        return maxOf(third, atLeast).coerceIn(1, MAX)
    }

    /** Never more places than players, so no money goes to a place nobody can finish in. */
    fun maxFor(playerCount: Int): Int = playerCount.coerceIn(1, MAX)
}

/**
 * One-tap payout structures. Each is a table of relative weights per number of places paid, 1 to
 * [PayoutPlaces.MAX], strictly decreasing and summing to 100 where possible.
 *
 * - [STANDARD] is the app's long-standing default (35/20/15/10/8/6/3/2/1, cut to the places paid):
 *   2 places 64/36, 3 places 50/29/21, close to the 50/30/20 hosts quote.
 * - [TOP_HEAVY] follows the 60/30/10 three-way split hosts use when the winner should take most.
 * - [FLAT] spreads the pool wider ("more sociable", per hosts on PokerChipForum): 45/32/23 for 3.
 *
 * Sources: reports/research.md, "Decide payouts before the bubble; use round numbers".
 */
enum class PayoutPreset(val label: String, private val table: List<List<Int>>) {
    TOP_HEAVY(label = "Top-heavy", table = TOP_HEAVY_TABLE),
    STANDARD(label = "Standard", table = STANDARD_TABLE),
    FLAT(label = "Flat", table = FLAT_TABLE);

    /** Weights for paying [places] places (clamped to 1..[PayoutPlaces.MAX]). */
    fun weightsFor(places: Int): List<Int> = table[places.coerceIn(1, PayoutPlaces.MAX) - 1]

    companion object {
        val DEFAULT = STANDARD

        /** The preset these weights came from, or null for hand-edited weights. */
        fun matching(weights: List<Int>): PayoutPreset? =
            entries.firstOrNull { weights.size in 1..PayoutPlaces.MAX && it.weightsFor(weights.size) == weights }
    }
}

private val STANDARD_TABLE: List<List<Int>> =
    (1..TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.size).map { TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(it) }

private val TOP_HEAVY_TABLE: List<List<Int>> = listOf(
    listOf(100),
    listOf(70, 30),
    listOf(60, 30, 10),
    listOf(55, 25, 13, 7),
    listOf(50, 24, 13, 8, 5),
    listOf(47, 23, 13, 8, 5, 4),
    listOf(45, 22, 12, 8, 6, 4, 3),
    listOf(44, 21, 12, 8, 6, 4, 3, 2),
    listOf(43, 21, 12, 8, 6, 4, 3, 2, 1)
)

private val FLAT_TABLE: List<List<Int>> = listOf(
    listOf(100),
    listOf(60, 40),
    listOf(45, 32, 23),
    listOf(36, 27, 21, 16),
    listOf(30, 24, 19, 15, 12),
    listOf(26, 21, 17, 14, 12, 10),
    listOf(23, 19, 16, 13, 11, 10, 8),
    listOf(21, 17, 15, 12, 11, 9, 8, 7),
    listOf(19, 16, 14, 12, 10, 9, 8, 7, 5)
)

/** A payout structure as the user picks it: weights, the preset they came from (if any), rounding. */
data class PayoutSettings(
    val weights: List<Int>,
    val preset: PayoutPreset?,
    val rounding: PayoutRounding
) {
    /**
     * The same structure paying [places] places (clamped to 1..[PayoutPlaces.MAX]): a preset's own
     * table for that many places, or hand-edited weights cut short or extended. Extended weights keep
     * falling (each new place weighs less than the one above, using the default weights where they
     * fit), so the result is always a valid structure.
     */
    fun withPlaces(places: Int): PayoutSettings {
        val target = places.coerceIn(1, PayoutPlaces.MAX)
        val resized = when {
            preset != null -> preset.weightsFor(target)
            target <= weights.size -> weights.take(target)
            else -> extended(weights.ifEmpty { PayoutPreset.DEFAULT.weightsFor(1) }, target)
        }
        return copy(weights = resized)
    }

    private fun extended(start: List<Int>, target: Int): List<Int> {
        // Room below the last weight for every new place: scale up (shares unchanged) if needed.
        val room = target - start.size
        val scale = if (start.last() > room) 1 else (room / start.last() + 1) * WEIGHT_SCALE_STEP
        val result = start.map { it * scale }.toMutableList()
        while (result.size < target) {
            val suggested = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.getOrElse(result.size) { 1 } * scale
            result += minOf(suggested, result.last() - 1).coerceAtLeast(1)
        }
        return result
    }

    private companion object {
        const val WEIGHT_SCALE_STEP = 10
    }
}

/** One row of the payout table. */
data class PayoutPlace(
    val place: Int,
    val amountCents: Long,
    val weight: Int,
    /** This place's share of the weights, in percent (the structure, before rounding). */
    val sharePercent: Double
) {
    val ordinal: String get() = ordinalOf(place)
}

/** The payout table for one prize pool. [places] always add up to [prizePoolCents] exactly. */
data class PayoutTable(
    val prizePoolCents: Long,
    val places: List<PayoutPlace>,
    val rounding: PayoutRounding
) {
    val totalCents: Long get() = places.sumOf { it.amountCents }

    /** What [place] pays; 0 for a place outside the table. */
    fun amountFor(place: Int): Long = places.getOrNull(place - 1)?.amountCents ?: 0L

    companion object {
        val EMPTY = PayoutTable(prizePoolCents = 0L, places = emptyList(), rounding = PayoutRounding.DEFAULT)
    }
}

private val TEENS = 11..13
private val ORDINAL_SUFFIXES = listOf("th", "st", "nd", "rd")
private const val HUNDRED = 100
private const val TEN = 10

/** 1 -> "1st", 2 -> "2nd", 11 -> "11th", 23 -> "23rd". */
fun ordinalOf(place: Int): String {
    val suffix = if (place % HUNDRED in TEENS) "th" else ORDINAL_SUFFIXES.getOrElse(place % TEN) { "th" }
    return "$place$suffix"
}
