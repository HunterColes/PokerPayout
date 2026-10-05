package com.huntercoles.pokerpayout.tools.poker

import kotlinx.coroutines.runBlocking

/**
 * The single seam between the golden tests and the code under test.
 *
 * The golden tests only talk to [OddsSubject] in card-string terms ("As Kd 9c"). This
 * adapter is the only file that changes when the engine is replaced, so the same
 * assertions ran unchanged against the old evaluator (where 38 of them failed, see commit
 * "Add golden odds tests that expose the kicker-order bug (red)") and the new one.
 *
 * This version drives the new engine: [HandEvaluator] and [OddsEngine].
 */
internal object OddsSubject {

    private val engine = OddsEngine()

    /** Strength of the best five-card hand among [hand] (5..7 cards). Higher wins; equal splits. */
    fun strength(hand: String): Int = HandEvaluator.evaluate(Cards.parseAll(hand))

    fun category(hand: String): GoldenCategory = categoryOfStrength(strength(hand))

    fun categoryOfStrength(strength: Int): GoldenCategory =
        GoldenCategory.entries[HandEvaluator.category(strength).ordinal]

    /** Strength of the hand made of card i for each i in [indices] (deck order is the engine's). */
    fun strengthOfDeckIndices(indices: IntArray): Int = HandEvaluator.evaluate(Cards.mask(indices))

    /** Exhaustive equity through the engine's exact mode. */
    fun exact(holes: List<String>, board: String = ""): Showdown = run(holes, board, OddsSettings())

    /** Heads-up preflop odds the way the app gets them: the engine's default settings. */
    fun preflop(a: String, b: String): Showdown = run(listOf(a, b), "", OddsSettings())

    private fun run(holes: List<String>, board: String, settings: OddsSettings): Showdown {
        val request = OddsRequest(holes.map { Seat.of(it) }, Cards.parseAll(board))
        val r = runBlocking { engine.finalResult(request, settings) }
        return Showdown(
            deals = r.deals,
            wins = r.players.map { it.wins },
            ties = r.players.map { it.ties },
            equityShares = r.players.map { it.potShares },
            exact = r.exact,
        )
    }
}

internal enum class GoldenCategory {
    HIGH_CARD, PAIR, TWO_PAIR, TRIPS, STRAIGHT, FLUSH, FULL_HOUSE, QUADS, STRAIGHT_FLUSH
}

/**
 * Integer showdown tallies. [equityShares] are in 1/[SHARE_UNIT] pots, so a three-way split
 * adds exactly 840 to each winner and equity stays an exact integer.
 */
internal data class Showdown(
    val deals: Long,
    val wins: List<Long>,
    val ties: List<Long>,
    val equityShares: List<Long>,
    val exact: Boolean,
) {
    fun winPct(i: Int) = 100.0 * wins[i] / deals
    fun tiePct(i: Int) = 100.0 * ties[i] / deals
    fun equityPct(i: Int) = 100.0 * equityShares[i] / (deals * SHARE_UNIT)

    companion object {
        /** lcm(1..10): every k-way split of one pot is a whole number of units. */
        const val SHARE_UNIT = 2520L
    }
}
