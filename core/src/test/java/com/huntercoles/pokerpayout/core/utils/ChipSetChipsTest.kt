package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The chip set as the clock colors up with it (PP-091 #9). */
class ChipSetChipsTest {

    private val mine = ChipInventory.of(
        listOf(
            InventoryChip(ChipColour.White, 25, 200),
            InventoryChip(ChipColour.Red, 100, 200),
            InventoryChip(ChipColour.Green, 500, 0), // a colour with no chips plays no part
            InventoryChip(ChipColour.Black, 1_000, 100),
        )
    )

    @Test
    fun `a set the app filled in isn't set up yet, and neither is one with no chips`() {
        assertNull(ChipSetChips.of(ChipSetSettings()))
        assertNull(ChipSetChips.of(ChipSetSettings(inventory = mine, inventoryReviewed = false)))
        assertNull(ChipSetChips.of(ChipSetSettings(inventory = ChipInventory.EMPTY, inventoryReviewed = true)))
    }

    @Test
    fun `a set you changed is the chips you own, smallest first, in your colours`() {
        val chips = ChipSetChips.of(ChipSetSettings(inventory = mine, inventoryReviewed = true))
        assertEquals(
            ChipSetChips(listOf(ChipRef(ChipColour.White, 25), ChipRef(ChipColour.Red, 100), ChipRef(ChipColour.Black, 1_000))),
            chips
        )
        assertEquals(ChipColour.White, chips?.colourOf(25))
        assertNull(chips?.colourOf(500))
    }

    @Test
    fun `the color-up chain starts at the largest chip that pays the first small blind`() {
        val chips = ChipSetChips(
            listOf(25, 50, 100, 500).map { ChipRef(ChipColour.forStandardValue(it) ?: ChipColour.Grey, it) }
        )
        assertEquals(listOf(25, 50, 100, 500), chips.chainFor(25))
        assertEquals(listOf(50, 100, 500), chips.chainFor(50)) // 25s stay in the box, as the chip set's stack has it
        assertEquals(listOf(25, 50, 100, 500), chips.chainFor(75)) // only the 25 pays 75
        assertEquals(listOf(100, 500), chips.chainFor(300))
        assertEquals(emptyList<Int>(), chips.chainFor(10)) // none pays it
        assertEquals(emptyList<Int>(), chips.chainFor(0))
    }
}
