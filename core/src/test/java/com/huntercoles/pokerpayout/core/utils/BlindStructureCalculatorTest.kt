package com.huntercoles.pokerpayout.core.utils

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for BlindStructureCalculator end-to-end schedule generation.
 */
class BlindStructureCalculatorTest {

    @Test
    fun `default tournament generates valid schedule`() {
        val input = BlindStructureInput(
            players = 10,
            targetDurationMinutes = 180,
            smallestChip = 50,
            startingStack = 5_000,
            roundLengthMinutes = 20
        )

        val schedule = BlindStructureCalculator.generateSchedule(input)
        val expectedLevels = ceil(input.targetDurationMinutes.toDouble() / input.roundLengthMinutes).toInt()

        assertEquals(expectedLevels, schedule.size)
        assertEquals(input.smallestChip, schedule.first().smallBlind)
        assertEquals(input.startingStack, schedule.last().smallBlind)
        assertTrue(schedule.zipWithNext().all { (prev, next) -> next.smallBlind > prev.smallBlind })
        assertTrue(schedule.all { it.smallBlind % input.smallestChip == 0 })
        
        // Most growth steps should be reasonable (30%-100%)
        val growthRates = schedule.zipWithNext { prev, next -> next.smallBlind.toDouble() / prev.smallBlind }
        val goodGrowth = growthRates.count { it in 1.3..2.0 }
        assertTrue(goodGrowth >= growthRates.size * 0.7, 
            "At least 70% of growth steps should be in 30%-100% window")
    }

    @Test
    fun `schedule adapts to different configurations`() {
        val input = BlindStructureInput(
            players = 12,
            targetDurationMinutes = 240,
            smallestChip = 50,
            startingStack = 6_400,
            roundLengthMinutes = 30
        )

        val schedule = BlindStructureCalculator.generateSchedule(input)

        assertEquals(input.smallestChip, schedule.first().smallBlind)
        assertEquals(input.startingStack, schedule.last().smallBlind)
        assertTrue(schedule.all { it.smallBlind % input.smallestChip == 0 })
        
        val growthRates = schedule.zipWithNext { prev, next -> next.smallBlind.toDouble() / prev.smallBlind }
        val goodGrowth = growthRates.count { it in 1.3..2.0 }
        assertTrue(goodGrowth >= growthRates.size * 0.7)
    }
}
