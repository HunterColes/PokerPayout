package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.design.ChipDenominations

/** The chip values Tournament -> Blinds offers as the smallest chip (PP-051). */
object SmallestChipChoices {
    val values: List<Int> = ChipDenominations.ALL_CHIPS.map { it.value }.sorted()

    /**
     * Maps a stored free-entry value onto a real chip: the largest chip value that divides it, so every
     * blind the old value could build is still payable (15 -> 5, 30 -> 10, 75 -> 25, 7 -> 1).
     * Values already on the list are kept.
     */
    fun normalize(value: Int): Int =
        values.lastOrNull { it <= value && value % it == 0 } ?: values.first()
}

/**
 * When to color up: the smallest chip in play is no longer needed once every blind and ante from some
 * level on is a multiple of the next chip up.
 */
object ColorUpPlanner {

    /**
     * Chips that a common home set colors up into, from the smallest. A chip colors up into the first
     * of these that is larger and a multiple of it (25 -> 100, 50 -> 100, 10 -> 100, 5 -> 25).
     */
    private val COLOR_UP_CHAIN = listOf(1, 5, 25, 100, 500, 1_000, 5_000, 25_000, 100_000)

    fun nextChipUp(chip: Int): Int? = nextChipUp(chip, COLOR_UP_CHAIN)

    /** The first chip of [chain] above [chip] that is a multiple of it, or null. */
    fun nextChipUp(chip: Int, chain: List<Int>): Int? = chain.firstOrNull { it > chip && it % chip == 0 }

    /**
     * Level index -> chip values that are no longer needed from that level on (in the order they
     * drop out). Level 0 never appears: its small blind is the smallest chip.
     *
     * @param chain the chips there are to color up into, ascending. By default a common home set's;
     *   the chip set (PP-033) passes the chips you own.
     */
    fun plan(levels: List<BlindLevel>, smallestChip: Int, chain: List<Int> = COLOR_UP_CHAIN): Map<Int, List<Int>> {
        if (levels.isEmpty() || smallestChip <= 0) return emptyMap()
        val result = linkedMapOf<Int, MutableList<Int>>()
        var chip = smallestChip
        var from = 1
        var dropsAt = nextChipUp(chip, chain)?.let { firstLevelPayableIn(levels, it, from) }
        while (dropsAt != null) {
            result.getOrPut(dropsAt) { mutableListOf() } += chip
            chip = nextChipUp(chip, chain) ?: break
            from = dropsAt
            dropsAt = nextChipUp(chip, chain)?.let { firstLevelPayableIn(levels, it, from) }
        }
        return result
    }

    /**
     * [plan] for a whole set of chips (the chip set, PP-033): every chip of [chips] (ascending) that
     * has a larger multiple in the set drops out at the first level, no earlier than the chip before
     * it, from which every amount is a multiple of that larger chip. Unlike [plan], chips off the
     * smallest chip's path count too (a 250 in a 50/100/500 set drops with the 100s).
     */
    fun planFor(levels: List<BlindLevel>, chips: List<Int>): Map<Int, List<Int>> {
        if (levels.isEmpty()) return emptyMap()
        val result = sortedMapOf<Int, MutableList<Int>>()
        var from = 1
        val set = chips.filter { it > 0 }.distinct().sorted()
        set.forEach { chip ->
            val next = nextChipUp(chip, set) ?: return@forEach
            val dropsAt = firstLevelPayableIn(levels, next, from) ?: return@forEach
            result.getOrPut(dropsAt) { mutableListOf() } += chip
            from = dropsAt
        }
        return result
    }

    /**
     * When the clock colors up a chip that drops out from level index [levelIndex]: at the first
     * break whose next level is that level or later (a break is the time to do it), else on the
     * level itself. Returns the 0-based index into [breaksAfterLevelIndex] (the level index each
     * break follows, ascending), or -1 for "on the level". The clock's timeline places its
     * color-ups the same way.
     */
    fun breakFor(levelIndex: Int, breaksAfterLevelIndex: List<Int>): Int =
        breaksAfterLevelIndex.indexOfFirst { it + 1 >= levelIndex }

    /** First index >= [from] after which every amount is a multiple of [chip], or null if none. */
    private fun firstLevelPayableIn(levels: List<BlindLevel>, chip: Int, from: Int): Int? {
        var index = levels.size
        while (index > from && levels[index - 1].amounts().all { it % chip == 0 }) index--
        return index.takeIf { it < levels.size }
    }

    private fun BlindLevel.amounts() = listOf(smallBlind, bigBlind, ante).filter { it > 0 }
}
