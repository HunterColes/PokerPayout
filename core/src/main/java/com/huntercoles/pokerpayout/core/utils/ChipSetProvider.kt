package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import kotlinx.coroutines.flow.Flow

/**
 * The chip set (Tools → Chip set, PP-033) for features outside `tools-feature` (the clock's break
 * screen, PP-091 #9), so they never import it. Core implements it from the chip set's saved
 * settings ([com.huntercoles.pokerpayout.core.preferences.SavedChipSetProvider]).
 */
interface ChipSetProvider {
    /** Your chip set as it is now, or null when none is set up. */
    fun current(): ChipSetChips?

    /** The same, emitted again whenever it changes. */
    val chipSet: Flow<ChipSetChips?>
}

/**
 * Your chip set as the tournament clock colors up with it (PP-091 #9): the chips you own, smallest
 * first, each in its colour in your set (a white 25 is white, whatever a standard set makes it).
 */
data class ChipSetChips(val chips: List<ChipRef>) {

    /** The colour of the chip worth [value] in your set, or null when you have none. */
    fun colourOf(value: Int): ChipColour? = chips.firstOrNull { it.value == value }?.colour

    /**
     * The chips a game whose first small blind is [smallBlind] colors up through, as the chip set's
     * color-up plan counts them ([PlanStacksUseCase]): from the largest chip that pays that blind,
     * up. Empty when none of your chips pays it (the clock then keeps a common home set's chips).
     */
    fun chainFor(smallBlind: Int): List<Int> {
        val values = chips.map { it.value }
        val smallest = values.filter { smallBlind > 0 && smallBlind % it == 0 }.maxOrNull() ?: return emptyList()
        return values.filter { it >= smallest }
    }

    companion object {
        /**
         * The chips of [settings], or null until the chip set is set up: changed by you at least
         * once (a set the app filled in, the starting set or one made from the old calculator, isn't
         * yours yet), with some chips in it.
         */
        fun of(settings: ChipSetSettings): ChipSetChips? {
            val owned = settings.inventory.owned
            if (!settings.inventoryReviewed || owned.isEmpty()) return null
            return ChipSetChips(owned.map { ChipRef(it.colour, it.value) })
        }
    }
}
