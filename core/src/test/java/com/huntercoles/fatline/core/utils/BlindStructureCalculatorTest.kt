package com.huntercoles.fatline.core.utils

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

        val expectedLevels = ceil((input.targetDurationMinutes + 60.0) / input.roundLengthMinutes).toInt()
        assertTrue(schedule.isNotEmpty(), "Expected schedule to contain at least one level")
        assertEquals(input.smallestChip, schedule.first().smallBlind)
        assertEquals(input.smallestChip * 2, schedule.first().bigBlind)
        assertTrue(
            schedule.size >= expectedLevels,
            "Expected at least $expectedLevels levels but was ${schedule.size}"
        )
        assertTrue(schedule.zipWithNext().all { (prev, next) -> next.smallBlind > prev.smallBlind })

        // Growth between 33% and 100%
        schedule.zipWithNext { prev, next -> next.smallBlind.toDouble() / prev.smallBlind }
            .forEach { growth ->
                assertTrue(growth in 1.33..2.0, "Growth step $growth outside 33%-100% window")
            }

        // Final big blind must be at least double the starting stack
        val finalLevel = schedule.last()
        assertTrue(finalLevel.bigBlind >= input.startingStack * 2)

        // All blinds are multiples of the smallest chip denomination
        assertTrue(schedule.all { it.smallBlind % input.smallestChip == 0 })

        // Overtime levels should be flagged as sudden-death
        val regulationCutoff = ceil(input.targetDurationMinutes.toDouble() / input.roundLengthMinutes).toInt()
        schedule.withIndex()
            .filter { it.index >= regulationCutoff }
            .forEach { (_, level) ->
                assertTrue(level.isSuddenDeath, "Expected level ${level.level} to be marked as sudden death")
            }
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
        assertTrue(schedule.last().bigBlind >= startingStack * 2)
        growthRates.forEach { growth ->
            assertTrue(growth in 1.33..2.0, "Growth step $growth outside expected window")
        }
        assertTrue(schedule.all { it.smallBlind % input.smallestChip == 0 })
    }
}