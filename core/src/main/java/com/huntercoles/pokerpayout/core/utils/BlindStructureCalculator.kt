package com.huntercoles.pokerpayout.core.utils

import android.os.Parcelable
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

    /**
     * Generates a blind schedule using simplified algorithm:
     * - Number of rounds = duration / roundLength
     * - First small blind = smallestChip
     * - Final small blind = startingStack
     * - Intermediate blinds fit to exponential growth curve
     */
    fun generateSchedule(input: BlindStructureInput): List<BlindLevel> {
        require(input.players > 0) { "Player count must be greater than zero" }
        require(input.targetDurationMinutes > 0) { "Target duration must be positive" }
        require(input.smallestChip > 0) { "Smallest chip must be positive" }
        require(input.startingStack > 0) { "Starting stack must be positive" }
        require(input.roundLengthMinutes > 0) { "Round length must be positive" }

        // Calculate exact number of regular levels
        val numRounds = (input.targetDurationMinutes / input.roundLengthMinutes).coerceAtLeast(2)
        
        // Use fitting algorithm to generate smooth blind progression
        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = numRounds,
            smallestChip = input.smallestChip,
            startingChips = input.startingStack
        )
        
        val smallBlinds = result.blinds
        // Fit score is available in result.fitScore but not used in UI yet

        // Convert to BlindLevel objects
        return smallBlinds.mapIndexed { index, smallBlind ->
            val bigBlind = smallBlind * 2
            val ante = if (input.includeAnte && index >= ANTE_START_LEVEL_INDEX) {
                calculateAnte(smallBlind, input.smallestChip)
            } else {
                0
            }
            
            BlindLevel(
                level = index + 1,
                smallBlind = smallBlind,
                bigBlind = bigBlind,
                ante = ante,
                roundStartMinute = index * input.roundLengthMinutes
            )
        }
    }
    
    /**
     * Calculates ante as half of small blind, rounded to nearest chip denomination.
     */
    private fun calculateAnte(smallBlind: Int, smallestChip: Int): Int {
        if (smallBlind <= 0) return 0
        val targetAnte = smallBlind / 2
        val rounded = ((targetAnte + smallestChip - 1) / smallestChip) * smallestChip
        return rounded.coerceAtLeast(smallestChip)
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