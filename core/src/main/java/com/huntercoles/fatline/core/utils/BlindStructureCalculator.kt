package com.huntercoles.fatline.core.utils

import android.os.Parcelable
import com.huntercoles.fatline.core.constants.BlindStructureConstants
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
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
    val roundStartMinute: Int,
    val isSuddenDeath: Boolean
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
        val finalSmallBlind = input.startingStack

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
                roundStartMinute = index * roundLengthMinutes,
                isSuddenDeath = index >= regularLevelCutoff
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
        val growthFactor = if (totalLevels <= 1) {
            1.0
        } else {
            (endValue / startValue).pow(1.0 / (totalLevels - 1).toDouble())
        }

        val progression = mutableListOf<Int>()
        var lastValue = allowedBlinds[startIndex]
        var lastIndex = startIndex
        progression += lastValue

        for (level in 1 until totalLevels - 1) {
            val rawValue = startValue * growthFactor.pow(level.toDouble())
            val ceiling = ceil(rawValue).toInt()
            val snappedIndexCandidate = allowedBlinds.indexOfFirst { it >= ceiling }
            val snappedIndex = when {
                snappedIndexCandidate == -1 -> finalIndex
                snappedIndexCandidate > finalIndex -> finalIndex
                snappedIndexCandidate < startIndex -> startIndex
                else -> snappedIndexCandidate
            }
            val adjustedIndex = when {
                snappedIndex <= lastIndex && lastIndex < finalIndex -> (lastIndex + 1).coerceAtMost(finalIndex)
                else -> snappedIndex
            }
            val snapped = allowedBlinds[adjustedIndex]
            val candidate = snapped
            progression += candidate
            lastValue = candidate
            lastIndex = adjustedIndex
        }

        progression += allowedBlinds[finalIndex]
        return progression
    }

    private fun buildAllowedSmallBlindList(smallestChip: Int, targetSmallBlind: Int): List<Int> {
        val factor = max(1, smallestChip / 5)
        val values = sortedSetOf<Int>()
        values += smallestChip
        values += targetSmallBlind

        BlindStructureConstants.STANDARD_SMALL_BLIND_BASES
            .map { it * factor }
            .filter { it % smallestChip == 0 }
            .forEach { values += it }

        var currentMax = values.maxOrNull() ?: smallestChip
        while (currentMax < targetSmallBlind * 2) {
            currentMax *= 2
            values += currentMax
        }

        return values.filter { it >= smallestChip }.sorted()
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