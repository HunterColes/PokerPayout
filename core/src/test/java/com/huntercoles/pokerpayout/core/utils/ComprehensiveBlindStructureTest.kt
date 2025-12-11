package com.huntercoles.pokerpayout.core.utils

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ComprehensiveBlindStructureTest {

    private val testScenarios = listOf(
        // Test different tournament durations, chip sizes, starting stacks, and round lengths
        TestScenario(180, 25, 5000, 20),  // 3 hour, standard chips
        TestScenario(120, 10, 2000, 10),  // 2 hour, small chips, fast rounds
        TestScenario(240, 25, 5000, 30),  // 4 hour, standard chips, longer rounds
        TestScenario(120, 20, 2000, 10),  // 2 hour, 20 chips, fast rounds
        TestScenario(120, 25, 3000, 10),  // 2 hour, standard chips, fast rounds
        TestScenario(180, 50, 3000, 10)   // 3 hour, large chips, fast rounds
    )

    data class TestScenario(
        val durationMinutes: Int,
        val smallestChip: Int,
        val startingStack: Int,
        val roundLengthMinutes: Int
    ) {
        fun toInput(players: Int = 10) = BlindStructureInput(
            players = players,
            targetDurationMinutes = durationMinutes,
            smallestChip = smallestChip,
            startingStack = startingStack,
            roundLengthMinutes = roundLengthMinutes
        )
        
        override fun toString(): String = 
            "${durationMinutes}min, chip:${smallestChip}, stack:${startingStack}, round:${roundLengthMinutes}min"
    }

    @Test
    fun `all scenarios generate valid blind schedules`() {
        var successCount = 0
        val failures = mutableListOf<String>()
        
        testScenarios.forEach { scenario ->
            try {
                val input = scenario.toInput()
                val schedule = BlindStructureCalculator.generateSchedule(input)
                
                // Basic validations
                assertTrue(schedule.isNotEmpty(), "Schedule should not be empty for $scenario")
                assertTrue(schedule.size >= 2, "Schedule should have at least 2 levels for $scenario")
                
                val firstLevel = schedule.first()
                val finalLevel = schedule.last()
                
                // First level should start with smallest chip
                assertTrue(
                    firstLevel.smallBlind == scenario.smallestChip,
                    "First small blind should be ${scenario.smallestChip} but was ${firstLevel.smallBlind} for $scenario"
                )
                
                // Final regular level small blind should equal starting stack
                assertTrue(
                    finalLevel.smallBlind == scenario.startingStack,
                    "Final small blind (${finalLevel.smallBlind}) should equal starting stack ${scenario.startingStack} for $scenario"
                )
                
                successCount++
                
            } catch (e: Exception) {
                failures.add("$scenario: ${e.message}")
            }
        }
        
        println("=== COMPREHENSIVE BLIND STRUCTURE TEST RESULTS ===")
        println("Total scenarios tested: ${testScenarios.size}")
        println("Successful: $successCount")
        println("Failed: ${failures.size}")
        
        if (failures.isNotEmpty()) {
            println("\nFailures:")
            failures.forEach { println("  - $it") }
        }
        
        assertTrue(failures.isEmpty(), "Found ${failures.size} failing scenarios")
    }

    @Test
    fun `all scenarios have proper growth rates`() {
        var violationCount = 0
        val growthViolations = mutableListOf<String>()
        val violationsByCategory = mutableMapOf<String, MutableList<String>>()
        
        testScenarios.forEach { scenario ->
            val input = scenario.toInput()
            val schedule = BlindStructureCalculator.generateSchedule(input)
            
            // Check that at least 70% of growth rates are reasonable (must hit exact targets now)
            val growthRates = schedule.zipWithNext { current, next ->
                next.smallBlind.toDouble() / current.smallBlind.toDouble()
            }
            val goodGrowthCount = growthRates.count { it in 1.25..2.0 }
            val goodGrowthPercent = if (growthRates.isNotEmpty()) {
                goodGrowthCount.toDouble() / growthRates.size
            } else 1.0
            
            if (goodGrowthPercent < 0.7) {
                violationCount++
                val category = "${input.smallestChip}chip_${input.targetDurationMinutes}min"
                violationsByCategory.getOrPut(category) { mutableListOf() }
                
                val violation = "$scenario: Only ${(goodGrowthPercent * 100).toInt()}% good growth (need 70%+)"
                growthViolations.add(violation)
                violationsByCategory[category]?.add(violation)
            }
        }
        
        if (growthViolations.isNotEmpty()) {
            println("=== GROWTH RATE VIOLATIONS ANALYSIS ===")
            println("Total violations: $violationCount")
            
            println("\n=== BY CHIP SIZE & DURATION ===")
            violationsByCategory.entries.sortedBy { it.key }.forEach { (category, categoryViolations) ->
                println("$category: ${categoryViolations.size} violations")
                categoryViolations.take(3).forEach { println("  $it") }
                if (categoryViolations.size > 3) println("  ... and ${categoryViolations.size - 3} more")
            }
        }
        
        assertTrue(
            growthViolations.isEmpty(),
            "Found $violationCount scenarios with poor growth rates (<70% good)"
        )
    }

    @Test
    fun `all scenarios have unique blind levels`() {
        val uniquenessViolations = mutableListOf<String>()
        
        testScenarios.forEach { scenario ->
            val input = scenario.toInput()
            val schedule = BlindStructureCalculator.generateSchedule(input)
            
            // Check for duplicate small blinds
            val smallBlinds = schedule.map { it.smallBlind }
            val uniqueSmallBlinds = smallBlinds.toSet()
            
            if (smallBlinds.size != uniqueSmallBlinds.size) {
                val duplicates = smallBlinds.groupBy { it }.filter { it.value.size > 1 }
                uniquenessViolations.add("$scenario: Duplicate small blinds: ${duplicates.keys}")
            }
        }
        
        if (uniquenessViolations.isNotEmpty()) {
            println("=== UNIQUENESS VIOLATIONS ===")
            uniquenessViolations.forEach { println("  $it") }
        }
        
        assertTrue(
            uniquenessViolations.isEmpty(),
            "Found ${uniquenessViolations.size} uniqueness violations"
        )
    }

    @Test
    fun `all scenarios use multiples of smallest chip`() {
        val chipViolations = mutableListOf<String>()
        
        testScenarios.forEach { scenario ->
            val input = scenario.toInput()
            val schedule = BlindStructureCalculator.generateSchedule(input)
            
            schedule.forEach { level ->
                if (level.smallBlind % scenario.smallestChip != 0) {
                    chipViolations.add(
                        "$scenario: Level ${level.level} small blind (${level.smallBlind}) not divisible by ${scenario.smallestChip}"
                    )
                }
                
                if (level.bigBlind % scenario.smallestChip != 0) {
                    chipViolations.add(
                        "$scenario: Level ${level.level} big blind (${level.bigBlind}) not divisible by ${scenario.smallestChip}"
                    )
                }
            }
        }
        
        if (chipViolations.isNotEmpty()) {
            println("=== CHIP DENOMINATION VIOLATIONS ===")
            chipViolations.take(10).forEach { println("  $it") }
            if (chipViolations.size > 10) {
                println("  ... and ${chipViolations.size - 10} more violations")
            }
        }
        
        assertTrue(
            chipViolations.isEmpty(),
            "Found ${chipViolations.size} chip denomination violations"
        )
    }

    @Test
    fun `sample scenarios detailed analysis`() {
        val sampleScenarios = listOf(
            TestScenario(180, 25, 5000, 20),  // 3 hour, standard chips
            TestScenario(120, 10, 2000, 10),  // 2 hour, small chips, fast rounds
            TestScenario(120, 25, 3000, 10),  // 2 hour, standard chips, fast rounds
            TestScenario(180, 50, 3000, 10)   // 3 hour, large chips, fast rounds
        )
        
        println("=== DETAILED ANALYSIS OF SAMPLE SCENARIOS ===")
        
        sampleScenarios.forEach { scenario ->
            val input = scenario.toInput()
            val schedule = BlindStructureCalculator.generateSchedule(input)
            
            println("\n--- $scenario ---")
            println("Generated ${schedule.size} levels:")
            
            schedule.forEachIndexed { index, level ->
                val growth = if (index > 0) {
                    val prev = schedule[index - 1]
                    FormatUtils.formatPercent(((level.smallBlind.toDouble() / prev.smallBlind.toDouble()) - 1) * 100)
                } else {
                    "start"
                }
                
                println("  Level ${level.level}: ${level.smallBlind}/${level.bigBlind} (growth: $growth)")
            }
            
            val finalLevel = schedule.last()
            val finalBBRatio = finalLevel.bigBlind.toDouble() / scenario.startingStack
            println("  Final BB ratio: ${String.format("%.2fx", finalBBRatio)} starting stack")
        }
    }
}