package com.huntercoles.pokerpayout.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChipColorUpTest {

    private fun ladder(vararg smallBlinds: Int, anteFrom: Int = 0) = smallBlinds.mapIndexed { i, sb ->
        val ante = if (anteFrom in 1..i + 1) 2 * sb else 0
        BlindLevel(level = i + 1, smallBlind = sb, bigBlind = 2 * sb, ante = ante, roundStartMinute = i * 20)
    }

    @Test
    fun `the smallest chip picker offers the real chip values`() {
        assertEquals(listOf(1, 5, 10, 20, 25, 50, 100, 250, 500, 1_000, 2_000, 5_000), SmallestChipChoices.values)
    }

    @Test
    fun `an old free-entry smallest chip becomes the largest real chip that divides it`() {
        val migrated = listOf(15, 30, 75, 7, 150, 1_500, 10_000, 40, 0).map(SmallestChipChoices::normalize)
        assertEquals(listOf(5, 10, 25, 1, 50, 500, 5_000, 20, 1), migrated)
        SmallestChipChoices.values.forEach { assertEquals(it, SmallestChipChoices.normalize(it)) }
    }

    @Test
    fun `chips color up into the next chip of a common set that they divide`() {
        assertEquals(5, ColorUpPlanner.nextChipUp(1))
        assertEquals(25, ColorUpPlanner.nextChipUp(5))
        assertEquals(100, ColorUpPlanner.nextChipUp(25))
        assertEquals(100, ColorUpPlanner.nextChipUp(50))
        assertEquals(100, ColorUpPlanner.nextChipUp(10))
        assertEquals(100, ColorUpPlanner.nextChipUp(20))
        assertEquals(500, ColorUpPlanner.nextChipUp(250))
        assertEquals(25_000, ColorUpPlanner.nextChipUp(5_000))
        assertNull(ColorUpPlanner.nextChipUp(100_000))
    }

    @Test
    fun `the default schedule drops the 50s at 300-600 and the 100s at 1,500-3,000`() {
        val plan = ColorUpPlanner.plan(ladder(50, 100, 150, 300, 500, 800, 1_500, 3_000, 5_000), smallestChip = 50)

        // Level 4 (index 3) on, every amount is a multiple of 100; level 7 on, of 500; and so on.
        assertEquals(mapOf(3 to listOf(50), 6 to listOf(100), 7 to listOf(500), 8 to listOf(1_000)), plan)
    }

    @Test
    fun `a chip stays while any later level still needs it`() {
        // 750 needs 25s (or 50s) until the very end, so the 25s never go
        val plan = ColorUpPlanner.plan(ladder(25, 50, 100, 200, 400, 750), smallestChip = 25)
        assertTrue(plan.isEmpty(), "$plan")
    }

    @Test
    fun `several chips can go at the same level`() {
        val plan = ColorUpPlanner.plan(ladder(1, 2, 4, 8, 16, 500, 1_000), smallestChip = 1)
        // From 500/1,000 on, 1s, 5s, 25s and 100s are all unneeded; from 1,000/2,000 on, the 500s too
        assertEquals(mapOf(5 to listOf(1, 5, 25, 100), 6 to listOf(500)), plan)
    }

    @Test
    fun `antes count as amounts that need chips`() {
        // Without antes the 25s go at 100-200; a BB ante doesn't change the multiples, it equals the BB
        assertEquals(
            ColorUpPlanner.plan(ladder(25, 50, 100, 200, 400), 25),
            ColorUpPlanner.plan(ladder(25, 50, 100, 200, 400, anteFrom = 1), 25)
        )
    }
}
