package com.huntercoles.pokerpayout.core.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * @param bigBlindAnteFromLevel 0 for no ante; otherwise the 1-based level from which every level has a
 *   big-blind ante (the player in the big blind antes one big blind for the table).
 */
data class BlindStructureInput(
    val players: Int,
    val targetDurationMinutes: Int,
    val smallestChip: Int,
    val startingStack: Int,
    val roundLengthMinutes: Int,
    val bigBlindAnteFromLevel: Int = 0
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

    /**
     * Generates a blind schedule:
     * - number of rounds = duration / round length (at least 2)
     * - first small blind = smallest chip, final small blind = starting stack
     * - intermediate blinds from [BlindFittingAlgorithm], every step within 1.3x-2.0x
     * - `roundStartMinute` counts playing time only; breaks are laid out by the clock
     *
     * @throws BlindLadderException when no in-band ladder exists
     */
    fun generateSchedule(input: BlindStructureInput): List<BlindLevel> {
        require(input.players > 0) { "Player count must be greater than zero" }
        require(input.targetDurationMinutes > 0) { "Target duration must be positive" }
        require(input.smallestChip > 0) { "Smallest chip must be positive" }
        require(input.startingStack > 0) { "Starting stack must be positive" }
        require(input.roundLengthMinutes > 0) { "Round length must be positive" }
        require(input.bigBlindAnteFromLevel >= 0) { "Ante level can't be negative" }

        val numRounds = (input.targetDurationMinutes / input.roundLengthMinutes).coerceAtLeast(2)

        val result = BlindFittingAlgorithm.fitBlinds(
            numRounds = numRounds,
            smallestChip = input.smallestChip,
            startingChips = input.startingStack
        )

        return result.blinds.mapIndexed { index, smallBlind ->
            val levelNumber = index + 1
            val bigBlind = smallBlind * 2
            BlindLevel(
                level = levelNumber,
                smallBlind = smallBlind,
                bigBlind = bigBlind,
                ante = bigBlindAnte(levelNumber, bigBlind, input.bigBlindAnteFromLevel),
                roundStartMinute = index * input.roundLengthMinutes
            )
        }
    }

    /**
     * Generates the next overtime level, doubling the previous one, for play that runs past the
     * scheduled duration.
     *
     * @param currentSchedule The schedule so far (including any overtime already added)
     * @param roundLengthMinutes Duration of each round
     * @param bigBlindAnteFromLevel As in [BlindStructureInput]; 0 for no ante
     * @return The next overtime level, or null for an empty schedule or when its big blind would be
     *   too big to count (over 2,147,483,647 chips: a starting stack above 134,217,727 gets there
     *   within three overtime levels), so overtime stops instead of showing negative blinds
     */
    fun generateNextOvertimeLevel(
        currentSchedule: List<BlindLevel>,
        roundLengthMinutes: Int,
        bigBlindAnteFromLevel: Int = 0
    ): BlindLevel? {
        val lastLevel = currentSchedule.lastOrNull()
        if (lastLevel == null || lastLevel.smallBlind * 2L * 2L > Int.MAX_VALUE) return null
        val levelNumber = currentSchedule.size + 1
        val smallBlind = lastLevel.smallBlind * 2
        val bigBlind = smallBlind * 2
        return BlindLevel(
            level = levelNumber,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            ante = bigBlindAnte(levelNumber, bigBlind, bigBlindAnteFromLevel),
            roundStartMinute = lastLevel.roundStartMinute + roundLengthMinutes
        )
    }

    private fun bigBlindAnte(levelNumber: Int, bigBlind: Int, fromLevel: Int): Int =
        if (fromLevel in 1..levelNumber) bigBlind else 0
}
