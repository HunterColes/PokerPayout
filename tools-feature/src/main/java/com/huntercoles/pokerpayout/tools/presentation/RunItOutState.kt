package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.tools.poker.NextCardBreakdown
import com.huntercoles.pokerpayout.tools.poker.OddsRequest

/** Each seat's equity, in percent, once a street was out. Folded seats are 0. */
data class StreetEquity(val street: Street, val equityPct: List<Double>)

/** One board of "run it twice": the full five cards and each seat's share of that half of the pot. */
data class TwiceBoard(val board: List<Int>, val equityPct: List<Double>)

/**
 * Run it out (S10): the hand from the odds screen, dealt one street at a time from a seeded,
 * pre-shuffled runout, with each seat's exact equity after every street.
 *
 * The state machine: [DealNext][OddsCalculatorIntent.DealNext] shows the next street until the
 * river ([isComplete]); [RunAgain][OddsCalculatorIntent.RunAgain] deals a fresh runout from the
 * board you started on; [RunTwice][OddsCalculatorIntent.RunTwice] deals two complete boards from
 * one deck ([twice]).
 *
 * @property request the hand as it was when run it out started; its board is the starting board.
 * @property seed with [run], fixes the cards: the same seed and run always deal the same runout.
 * @property runout this run's cards still to come, already dealt face down (5 - starting board).
 * @property dealt how many of [runout] are face up.
 * @property history equity after each street shown so far, preflop first. The streets before the
 *   starting board are included, so the chart starts at preflop.
 * @property outs who leads after each possible next card, while one is to come on the flop or turn.
 * @property landscape the player asked for the propped-up landscape view.
 */
data class RunOutState(
    val request: OddsRequest,
    val seed: Long,
    val run: Int = 0,
    val runout: List<Int> = emptyList(),
    val dealt: Int = 0,
    val history: List<StreetEquity> = emptyList(),
    val twice: List<TwiceBoard>? = null,
    val outs: NextCardBreakdown? = null,
    val isDealing: Boolean = false,
    val landscape: Boolean = false,
) {
    /** The board face up now. */
    val board: List<Int> get() = request.board + runout.take(dealt)

    val street: Street get() = Street.of(board.size)

    /** The river is out (or the hand was run twice): nothing left to deal. */
    val isComplete: Boolean get() = twice != null || board.size == OddsTable.BOARD_SLOTS

    /** The street the next deal shows, or `null` once the river is out. */
    val nextStreet: Street? get() = if (isComplete) null else Street.entries.first { it.boardCards > board.size }

    /** The street run it out started on (the board it re-deals from). */
    val startStreet: Street get() = Street.of(request.board.size)

    /** Seats still in the hand. */
    val contestants: List<Int> get() = request.seats.indices.filter { !request.seats[it].folded }

    /** Equity now, per seat (empty until the first street is worked out). */
    val equity: List<Double> get() = history.lastOrNull()?.equityPct.orEmpty()

    /** How each seat's equity moved with the last street, in points; empty before the first deal. */
    val delta: List<Double>
        get() {
            if (history.size < 2) return emptyList()
            val (before, after) = history.takeLast(2)
            return after.equityPct.zip(before.equityPct) { a, b -> a - b }
        }

    /** The seats that won (or split) on the river; empty before it. */
    val winners: List<Int>
        get() = if (board.size == OddsTable.BOARD_SLOTS) {
            contestants.filter { (equity.getOrNull(it) ?: 0.0) > 0.0 }
        } else {
            emptyList()
        }

    /**
     * The seat ahead before the river came (the one that could "hold"), or `null`. Used to say
     * "Player 2 holds" rather than "wins" when the hand that was ahead stays ahead.
     */
    val leaderBeforeRiver: Int?
        get() {
            val before = history.getOrNull(history.size - 2)?.equityPct ?: return null
            val best = contestants.maxOfOrNull { before[it] } ?: return null
            return contestants.singleOrNull { before[it] == best }
        }
}
