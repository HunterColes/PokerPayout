package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.design.ChipDenominations

/**
 * The colours poker chips come in (the physical chips of [com.huntercoles.pokerpayout.core.design.ChipDenominations]).
 * A colour is only a look: what it is worth is whatever your set says. [standardValue] is what most
 * sets make it, offered first when the colour is added.
 *
 * [key] is how the colour is saved; it never changes.
 */
enum class ChipColour(val key: String, val standardValue: Int) {
    White("white", ChipDenominations.WHITE.value),
    Red("red", ChipDenominations.RED.value),
    Blue("blue", ChipDenominations.BLUE.value),
    Grey("grey", ChipDenominations.GREY.value),
    Green("green", ChipDenominations.GREEN.value),
    Orange("orange", ChipDenominations.ORANGE.value),
    Black("black", ChipDenominations.BLACK.value),
    Pink("pink", ChipDenominations.PINK.value),
    Purple("purple", ChipDenominations.PURPLE.value),
    Yellow("yellow", ChipDenominations.YELLOW.value),
    LightBlue("light_blue", ChipDenominations.LIGHT_BLUE.value),
    Brown("brown", ChipDenominations.BROWN.value),
    ;

    companion object {
        fun fromKey(key: String): ChipColour? = entries.firstOrNull { it.key == key }

        /** The colour a standard set gives [value], or null when no standard chip is worth that. */
        fun forStandardValue(value: Int): ChipColour? = entries.firstOrNull { it.standardValue == value }
    }
}

/** One colour of a chip set: what each chip is worth and how many you own. */
data class InventoryChip(val colour: ChipColour, val value: Int, val count: Int)

/**
 * The chips you own (PP-033): one entry per colour, each with its value and count. Colours are
 * unique and so are values; [chips] is sorted by value, smallest first.
 *
 * Build one with [of], which sorts, clamps counts to 0..[MAX_COUNT] and values to 1..[MAX_VALUE],
 * and keeps the first entry when two share a colour or a value. A colour with no chips stays in the
 * set (you own the colour, just none right now) but plays no part in any stack.
 */
class ChipInventory private constructor(val chips: List<InventoryChip>) {

    /** Chips you own, of every colour. */
    val totalChips: Int get() = chips.sumOf { it.count }

    /** What all your chips are worth together. */
    val totalValue: Long get() = chips.sumOf { it.value.toLong() * it.count }

    /** Colours with at least one chip. */
    val owned: List<InventoryChip> get() = chips.filter { it.count > 0 }

    /** Colours not in the set yet, in the usual order. */
    val missingColours: List<ChipColour> get() = ChipColour.entries.filter { colour -> chips.none { it.colour == colour } }

    operator fun get(colour: ChipColour): InventoryChip? = chips.firstOrNull { it.colour == colour }

    fun chipWorth(value: Int): InventoryChip? = chips.firstOrNull { it.value == value }

    fun isEmpty(): Boolean = chips.isEmpty()

    /** This set with [colour]'s count set to [count] (clamped); unchanged if the colour isn't in it. */
    fun withCount(colour: ChipColour, count: Int): ChipInventory =
        of(chips.map { if (it.colour == colour) it.copy(count = count) else it })

    /**
     * This set with [chip] in place of [replacing] (or added, when [replacing] is null or not in the
     * set). Null when another colour already has [chip]'s colour or value.
     */
    fun withChip(chip: InventoryChip, replacing: ChipColour? = null): ChipInventory? {
        val others = chips.filter { it.colour != replacing }
        if (others.any { it.colour == chip.colour || it.value == chip.value }) return null
        return of(others + chip)
    }

    fun without(colour: ChipColour): ChipInventory = of(chips.filter { it.colour != colour })

    /** "green:25:150;black:100:150": how the chip set's preferences save it. */
    fun encode(): String =
        chips.joinToString(ENTRY_SEPARATOR) { listOf(it.colour.key, it.value, it.count).joinToString(FIELD_SEPARATOR) }

    override fun equals(other: Any?): Boolean = other is ChipInventory && other.chips == chips

    override fun hashCode(): Int = chips.hashCode()

    override fun toString(): String = "ChipInventory(${encode()})"

    companion object {
        /** Most chips of one colour; more than any home set. */
        const val MAX_COUNT = 9_999

        /** Most a chip can be worth. */
        const val MAX_VALUE = 1_000_000

        private const val ENTRY_SEPARATOR = ";"
        private const val FIELD_SEPARATOR = ":"
        private const val FIELDS = 3

        val EMPTY = ChipInventory(emptyList())

        /**
         * A common 500-chip home set (the S11 mockup's): 150 green 25s, 150 black 100s, 100 purple
         * 500s and 100 yellow 1,000s. A fresh install starts here.
         */
        val HOME_SET: ChipInventory = of(
            listOf(
                InventoryChip(ChipColour.Green, 25, 150),
                InventoryChip(ChipColour.Black, 100, 150),
                InventoryChip(ChipColour.Purple, 500, 100),
                InventoryChip(ChipColour.Yellow, 1_000, 100),
            )
        )

        fun of(chips: List<InventoryChip>): ChipInventory {
            val seenColours = mutableSetOf<ChipColour>()
            val seenValues = mutableSetOf<Int>()
            val kept = chips
                .map { it.copy(value = it.value.coerceIn(1, MAX_VALUE), count = it.count.coerceIn(0, MAX_COUNT)) }
                .filter { seenColours.add(it.colour) && seenValues.add(it.value) }
            return ChipInventory(kept.sortedBy { it.value })
        }

        /** Reads [encode]'s form. Entries that don't parse are skipped; null input gives null. */
        fun decode(encoded: String?): ChipInventory? {
            if (encoded == null) return null
            val entries = encoded.split(ENTRY_SEPARATOR).filter { it.isNotBlank() }.mapNotNull { entry ->
                val fields = entry.split(FIELD_SEPARATOR)
                if (fields.size != FIELDS) return@mapNotNull null
                val colour = ChipColour.fromKey(fields[0]) ?: return@mapNotNull null
                val value = fields[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val count = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                InventoryChip(colour, value, count)
            }
            return of(entries)
        }

        /**
         * The set an existing user starts with when the chip calculator becomes the chip set: the
         * colours of their last stack, [perStack] chips of each per player, with enough for every
         * player twice over (room for rebuys), rounded up to whole rolls of 25.
         *
         * @param stack the last stack, as (chip value, chips per player); values that aren't a
         *   standard chip are skipped (the old calculator only ever used standard chips).
         */
        fun fromLastStack(stack: List<Pair<Int, Int>>, players: Int): ChipInventory? {
            val chips = stack.mapNotNull { (value, perStack) ->
                val colour = ChipColour.forStandardValue(value) ?: return@mapNotNull null
                val wanted = perStack.toLong().coerceAtLeast(1) * players.coerceAtLeast(1) * MIGRATION_HEADROOM
                InventoryChip(colour, value, roundUpToRoll(wanted))
            }
            return if (chips.isEmpty()) null else of(chips)
        }

        /** Chips come in rolls (sleeves) of this many. */
        const val ROLL = 25
        private const val MIGRATION_HEADROOM = 2

        private fun roundUpToRoll(count: Long): Int =
            (((count + ROLL - 1) / ROLL) * ROLL).coerceAtMost(MAX_COUNT.toLong()).toInt()
    }
}
