package com.huntercoles.pokerpayout.tools.poker

/**
 * One seat in an odds request.
 *
 * @property cards 0, 1 or 2 known hole cards (see [Cards]). Missing cards are dealt at random
 *   from the live deck, so an empty list is a "random hand".
 * @property folded a folded seat does not contest the pot; its known cards are dead.
 */
data class Seat(
    val cards: List<Int> = emptyList(),
    val folded: Boolean = false,
) {
    companion object {
        fun of(cards: String, folded: Boolean = false) = Seat(Cards.parseAll(cards), folded)

        val RANDOM = Seat()
    }
}

/**
 * What to compute: who is in the hand, the board so far, and cards known to be out of play.
 *
 * @property board 0..5 community cards.
 * @property dead cards that can't come (burned, mucked, seen); never dealt.
 */
data class OddsRequest(
    val seats: List<Seat>,
    val board: List<Int> = emptyList(),
    val dead: List<Int> = emptyList(),
)

/**
 * @property maxSamples Monte Carlo stops after this many deals.
 * @property targetStdErr Monte Carlo also stops once every contestant's equity standard error
 *   is at or below this many percentage points; `0` disables the early stop.
 * @property exactBudget use exact enumeration when it needs at most this many hand
 *   evaluations (deals x contestants); `0` forces Monte Carlo.
 * @property seed Monte Carlo seed. Same request + seed + [maxSamples] gives identical
 *   snapshots on any device and thread count. `null` picks a random seed.
 */
data class OddsSettings(
    val maxSamples: Int = DEFAULT_MAX_SAMPLES,
    val targetStdErr: Double = 0.0,
    val exactBudget: Long = DEFAULT_EXACT_BUDGET,
    val seed: Long? = null,
) {
    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
        require(targetStdErr >= 0.0) { "targetStdErr must not be negative" }
        require(exactBudget >= 0) { "exactBudget must not be negative" }
    }

    companion object {
        const val DEFAULT_MAX_SAMPLES = 100_000

        /**
         * 20M evaluations: covers every all-known spot (heads-up preflop is the largest at
         * 1.7M boards x 2) and e.g. one random hand on the flop (1,081 x 990 x 2).
         */
        const val DEFAULT_EXACT_BUDGET = 20_000_000L
    }
}

/**
 * Odds for one seat, as a share of all deals considered.
 *
 * @property wins deals this seat won outright.
 * @property ties deals this seat split with at least one other seat.
 * @property potShares pots won, in units of 1/[OddsResult.SHARE_UNIT] of a pot, so an
 *   outright win adds 2520, a two-way split 1260 and a three-way split 840.
 * @property equityPct share of the pot this seat wins on average: win% plus its share of
 *   split pots. Contestants' equities add up to 100.
 * @property equityStdErr one standard error of [equityPct] in percentage points; 0 when exact.
 * @property handCategoryPct how often this seat's final (river) hand is each
 *   [HandCategory], indexed by ordinal, in percent; empty for a folded seat.
 */
data class PlayerOdds(
    val folded: Boolean,
    val wins: Long,
    val ties: Long,
    val potShares: Long,
    val winPct: Double,
    val tiePct: Double,
    val equityPct: Double,
    val equityStdErr: Double,
    val handCategoryPct: List<Double>,
)

/**
 * One snapshot of a calculation. Monte Carlo emits several, with growing [deals];
 * the last one has [complete] set. Exact enumeration emits a single, complete result.
 *
 * @property players one entry per request seat, in seat order (folded seats included).
 * @property deals deals evaluated so far (for an exact result: every possible deal).
 * @property exact the numbers are exact, not estimates.
 * @property seed the Monte Carlo seed used, so a run can be reproduced; `null` when exact.
 */
data class OddsResult(
    val players: List<PlayerOdds>,
    val deals: Long,
    val exact: Boolean,
    val complete: Boolean,
    val seed: Long?,
) {
    /** The largest equity standard error among contestants, in percentage points. */
    val maxStdErr: Double get() = players.filterNot { it.folded }.maxOfOrNull { it.equityStdErr } ?: 0.0

    companion object {
        /** lcm(1..10): any k-way split of a pot (k <= 10) is a whole number of units. */
        const val SHARE_UNIT = 2520L
    }
}

/** The request can't be computed: duplicate cards, too few players, a bad board and so on. */
class OddsInputException(message: String) : IllegalArgumentException(message)
