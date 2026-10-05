package com.huntercoles.pokerpayout.tools.poker

import kotlin.random.Random
import kotlinx.coroutines.runBlocking

/**
 * The single seam between the golden tests and the code under test.
 *
 * The golden tests only talk to [OddsSubject] in card-string terms ("As Kd 9c"). This
 * adapter is the only file that changes when the engine is replaced, so the same
 * assertions run unchanged against the old evaluator (where they must fail) and the new
 * one (where they must pass).
 *
 * This version drives the ORIGINAL engine: `evaluateBest` (21 five-card subsets) and
 * `simulateEquity` (Monte Carlo only).
 */
internal object OddsSubject {

    /** Strength of the best five-card hand among [hand] (5..7 cards). Higher wins; equal splits. */
    fun strength(hand: String): Int = evaluateBest(parse(hand))

    fun category(hand: String): GoldenCategory = categoryOfStrength(strength(hand))

    fun categoryOfStrength(strength: Int): GoldenCategory = GoldenCategory.entries[strength ushr 24]

    private val deck: List<Card> = fullDeck()

    /** Strength of the hand made of `deck[i]` for each i in [indices] (any fixed 52-card order). */
    fun strengthOfDeckIndices(indices: IntArray): Int = evaluateBest(indices.map { deck[it] })

    /**
     * Exhaustive equity: every remaining runout is dealt exactly once. The old engine has no
     * enumeration mode, so the adapter enumerates runouts itself and asks the old
     * evaluator for each showdown.
     */
    fun exact(holes: List<String>, board: String = ""): Showdown {
        val holeCards = holes.map(::parse)
        val boardCards = parse(board)
        val used = holeCards.flatten() + boardCards
        require(used.size == used.toSet().size) { "duplicate cards" }
        val rest = fullDeck().filterNot { it in used }
        val need = 5 - boardCards.size
        val n = holes.size
        val wins = LongArray(n)
        val ties = LongArray(n)
        val shares = LongArray(n)
        var deals = 0L
        forEachCombination(rest, need) { extra ->
            val fullBoard = boardCards + extra
            val s = holeCards.map { evaluateBest(it + fullBoard) }
            val best = s.max()
            val winners = s.indices.filter { s[it] == best }
            deals++
            if (winners.size == 1) {
                wins[winners[0]]++
                shares[winners[0]] += Showdown.SHARE_UNIT
            } else {
                for (w in winners) {
                    ties[w]++
                    shares[w] += Showdown.SHARE_UNIT / winners.size
                }
            }
        }
        return Showdown(deals, wins.toList(), ties.toList(), shares.toList(), exact = true)
    }

    /** Heads-up preflop odds, the way the app computes them (old engine: 10k-sample Monte Carlo). */
    fun preflop(a: String, b: String): Showdown = runBlocking {
        val iterations = 20_000
        val r = simulateEquity(listOf(parse(a), parse(b)), emptyList(), iterations, Random(42))
        val sims = r[0].simulations.toLong()
        // Heads-up, so every tie is a two-way split.
        val shares = r.map { it.wins * Showdown.SHARE_UNIT + it.ties * (Showdown.SHARE_UNIT / 2) }
        Showdown(sims, r.map { it.wins.toLong() }, r.map { it.ties.toLong() }, shares, exact = false)
    }

    private fun parse(s: String): List<Card> =
        s.split(' ').filter { it.isNotBlank() }.map { Card(it[0], it[1]) }

    private fun <T> forEachCombination(items: List<T>, k: Int, action: (List<T>) -> Unit) {
        val idx = IntArray(k)
        fun go(start: Int, depth: Int) {
            if (depth == k) {
                action(List(k) { items[idx[it]] })
                return
            }
            for (i in start..items.size - (k - depth)) {
                idx[depth] = i
                go(i + 1, depth + 1)
            }
        }
        go(0, 0)
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
