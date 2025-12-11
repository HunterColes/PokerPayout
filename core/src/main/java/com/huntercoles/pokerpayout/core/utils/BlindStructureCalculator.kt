package com.huntercoles.pokerpayout.core.utils

import android.os.Parcelable
import com.huntercoles.pokerpayout.core.constants.BlindStructureConstants
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
    const val MAX_OVERTIME_LEVELS = 3
    private const val ANTE_START_LEVEL_INDEX = 4 // zero-based (level 5)

    fun generateSchedule(input: BlindStructureInput): List<BlindLevel> {
        require(input.players > 0) { "Player count must be greater than zero" }
        require(input.targetDurationMinutes > 0) { "Target duration must be positive" }
        require(input.smallestChip > 0) { "Smallest chip must be positive" }
        require(input.startingStack > 0) { "Starting stack must be positive" }
        require(input.roundLengthMinutes > 0) { "Round length must be positive" }

        // Calculate exact number of regular levels (no overtime upfront)
        val regularLevels = ceil(input.targetDurationMinutes.toDouble() / input.roundLengthMinutes)
            .toInt()
            .coerceAtLeast(2)
        
        // Final regular level should have small blind = starting stack
        val finalSmallBlind = input.startingStack

        val schedule = generateBlindProgression(
            startingSmallBlind = input.smallestChip,
            finalSmallBlind = finalSmallBlind,
            totalLevels = regularLevels,
            regularLevelCutoff = regularLevels,
            roundLengthMinutes = input.roundLengthMinutes,
            includeAnte = input.includeAnte,
            smallestChip = input.smallestChip
        )

        return if (schedule.isNotEmpty()) schedule else buildFallbackSchedule(
            input = input,
            finalSmallBlind = finalSmallBlind,
            totalLevels = regularLevels,
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
        if (totalLevels <= 1) return listOf(allowedBlinds[finalIndex])
        if (totalLevels == 2) return listOf(allowedBlinds[startIndex], allowedBlinds[finalIndex])
        if (startIndex == finalIndex) {
            return List(totalLevels) { allowedBlinds[startIndex] }
        }

        val startValue = allowedBlinds[startIndex].toDouble()
        val endValue = allowedBlinds[finalIndex].toDouble()
        
        val progression = mutableListOf<Int>()
        progression += allowedBlinds[startIndex]

        // Generate intermediate levels (totalLevels - 2)
        for (level in 1 until totalLevels - 1) {
            // Calculate ideal position using exponential growth
            val position = level.toDouble() / (totalLevels - 1).toDouble()
            val targetValue = startValue * (endValue / startValue).pow(position)
            
            // Find closest valid blind that's greater than the last added
            var candidateIndex = allowedBlinds.indexOfFirst { it >= targetValue.toInt() && it > progression.last() }
            if (candidateIndex == -1) {
                // If we can't find one greater, just take the next index after current
                val lastAddedIndex = allowedBlinds.indexOf(progression.last())
                candidateIndex = (lastAddedIndex + 1).coerceAtMost(finalIndex - 1)
            }
            
            progression += allowedBlinds[candidateIndex]
        }

        // Always add the exact final value as the last level
        progression += allowedBlinds[finalIndex]
        
        return progression.distinct().let { unique ->
            // If after removing duplicates we have fewer levels, it means we had duplicates
            // Just return what we have - the tests will need to be more lenient or we need different input
            if (unique.size != totalLevels) {
                // Rebuild to hit exact count
                val step = (finalIndex - startIndex).toDouble() / (totalLevels - 1)
                (0 until totalLevels).map { i ->
                    val targetIndex = (startIndex + step * i).toInt().coerceIn(startIndex, finalIndex)
                    allowedBlinds[targetIndex]
                }.distinct().let {
                    // Ensure we have start and end values
                    (listOf(allowedBlinds[startIndex]) + it + listOf(allowedBlinds[finalIndex])).distinct().take(totalLevels)
                }
            } else {
                unique
            }
        }
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
            
            // Check if number is "smooth" - should end in 0 if value > threshold
            val isSmooth = value <= BlindStructureConstants.SMOOTH_NUMBER_THRESHOLD || (value % 10 == 0)
            
            // Use relaxed growth bounds from constants
            if (growthRate >= BlindStructureConstants.MIN_BLIND_GROWTH_RATE && 
                growthRate <= BlindStructureConstants.MAX_BLIND_GROWTH_RATE && isSmooth) {
                filteredValues.add(value)
                lastValue = value
            }
        }
        
        // CRITICAL: Always ensure the target value is in the list
        if (targetSmallBlind !in filteredValues) {
            filteredValues.add(targetSmallBlind)
            filteredValues.sort()
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

    /**
     * Generates overtime blind levels that double each time.
     * Call this to get the next overtime level when play extends past the tournament duration.
     * 
     * @param currentSchedule The current visible blind schedule (including any overtime already added)
     * @param roundLengthMinutes Duration of each round
     * @param includeAnte Whether to include antes
     * @return The next overtime level, or null if unable to generate
     */
    fun generateNextOvertimeLevel(
        currentSchedule: List<BlindLevel>,
        roundLengthMinutes: Int,
        includeAnte: Boolean = false
    ): BlindLevel? {
        if (currentSchedule.isEmpty()) return null

        val lastLevel = currentSchedule.last()
        val totalLevelNumber = currentSchedule.size + 1
        
        // Double the previous small blind
        val newSmallBlind = lastLevel.smallBlind * 2
        val newBigBlind = newSmallBlind * 2
        
        // Calculate start minute (continuing from where previous level ends)
        val newStartMinute = lastLevel.roundStartMinute + roundLengthMinutes
        
        // Ante handling for overtime
        val newAnte = if (includeAnte && lastLevel.ante > 0) {
            lastLevel.ante * 2
        } else if (includeAnte) {
            newSmallBlind / 2
        } else {
            0
        }
        
        return BlindLevel(
            level = totalLevelNumber,
            smallBlind = newSmallBlind,
            bigBlind = newBigBlind,
            ante = newAnte,
            roundStartMinute = newStartMinute
        )
    }
}