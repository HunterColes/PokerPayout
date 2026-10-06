package com.huntercoles.pokerpayout.tools.poker

/** The ten poker hands, best first (the Hand ranks list, S12). A royal flush is its own row. */
enum class HandRank {
    RoyalFlush,
    StraightFlush,
    FourOfAKind,
    FullHouse,
    Flush,
    Straight,
    ThreeOfAKind,
    TwoPair,
    OnePair,
    HighCard,
}

/**
 * How often each hand is the best five of seven cards (your two and the five on the board): the
 * number of the 133,784,560 possible seven-card hands it is ([TOTAL] = C(52, 7)). The straight flush
 * row leaves out royal flushes, which have their own.
 *
 * Constants, checked in `HandFrequenciesTest` against a count worked out from first principles
 * (every rank pattern and suit pattern) and in `HandEvaluatorTest` against an exhaustive deal of all
 * seven-card hands through the odds engine's evaluator.
 */
object SevenCardFrequencies {
    const val TOTAL: Long = 133_784_560L

    val counts: Map<HandRank, Long> = linkedMapOf(
        HandRank.RoyalFlush to 4_324L,
        HandRank.StraightFlush to 37_260L,
        HandRank.FourOfAKind to 224_848L,
        HandRank.FullHouse to 3_473_184L,
        HandRank.Flush to 4_047_644L,
        HandRank.Straight to 6_180_020L,
        HandRank.ThreeOfAKind to 6_461_620L,
        HandRank.TwoPair to 31_433_400L,
        HandRank.OnePair to 58_627_800L,
        HandRank.HighCard to 23_294_460L,
    )

    fun count(rank: HandRank): Long = counts.getValue(rank)

    /** Share of all seven-card hands, in percent. */
    fun percent(rank: HandRank): Double = count(rank) * PERCENT / TOTAL

    /** "1 in N": how many seven-card hands per one of these. */
    fun oneIn(rank: HandRank): Double = TOTAL.toDouble() / count(rank)

    private const val PERCENT = 100.0
}
