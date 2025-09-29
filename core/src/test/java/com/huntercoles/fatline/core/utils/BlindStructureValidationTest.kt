package com.huntercoles.fatline.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlindStructureValidationTest {

    @Test
    fun `final big blind is at least double starting stack`() {
        val testCases = listOf(
            BlindStructureInput(
                players = 10,
                targetDurationMinutes = 180,
                smallestChip = 25,
                startingStack = 5_000,
                roundLengthMinutes = 20
            ),
            BlindStructureInput(
                players = 6,
                targetDurationMinutes = 120,
                smallestChip = 50,
                startingStack = 10_000,
                roundLengthMinutes = 15
            ),
            BlindStructureInput(
                players = 12,
                targetDurationMinutes = 240,
                smallestChip = 100,
                startingStack = 15_000,
                roundLengthMinutes = 25
            )
        )

        testCases.forEach { input ->
            val schedule = BlindStructureCalculator.generateSchedule(input)
            val finalLevel = schedule.last()
            
            assertTrue(
                finalLevel.bigBlind >= input.startingStack * 2,
                "Final big blind (${finalLevel.bigBlind}) must be at least 2x starting stack (${input.startingStack * 2}) " +
                "for input: players=${input.players}, duration=${input.targetDurationMinutes}, " +
                "smallestChip=${input.smallestChip}, startingStack=${input.startingStack}"
            )
        }
    }

    @Test
    fun `first small blind equals smallest chip amount`() {
        val testCases = listOf(
            BlindStructureInput(
                players = 10,
                targetDurationMinutes = 180,
                smallestChip = 25,
                startingStack = 5_000,
                roundLengthMinutes = 20
            ),
            BlindStructureInput(
                players = 8,
                targetDurationMinutes = 150,
                smallestChip = 50,
                startingStack = 7_500,
                roundLengthMinutes = 18
            ),
            BlindStructureInput(
                players = 12,
                targetDurationMinutes = 240,
                smallestChip = 100,
                startingStack = 12_000,
                roundLengthMinutes = 25
            )
        )

        testCases.forEach { input ->
            val schedule = BlindStructureCalculator.generateSchedule(input)
            val firstLevel = schedule.first()
            
            assertEquals(
                input.smallestChip,
                firstLevel.smallBlind,
                "First small blind must equal smallest chip amount (${input.smallestChip}) " +
                "for input: players=${input.players}, smallestChip=${input.smallestChip}"
            )
        }
    }

    @Test
    fun `all blind levels are unique and different`() {
        val input = BlindStructureInput(
            players = 10,
            targetDurationMinutes = 180,
            smallestChip = 25,
            startingStack = 5_000,
            roundLengthMinutes = 20
        )

        val schedule = BlindStructureCalculator.generateSchedule(input)
        
        // Check that all small blinds are unique
        val smallBlinds = schedule.map { it.smallBlind }
        val uniqueSmallBlinds = smallBlinds.toSet()
        
        assertEquals(
            smallBlinds.size,
            uniqueSmallBlinds.size,
            "All small blind levels should be unique. Found duplicates in: $smallBlinds"
        )
        
        // Check that all big blinds are unique
        val bigBlinds = schedule.map { it.bigBlind }
        val uniqueBigBlinds = bigBlinds.toSet()
        
        assertEquals(
            bigBlinds.size,
            uniqueBigBlinds.size,
            "All big blind levels should be unique. Found duplicates in: $bigBlinds"
        )
        
        // Check that blinds are strictly increasing
        assertTrue(
            schedule.zipWithNext().all { (prev, next) -> next.smallBlind > prev.smallBlind },
            "Small blinds must be strictly increasing"
        )
        
        assertTrue(
            schedule.zipWithNext().all { (prev, next) -> next.bigBlind > prev.bigBlind },
            "Big blinds must be strictly increasing"
        )
    }

    @Test
    fun `all blind levels have different values by default`() {
        val testInputs = listOf(
            BlindStructureInput(
                players = 6,
                targetDurationMinutes = 120,
                smallestChip = 25,
                startingStack = 5_000,
                roundLengthMinutes = 15
            ),
            BlindStructureInput(
                players = 10,
                targetDurationMinutes = 180,
                smallestChip = 50,
                startingStack = 8_000,
                roundLengthMinutes = 20
            ),
            BlindStructureInput(
                players = 15,
                targetDurationMinutes = 300,
                smallestChip = 100,
                startingStack = 15_000,
                roundLengthMinutes = 25
            )
        )

        testInputs.forEach { input ->
            val schedule = BlindStructureCalculator.generateSchedule(input)
            
            // Ensure we have at least 2 levels to compare
            assertTrue(
                schedule.size >= 2,
                "Schedule should have at least 2 levels for meaningful comparison"
            )
            
            // Check consecutive levels are different
            schedule.zipWithNext { current, next ->
                assertTrue(
                    current.smallBlind != next.smallBlind,
                    "Consecutive levels should have different small blinds: " +
                    "Level ${current.level} (${current.smallBlind}) vs Level ${next.level} (${next.smallBlind})"
                )
                
                assertTrue(
                    current.bigBlind != next.bigBlind,
                    "Consecutive levels should have different big blinds: " +
                    "Level ${current.level} (${current.bigBlind}) vs Level ${next.level} (${next.bigBlind})"
                )
            }
        }
    }
}