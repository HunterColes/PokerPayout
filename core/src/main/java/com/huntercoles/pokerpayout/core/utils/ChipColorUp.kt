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

    fun nextChipUp(chip: Int): Int? = COLOR_UP_CHAIN.firstOrNull { it > chip && it % chip == 0 }

    /**
     * Level index -> chip values that are no longer needed from that level on (in the order they
     * drop out). Level 0 never appears: its small blind is the smallest chip.
     */
    fun plan(levels: List<BlindLevel>, smallestChip: Int): Map<Int, List<Int>> {
        if (levels.isEmpty() || smallestChip <= 0) return emptyMap()
        val result = linkedMapOf<Int, MutableList<Int>>()
        var chip = smallestChip
        var from = 1
        var dropsAt = nextChipUp(chip)?.let { firstLevelPayableIn(levels, it, from) }
        while (dropsAt != null) {
            result.getOrPut(dropsAt) { mutableListOf() } += chip
            chip = nextChipUp(chip) ?: break
            from = dropsAt
            dropsAt = nextChipUp(chip)?.let { firstLevelPayableIn(levels, it, from) }
        }
        return result
    }

    /** First index >= [from] after which every amount is a multiple of [chip], or null if none. */
    private fun firstLevelPayableIn(levels: List<BlindLevel>, chip: Int, from: Int): Int? {
        var index = levels.size
        while (index > from && levels[index - 1].amounts().all { it % chip == 0 }) index--
        return index.takeIf { it < levels.size }
    }

    private fun BlindLevel.amounts() = listOf(smallBlind, bigBlind, ante).filter { it > 0 }
}
