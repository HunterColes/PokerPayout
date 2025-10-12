package com.huntercoles.fatline.core.utils

import android.os.Parcelable
import com.huntercoles.fatline.core.constants.BlindStructureConstants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.abs
import kotlinx.parcelize.Parcelize

data class BlindStructureInput(
    val players: Int,
    val targetDurationMinutes: Int,
    val smallestChip: Int,
    val startingStack: Int,
    val roundLengthMinutes: Int,
    val includeAnte: Boolean = false
)

@Parcelize
data class BlindLevel(
    val level: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val ante: Int,
    val roundStartMinute: Int
) : Parcelable

object BlindStructureCalculator {
    private const val EXTRA_LEVELS = 3
    private const val ANTE_START_LEVEL_INDEX = 4 // zero-based (level 5)

    fun generateSchedule(input: BlindStructureInput): List<BlindLevel> {
        require(input.players > 0) { "Player count must be greater than zero" }
        require(input.targetDurationMinutes > 0) { "Target duration must be positive" }
        require(input.smallestChip > 0) { "Smallest chip must be positive" }
        require(input.startingStack > 0) { "Starting stack must be positive" }
        require(input.roundLengthMinutes > 0) { "Round length must be positive" }

        val baseLevelCount = ceil(input.targetDurationMinutes.toDouble() / input.roundLengthMinutes)
            .toInt()
            .coerceAtLeast(1)
        val totalLevels = (baseLevelCount + EXTRA_LEVELS).coerceAtLeast(2)
        val regularLevels = baseLevelCount
        
        // Ensure final small blind produces big blind >= starting stack * 2
        val finalSmallBlind = input.startingStack.coerceAtLeast(input.smallestChip)

        val schedule = generateBlindProgression(
            startingSmallBlind = input.smallestChip,
            finalSmallBlind = finalSmallBlind,
            totalLevels = totalLevels,
            regularLevelCutoff = regularLevels,
            roundLengthMinutes = input.roundLengthMinutes,
            includeAnte = input.includeAnte,
            smallestChip = input.smallestChip
        )

        return if (schedule.isNotEmpty()) schedule else buildFallbackSchedule(
            input = input,
            finalSmallBlind = finalSmallBlind,
            totalLevels = totalLevels,
            regularLevelCutoff = regularLevels
        )
    }
    
    private fun generateBlindProgression(
        startingSmallBlind: Int,
        finalSmallBlind: Int,
        totalLevels: Int,
        regularLevelCutoff: Int,
        roundLengthMinutes: Int,
        includeAnte: Boolean,
        smallestChip: Int
    ): List<BlindLevel> {
        if (totalLevels < 2) return emptyList()
        
        // Create allowed blind values (standard poker amounts)
    val allowedBlinds = buildAllowedSmallBlindList(smallestChip, finalSmallBlind)
        
        // Find indices for start and end values
        val startIndex = allowedBlinds.indexOfFirst { it >= startingSmallBlind }
        val finalIndex = allowedBlinds.indexOfFirst { it >= finalSmallBlind }
        
        if (startIndex == -1 || finalIndex == -1) return emptyList()
        
        // Generate smooth progression between start and final indices
        val progression = generateSmoothProgression(
            allowedBlinds = allowedBlinds,
            startIndex = startIndex,
            finalIndex = finalIndex,
            totalLevels = totalLevels
        )
        
        // Convert to BlindLevel objects
        return progression.mapIndexed { index, smallBlind ->
            val bigBlind = smallBlind * 2
            val ante = if (includeAnte && index >= ANTE_START_LEVEL_INDEX) {
                roundAnte((smallBlind / 2).coerceAtLeast(smallestChip), smallestChip)
            } else {
                0
            }
            
            BlindLevel(
                level = index + 1,
                smallBlind = smallBlind,
                bigBlind = bigBlind,
                ante = ante,
                roundStartMinute = index * roundLengthMinutes
            )
        }
    }
    
    private fun generateSmoothProgression(
        allowedBlinds: List<Int>,
        startIndex: Int,
        finalIndex: Int,
        totalLevels: Int
    ): List<Int> {
        if (totalLevels <= 1) return listOf(allowedBlinds[startIndex])
        if (startIndex == finalIndex) {
            return List(totalLevels) { allowedBlinds[startIndex] }
        }

        val startValue = allowedBlinds[startIndex].toDouble()
        val endValue = allowedBlinds[finalIndex].toDouble()
        
        val progression = mutableListOf<Int>()
        var lastIndex = startIndex
        progression += allowedBlinds[startIndex]

        for (level in 1 until totalLevels - 1) {
            // Calculate ideal position using exponential growth
            val position = level.toDouble() / (totalLevels - 1).toDouble()
            val targetValue = startValue * (endValue / startValue).pow(position)
            
            // Find next valid index
            var candidateIndex = allowedBlinds.indexOfFirst { it >= targetValue.toInt() }
            if (candidateIndex == -1) candidateIndex = finalIndex
            candidateIndex = candidateIndex.coerceIn(startIndex, finalIndex)
            
            // Ensure monotonic progression and respect growth bounds
            // CRITICAL: Always advance to the next index to prevent duplicates
            val minNextIndex = lastIndex + 1
            if (candidateIndex < minNextIndex && minNextIndex <= finalIndex) {
                candidateIndex = minNextIndex
            }
            
            // Verify the growth rate is within new bounds (25% to 100%)
            val currentValue = allowedBlinds[candidateIndex].toDouble()
            val lastValue = allowedBlinds[lastIndex].toDouble()
            val growthRate = currentValue / lastValue
            
            // If growth is outside bounds, find a better step
            if (growthRate < 1.25 || growthRate > 2.0) {
                var bestIndex = candidateIndex
                var bestGrowth = growthRate
                
                // Look for the closest growth rate to 1.33 (33% target)
                for (testIndex in (lastIndex + 1)..finalIndex) {
                    val testValue = allowedBlinds[testIndex].toDouble()
                    val testGrowth = testValue / lastValue
                    
                    if (testGrowth >= 1.25 && testGrowth <= 2.0) {
                        // Prefer values closer to 33% growth
                        val targetDistance = kotlin.math.abs(testGrowth - 1.33)
                        val currentDistance = kotlin.math.abs(bestGrowth - 1.33)
                        
                        if (testGrowth >= 1.25 && testGrowth <= 2.0 && 
                            (bestGrowth < 1.25 || bestGrowth > 2.0 || targetDistance < currentDistance)) {
                            bestIndex = testIndex
                            bestGrowth = testGrowth
                        }
                    }
                }
                candidateIndex = bestIndex
            }
            
            // CRITICAL: Always ensure we advance to avoid duplicates
            if (candidateIndex <= lastIndex && candidateIndex < finalIndex) {
                candidateIndex = lastIndex + 1
            }
            
            // Ensure we never add duplicate values - always find next unique value
            while (candidateIndex <= finalIndex && allowedBlinds[candidateIndex] == progression.last()) {
                candidateIndex++
            }
            
            if (candidateIndex <= finalIndex) {
                progression += allowedBlinds[candidateIndex]
                lastIndex = candidateIndex
            }
        }

        // Add final level only if it's different from the last and we haven't already reached it
        val finalValue = allowedBlinds[finalIndex]
        if (progression.isEmpty() || finalValue != progression.last()) {
            progression += finalValue
        }
        
        // Double-check for any duplicates and remove them
        return progression.distinct()
    }

    private fun buildAllowedSmallBlindList(smallestChip: Int, targetSmallBlind: Int): List<Int> {
        val factor = max(1, smallestChip / 5)
        val baseValues = sortedSetOf<Int>()
        baseValues += smallestChip
        baseValues += targetSmallBlind

        BlindStructureConstants.STANDARD_SMALL_BLIND_BASES
            .map { it * factor }
            .filter { it % smallestChip == 0 }
            .forEach { baseValues += it }

        var currentMax = baseValues.maxOrNull() ?: smallestChip
        while (currentMax < targetSmallBlind * 2) {
            currentMax *= 2
            baseValues += currentMax
        }

        val sortedValues = baseValues.filter { it >= smallestChip }.sorted()
        
        // Filter for smooth numbers and growth constraints (25% to 100% between consecutive values)
        // Target 33% average growth with smooth numbers (ending in 0 after 25)
        val filteredValues = mutableListOf<Int>()
        filteredValues.add(sortedValues.first())
        
        var lastValue = sortedValues.first()
        for (value in sortedValues.drop(1)) {
            val growthRate = value.toDouble() / lastValue.toDouble()
            
            // Check if number is "smooth" - should end in 0 if value > 25
            val isSmooth = value <= 25 || (value % 10 == 0)
            
            // Use relaxed growth bounds: 1.25x to 2.0x
            if (growthRate >= 1.25 && growthRate <= 2.0 && isSmooth) {
                filteredValues.add(value)
                lastValue = value
            }
        }
        
        return filteredValues
    }

    private fun buildFallbackSchedule(
        input: BlindStructureInput,
        finalSmallBlind: Int,
        totalLevels: Int,
        regularLevelCutoff: Int
    ): List<BlindLevel> {
        return generateBlindProgression(
            startingSmallBlind = input.smallestChip,
            finalSmallBlind = finalSmallBlind,
            totalLevels = totalLevels,
            regularLevelCutoff = regularLevelCutoff,
            roundLengthMinutes = input.roundLengthMinutes,
            includeAnte = input.includeAnte,
            smallestChip = input.smallestChip
        )
    }

    private fun roundAnte(targetAnte: Int, baseSmallBlind: Int): Int {
        if (targetAnte <= 0) return 0
        val alignmentUnit = baseSmallBlind
        val rounded = ((targetAnte + alignmentUnit - 1) / alignmentUnit) * alignmentUnit
        return max(alignmentUnit, rounded)
    }
}