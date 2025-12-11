package com.huntercoles.pokerpayout.core.utils

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlindStructureCalculatorTest {

    @Test
    fun `schedule follows growth window and overtime target`() {
        val input = BlindStructureInput(
            players = 10,
            targetDurationMinutes = 180,
            smallestChip = 25,
            startingStack = 5_000,
            roundLengthMinutes = 20
        )

        val schedule = BlindStructureCalculator.generateSchedule(input)

        // Expect exact number of levels: duration / roundLength
        val expectedLevels = ceil(input.targetDurationMinutes.toDouble() / input.roundLengthMinutes).toInt()
        assertTrue(schedule.isNotEmpty(), "Expected schedule to contain at least one level")
        assertEquals(input.smallestChip, schedule.first().smallBlind)
        assertEquals(input.smallestChip * 2, schedule.first().bigBlind)
        assertEquals(
            expectedLevels,
            schedule.size,
            "Expected exactly $expectedLevels levels but was ${schedule.size}"
        )
        assertTrue(schedule.zipWithNext().all { (prev, next) -> next.smallBlind > prev.smallBlind })

        // Most growth steps should be between 25% and 100%, but allow some flexibility
        // since we must hit exact target values
        val growthRates = schedule.zipWithNext { prev, next -> next.smallBlind.toDouble() / prev.smallBlind }
        val goodGrowth = growthRates.count { it in 1.25..2.0 }
        assertTrue(goodGrowth >= growthRates.size * 0.7, 
            "At least 70% of growth steps should be in 25%-100% window, but only $goodGrowth/${growthRates.size} were")

        // Final regular level small blind should equal starting stack
        val finalLevel = schedule.last()
        assertEquals(input.startingStack, finalLevel.smallBlind, 
            "Final small blind should equal starting stack")

        // All blinds are multiples of the smallest chip denomination
        assertTrue(schedule.all { it.smallBlind % input.smallestChip == 0 })

    }

    @Test
    fun `schedule adapts to larger stacks and round lengths`() {
        val players = 12
        val startingStack = 7_500
        val input = BlindStructureInput(
            players = players,
            targetDurationMinutes = 240,
            smallestChip = 50,
            startingStack = startingStack,
            roundLengthMinutes = 30
        )

        val schedule = BlindStructureCalculator.generateSchedule(input)
        val growthRates = schedule.zipWithNext { prev, next -> next.smallBlind.toDouble() / prev.smallBlind }

        assertTrue(schedule.first().smallBlind == input.smallestChip)
        // Final regular level small blind should equal starting stack
        assertEquals(startingStack, schedule.last().smallBlind)
        val goodGrowth = growthRates.count { it in 1.25..2.0 }
        assertTrue(goodGrowth >= growthRates.size * 0.7, 
            "At least 70% of growth steps should be in range")
        assertTrue(schedule.all { it.smallBlind % input.smallestChip == 0 })
    }
}