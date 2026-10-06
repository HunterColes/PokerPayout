package com.huntercoles.pokerpayout.core.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [ChipInventory]: the chips you own, how they are saved, and the set an old setup becomes. */
class ChipInventoryTest {

    private fun chip(colour: ChipColour, value: Int, count: Int) = InventoryChip(colour, value, count)

    @Test
    fun `the starting set is the mockup's 500 chips`() {
        val set = ChipInventory.HOME_SET
        assertEquals(listOf(25, 100, 500, 1_000), set.chips.map { it.value })
        assertEquals(
            listOf(ChipColour.Green, ChipColour.Black, ChipColour.Purple, ChipColour.Yellow),
            set.chips.map { it.colour }
        )
        assertEquals(500, set.totalChips)
        assertEquals(168_750L, set.totalValue)
    }

    @Test
    fun `a set is sorted by value, clamped, and keeps colours and values unique`() {
        val set = ChipInventory.of(
            listOf(
                chip(ChipColour.Black, 100, 150),
                chip(ChipColour.White, 25, 20_000), // white chips worth 25 in this set; too many
                chip(ChipColour.Black, 500, 10), // a second black: dropped
                chip(ChipColour.Red, 100, 10), // a second 100: dropped
                chip(ChipColour.Blue, 0, -3), // value and count clamped
            )
        )
        assertEquals(
            listOf(
                chip(ChipColour.Blue, 1, 0),
                chip(ChipColour.White, 25, ChipInventory.MAX_COUNT),
                chip(ChipColour.Black, 100, 150),
            ),
            set.chips
        )
        assertEquals(listOf(ChipColour.White, ChipColour.Black), set.owned.map { it.colour })
        assertTrue(ChipColour.Red in set.missingColours && ChipColour.White !in set.missingColours)
    }

    @Test
    fun `encode and decode round-trip, and decoding skips what doesn't parse`() {
        val set = ChipInventory.of(
            listOf(chip(ChipColour.White, 25, 300), chip(ChipColour.Red, 100, 0), chip(ChipColour.Brown, 5_000, 12))
        )
        assertEquals("white:25:300;red:100:0;brown:5000:12", set.encode())
        assertEquals(set, ChipInventory.decode(set.encode()))
        assertEquals(ChipInventory.EMPTY, ChipInventory.decode(""))
        assertNull(ChipInventory.decode(null))
        assertEquals(
            listOf(chip(ChipColour.Green, 25, 10)),
            ChipInventory.decode("green:25:10;purple:x:3;nope:5:5;black:100;yellow:1000:-1;orange:0:4")!!.chips
        )
    }

    @Test
    fun `changing a colour keeps the set valid`() {
        val set = ChipInventory.HOME_SET
        assertEquals(149, set.withCount(ChipColour.Black, 149)[ChipColour.Black]!!.count)
        assertEquals(set, set.withCount(ChipColour.Brown, 5)) // not in the set: unchanged

        // Pink 250s can join; a second 100 or a second green can't
        val added = set.withChip(chip(ChipColour.Pink, 250, 50))!!
        assertEquals(listOf(25, 100, 250, 500, 1_000), added.chips.map { it.value })
        assertNull(set.withChip(chip(ChipColour.Pink, 100, 50)))
        assertNull(set.withChip(chip(ChipColour.Green, 5, 50)))

        // Editing a colour may keep its own value, or move it, or change its colour
        val recoloured = set.withChip(chip(ChipColour.White, 25, 120), replacing = ChipColour.Green)!!
        assertEquals(chip(ChipColour.White, 25, 120), recoloured.chips.first())
        val revalued = set.withChip(chip(ChipColour.Green, 50, 150), replacing = ChipColour.Green)!!
        assertEquals(chip(ChipColour.Green, 50, 150), revalued.chips.first())

        assertEquals(listOf(100, 500, 1_000), set.without(ChipColour.Green).chips.map { it.value })
    }

    @Test
    fun `an old calculator stack becomes a set for every player twice over, in rolls of 25`() {
        // The v1.3.0 default answer (5,000 from 50s, 5 denominations, Linear Steep) for 5 players
        val set = ChipInventory.fromLastStack(listOf(50 to 9, 100 to 8, 250 to 5, 500 to 3, 1_000 to 1), players = 5)!!
        assertEquals(
            listOf(
                chip(ChipColour.Orange, 50, 100), // 9 × 5 × 2 = 90
                chip(ChipColour.Black, 100, 100), // 80
                chip(ChipColour.Pink, 250, 50), // 50
                chip(ChipColour.Purple, 500, 50), // 30
                chip(ChipColour.Yellow, 1_000, 25), // 10
            ),
            set.chips
        )
        assertNull(ChipInventory.fromLastStack(listOf(30 to 4), players = 5), "30 isn't a chip the calculator made")
        assertNull(ChipInventory.fromLastStack(emptyList(), players = 5))
    }

    @Test
    fun `colours are saved by key and match the standard chips`() {
        ChipColour.entries.forEach { assertEquals(it, ChipColour.fromKey(it.key)) }
        assertEquals(ChipDistributionOptimizer.STANDARD_DENOMINATIONS, ChipColour.entries.map { it.standardValue })
        assertEquals(ChipColour.Black, ChipColour.forStandardValue(100))
        assertNull(ChipColour.forStandardValue(30))
    }
}
