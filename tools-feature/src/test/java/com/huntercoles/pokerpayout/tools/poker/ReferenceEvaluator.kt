package com.huntercoles.pokerpayout.tools.poker

/**
 * Deliberately slow, obviously-correct reference: try all five-card subsets, rank each by
 * the textbook rules, keep the best. Used only to cross-check [HandEvaluator] in property
 * tests, never to compute expected values for golden fixtures.
 *
 * Cards use the engine's `rank * 4 + suit` numbering, decoded here with plain arithmetic.
 * A hand key is `[category, tie-break ranks...]`, compared lexicographically.
 */
internal object ReferenceEvaluator {

    fun best(cards: List<Int>): List<Int> {
        require(cards.size in 5..7)
        var best: List<Int>? = null
        for (five in subsetsOfFive(cards)) {
            val key = rankFive(five)
            if (best == null || compareKeys(key, best) > 0) best = key
        }
        return best!!
    }

    fun compareKeys(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return a.size.compareTo(b.size)
    }

    @Suppress("CyclomaticComplexMethod") // the textbook ranking rules, one branch per category
    private fun rankFive(cards: List<Int>): List<Int> {
        val ranks = cards.map { it / 4 }.sortedDescending()
        val suits = cards.map { it % 4 }
        val flush = suits.distinct().size == 1
        // (count, rank) groups, biggest group first, then higher rank first.
        val groups = ranks.groupBy { it }.map { (rank, list) -> list.size to rank }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
        val distinct = ranks.distinct()
        val straightTop = when {
            distinct.size == 5 && distinct.first() - distinct.last() == 4 -> distinct.first()
            distinct == listOf(12, 3, 2, 1, 0) -> 3 // A-2-3-4-5: the five is the top card
            else -> -1
        }
        val byGroups = groups.map { it.second }
        return when {
            flush && straightTop >= 0 -> listOf(8, straightTop)
            groups[0].first == 4 -> listOf(7) + byGroups
            groups[0].first == 3 && groups[1].first == 2 -> listOf(6) + byGroups
            flush -> listOf(5) + ranks
            straightTop >= 0 -> listOf(4, straightTop)
            groups[0].first == 3 -> listOf(3) + byGroups
            groups[0].first == 2 && groups[1].first == 2 -> listOf(2) + byGroups
            groups[0].first == 2 -> listOf(1) + byGroups
            else -> listOf(0) + ranks
        }
    }

    private fun subsetsOfFive(cards: List<Int>): Sequence<List<Int>> = sequence {
        val n = cards.size
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n)
            for (d in c + 1 until n) for (e in d + 1 until n) {
                yield(listOf(cards[a], cards[b], cards[c], cards[d], cards[e]))
            }
    }
}
