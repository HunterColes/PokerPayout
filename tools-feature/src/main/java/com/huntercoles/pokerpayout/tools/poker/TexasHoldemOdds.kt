package com.huntercoles.pokerpayout.tools.poker

import kotlin.random.Random
import kotlinx.coroutines.*

/**
 * Core Texas Hold'em odds & evaluation logic (self-contained, original implementation).
 * Purpose: Provide clean API for UI layer without relying on previous removed logic.
 */

data class Card(val rank: Char, val suit: Char) {
    override fun toString(): String = "${rank}${suit}"
}

data class EquityResult(
    val wins: Int = 0,
    val ties: Int = 0,
    val simulations: Int = 0
) {
    val winPct: Double get() = if (simulations == 0) 0.0 else wins * 100.0 / simulations
    val tiePct: Double get() = if (simulations == 0) 0.0 else ties * 100.0 / simulations
}

/**
 * Simple rank ordering highest to lowest. We use characters for compact storage.
 */
private val RANK_ORDER = charArrayOf('A','K','Q','J','T','9','8','7','6','5','4','3','2')
private val RANK_VALUE: Map<Char, Int> = RANK_ORDER.withIndex().associate { (i, r) -> r to (14 - i) }
private val SUITS = charArrayOf('h','d','c','s')

fun fullDeck(): List<Card> = buildList(52) {
    for (r in RANK_ORDER) for (s in SUITS) add(Card(r, s))
}

/** Encode a 5-card hand to an Int for comparison: category (0..8) shifted + kicker pattern base-15. */
private fun encode(category: Int, ranks: List<Int>): Int {
    var value = category shl 24
    var shift = 0
    for (r in ranks) {
        value = value or (r shl shift)
        shift += 4 // fits up to rank 14
    }
    return value
}

private enum class Category { HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_KIND, STRAIGHT_FLUSH }

/** Evaluate best 5-card hand out of up to 7 cards (Texas Hold'em). */
fun evaluateBest(cards: List<Card>): Int {
    require(cards.size in 5..7)
    var best = 0
    val working = IntArray(cards.size) { it }
    fun choose(start: Int, depth: Int, buffer: IntArray) {
        if (depth == 5) {
            val picked = List(5) { cards[buffer[it]] }
            val v = evaluate5(picked)
            if (v > best) best = v
            return
        }
        for (i in start until cards.size) {
            buffer[depth] = working[i]
            choose(i + 1, depth + 1, buffer)
        }
    }
    choose(0, 0, IntArray(5))
    return best
}

/** Evaluate exactly 5 cards. */
private fun evaluate5(cards: List<Card>): Int {
    val ranks = cards.map { RANK_VALUE[it.rank]!! }.sortedDescending()
    val suits = cards.groupBy { it.suit }
    val isFlush = suits.any { it.value.size == 5 }

    // Straight detection (treat A-5 wheel)
    val distinct = ranks.distinct()
    var straightHigh: Int? = null
    if (distinct.size >= 5) {
        val ordered = distinct.sortedDescending()
        for (i in 0..ordered.size - 5) {
            if (ordered[i] - ordered[i + 4] == 4) {
                straightHigh = ordered[i]
                break
            }
        }
        // Wheel (A=14,5,4,3,2)
        if (straightHigh == null && ordered.containsAll(listOf(14,5,4,3,2))) straightHigh = 5
    }

    val counts = ranks.groupingBy { it }.eachCount().entries
        .groupBy { it.value } // frequency -> entries
    val four = counts[4]?.maxByOrNull { it.key }?.key
    val threes = counts[3]?.map { it.key }?.sortedDescending() ?: emptyList()
    val pairs = counts[2]?.map { it.key }?.sortedDescending() ?: emptyList()

    // Straight flush
    if (isFlush && straightHigh != null) return encode(Category.STRAIGHT_FLUSH.ordinal, listOf(straightHigh))
    // Four of a kind
    if (four != null) {
        val kicker = ranks.filter { it != four }.maxOrNull()!!
        return encode(Category.FOUR_KIND.ordinal, listOf(four, kicker))
    }
    // Full house
    if (threes.isNotEmpty() && (pairs.isNotEmpty() || threes.size >= 2)) {
        val triple = threes.first()
        val pair = if (threes.size >= 2) threes[1] else pairs.first()
        return encode(Category.FULL_HOUSE.ordinal, listOf(triple, pair))
    }
    // Flush
    if (isFlush) return encode(Category.FLUSH.ordinal, ranks)
    // Straight
    if (straightHigh != null) return encode(Category.STRAIGHT.ordinal, listOf(straightHigh))
    // Three of a kind
    if (threes.isNotEmpty()) {
        val kickers = ranks.filter { it != threes.first() }.take(2)
        return encode(Category.THREE_KIND.ordinal, listOf(threes.first()) + kickers)
    }
    // Two pair
    if (pairs.size >= 2) {
        val kicker = ranks.first { it != pairs[0] && it != pairs[1] }
        return encode(Category.TWO_PAIR.ordinal, listOf(pairs[0], pairs[1], kicker))
    }
    // One pair
    if (pairs.isNotEmpty()) {
        val kickers = ranks.filter { it != pairs[0] }.take(3)
        return encode(Category.ONE_PAIR.ordinal, listOf(pairs[0]) + kickers)
    }
    // High card
    return encode(Category.HIGH_CARD.ordinal, ranks)
}

/**
 * Monte Carlo equity simulation. If board has 5 cards, result is deterministic.
 * Identical hole ranks across players are coerced to tie (per project requirement).
 */
suspend fun simulateEquity(
    holes: List<List<Card>>, // each 0..2 cards
    board: List<Card>,       // 0..5 board cards
    iterations: Int = 10000,
    random: Random = Random
): List<EquityResult> = withContext(Dispatchers.Default) {
    require(holes.size >= 2)
    val used = holes.flatten() + board
    require(used.size == used.toSet().size) { "Duplicate cards provided" }
    val baseDeck = fullDeck().filterNot { it in used }

    val results = MutableList(holes.size) { EquityResult() }.toMutableList()
    val completeBoardNeeded = 5 - board.size
    val needHoleCards = holes.map { 2 - it.size }
    val allHolesComplete = needHoleCards.all { it == 0 }
    val deterministic = allHolesComplete && completeBoardNeeded == 0
    val sims = if (deterministic) 1 else iterations.coerceAtLeast(1)

    // Note: Players cannot have exactly identical hole cards in poker (deck has unique cards)
    // However, players can have identical hand types (e.g., both have AA with different suits).
    // This implementation does not optimize for identical hand types.

    // Parallel processing for better performance
    val numCores = Runtime.getRuntime().availableProcessors()
    val chunkSize = (sims / numCores).coerceAtLeast(1)
    val chunks = sims / chunkSize
    val remainder = sims % chunkSize

    val partialResults = MutableList(numCores) { MutableList(holes.size) { EquityResult() } }

    coroutineScope {
        val jobs = (0 until numCores).map { coreIndex ->
            async {
                val coreRandom = Random(random.nextLong()) // Each core gets its own random sequence
                val startSim = coreIndex * chunkSize
                val endSim = if (coreIndex < numCores - 1) startSim + chunkSize else startSim + chunkSize + remainder
                val localResults = MutableList(holes.size) { EquityResult() }

                for (sim in startSim until endSim) {
                    val deck = if (deterministic) baseDeck else baseDeck.shuffled(coreRandom)
                    var deckIndex = 0

                    // Deal missing hole cards
                    val finalHoles: List<List<Card>> = holes.mapIndexed { idx, h ->
                        if (h.size == 2) h else buildList {
                            addAll(h)
                            repeat(2 - h.size) { add(deck[deckIndex++]) }
                        }
                    }

                    // Deal remaining board
                    val finalBoard = if (completeBoardNeeded == 0) board else buildList {
                        addAll(board)
                        repeat(completeBoardNeeded) { add(deck[deckIndex++]) }
                    }

                    // Evaluate all showdowns
                    val strengths = finalHoles.map { evaluateBest(it + finalBoard) }
                    val max = strengths.maxOrNull()!!
                    val winnerIndexes = strengths.withIndex().filter { it.value == max }.map { it.index }

                    // Natural win/tie handling (removed prior forced tie rule for identical pocket ranks)
                    if (winnerIndexes.size == 1) {
                        val w = winnerIndexes.first()
                        localResults[w] = localResults[w].copy(wins = localResults[w].wins + 1, simulations = localResults[w].simulations + 1)
                        for (i in localResults.indices) if (i != w) localResults[i] = localResults[i].copy(simulations = localResults[i].simulations + 1)
                    } else {
                        winnerIndexes.forEach { w ->
                            localResults[w] = localResults[w].copy(ties = localResults[w].ties + 1, simulations = localResults[w].simulations + 1)
                        }
                        // Non-winners still increment simulation counter
                        for (i in localResults.indices) if (i !in winnerIndexes) {
                            localResults[i] = localResults[i].copy(simulations = localResults[i].simulations + 1)
                        }
                    }
                }

                partialResults[coreIndex] = localResults
            }
        }
        jobs.forEach { it.await() }
    }

    // Combine results from all cores
    for (coreIndex in 0 until numCores) {
        for (playerIndex in 0 until holes.size) {
            val partial = partialResults[coreIndex][playerIndex]
            results[playerIndex] = results[playerIndex].copy(
                wins = results[playerIndex].wins + partial.wins,
                ties = results[playerIndex].ties + partial.ties,
                simulations = results[playerIndex].simulations + partial.simulations
            )
        }
    }

    results
}
