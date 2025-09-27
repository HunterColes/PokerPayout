package com.huntercoles.fatline.core.utils

import android.os.Parcelable
import com.huntercoles.fatline.core.constants.BlindStructureConstants
import kotlin.math.ceil
import kotlin.math.ln
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
    private const val MIN_GROWTH_PER_LEVEL = 1.33
    private const val MAX_GROWTH_PER_LEVEL = 2.0
    private const val EXTRA_DURATION_MINUTES = 60
    private const val MAX_ADDITIONAL_LEVEL_ATTEMPTS = 5
    private const val ANTE_START_LEVEL_INDEX = 4 // zero-based (level 5)

    fun generateSchedule(input: BlindStructureInput): List<BlindLevel> {
        require(input.players > 0) { "Player count must be greater than zero" }
        require(input.targetDurationMinutes > 0) { "Target duration must be positive" }
        require(input.smallestChip > 0) { "Smallest chip must be positive" }
        require(input.startingStack > 0) { "Starting stack must be positive" }
        require(input.roundLengthMinutes > 0) { "Round length must be positive" }

        val baseSmallBlind = input.smallestChip
        val regularLevelTarget = max(1, ceil(input.targetDurationMinutes.toDouble() / input.roundLengthMinutes).toInt())
        val overtimeLevelTarget = max(2, ceil((input.targetDurationMinutes + EXTRA_DURATION_MINUTES).toDouble() / input.roundLengthMinutes).toInt())

        val minimumFinalSmall = max(input.startingStack, baseSmallBlind)
        val finalSmallBlind = roundUpToStandardValue(minimumFinalSmall, baseSmallBlind)
        val allowedSmallBlinds = buildAllowedSmallBlindList(baseSmallBlind, finalSmallBlind)

        val levelCountInitial = computeTargetLevelCount(
            baseSmallBlind = baseSmallBlind,
            finalSmallBlind = finalSmallBlind,
            target = overtimeLevelTarget
        )

        var levelCount = levelCountInitial
        var additionalAttempts = 0
        while (additionalAttempts <= MAX_ADDITIONAL_LEVEL_ATTEMPTS) {
            val schedule = attemptGenerateSchedule(
                levelCount = levelCount,
                regularLevelCutoff = regularLevelTarget,
                input = input,
                finalSmallBlind = finalSmallBlind,
                allowedSmallBlinds = allowedSmallBlinds
            )

            if (schedule != null) {
                return schedule
            }

            levelCount++
            additionalAttempts++
        }

        return buildFallbackSchedule(input, finalSmallBlind, regularLevelTarget)
    }

    private fun computeTargetLevelCount(baseSmallBlind: Int, finalSmallBlind: Int, target: Int): Int {
        if (finalSmallBlind <= baseSmallBlind) return max(2, target)

        val ratio = finalSmallBlind.toDouble() / baseSmallBlind
        val minLevels = ceil(ln(ratio) / ln(MAX_GROWTH_PER_LEVEL)).toInt() + 1
        val maxLevels = max(minLevels, (ln(ratio) / ln(MIN_GROWTH_PER_LEVEL)).toInt() + 1)
        return target.coerceIn(minLevels, maxLevels)
    }

    private fun attemptGenerateSchedule(
        levelCount: Int,
        regularLevelCutoff: Int,
        input: BlindStructureInput,
        finalSmallBlind: Int,
        allowedSmallBlinds: List<Int>
    ): List<BlindLevel>? {
        if (levelCount < 2) return null

        val startIndex = allowedSmallBlinds.indexOf(input.smallestChip)
        val finalIndex = allowedSmallBlinds.indexOf(finalSmallBlind)
        if (startIndex == -1 || finalIndex == -1 || finalIndex <= startIndex) return null

        val sequence = buildSequence(
            values = allowedSmallBlinds,
            startIndex = startIndex,
            finalIndex = finalIndex,
            totalLevels = levelCount
        ) ?: return null

        if (sequence.size != levelCount) return null
        if (sequence.last() * 2 < input.startingStack * 2) return null

        return sequence.mapIndexed { index, smallBlind ->
            val bigBlind = smallBlind * 2
            val ante = if (input.includeAnte && index >= ANTE_START_LEVEL_INDEX) {
                roundAnte((smallBlind / 2).coerceAtLeast(input.smallestChip), input.smallestChip)
            } else {
                0
            }

            BlindLevel(
                level = index + 1,
                smallBlind = smallBlind,
                bigBlind = bigBlind,
                ante = ante,
                roundStartMinute = index * input.roundLengthMinutes,
                isSuddenDeath = index >= regularLevelCutoff
            )
        }
    }

    private fun buildSequence(
        values: List<Int>,
        startIndex: Int,
        finalIndex: Int,
        totalLevels: Int
    ): List<Int>? {
        val memo = HashMap<Pair<Int, Int>, List<Int>?>(totalLevels * values.size)

        fun dfs(index: Int, stepsRemaining: Int): List<Int>? {
            if (stepsRemaining == 0) {
                return if (index == finalIndex) listOf(values[index]) else null
            }

            val key = index to stepsRemaining
            memo[key]?.let { return it }

            val currentValue = values[index]
            val minCandidate = ceil(currentValue * MIN_GROWTH_PER_LEVEL).toInt()
            val maxCandidate = (currentValue * MAX_GROWTH_PER_LEVEL).toInt()

            for (nextIndex in index + 1..finalIndex) {
                val candidateValue = values[nextIndex]
                if (candidateValue < minCandidate) continue
                if (candidateValue > maxCandidate) break

                val remainingSteps = stepsRemaining - 1
                if (!canReachFinal(values, nextIndex, finalIndex, remainingSteps)) continue

                val tail = dfs(nextIndex, remainingSteps)
                if (tail != null) {
                    val result = listOf(currentValue) + tail
                    memo[key] = result
                    return result
                }
            }

            memo[key] = null
            return null
        }

        return dfs(startIndex, totalLevels - 1)
    }

    private fun canReachFinal(
        values: List<Int>,
        currentIndex: Int,
        finalIndex: Int,
        stepsRemaining: Int
    ): Boolean {
        if (stepsRemaining == 0) {
            return currentIndex == finalIndex
        }
        if (finalIndex - currentIndex < stepsRemaining) return false

        val currentValue = values[currentIndex].toDouble()
        val finalValue = values[finalIndex].toDouble()
        val minReachable = currentValue * MIN_GROWTH_PER_LEVEL.pow(stepsRemaining.toDouble())
        val maxReachable = currentValue * MAX_GROWTH_PER_LEVEL.pow(stepsRemaining.toDouble())
        return finalValue in minReachable..maxReachable
    }

    private fun buildAllowedSmallBlindList(smallestChip: Int, finalSmallBlind: Int): List<Int> {
        val factor = max(1, smallestChip / 5)
        val values = sortedSetOf<Int>()
        values += smallestChip

        BlindStructureConstants.STANDARD_SMALL_BLIND_BASES
            .map { it * factor }
            .filter { it % smallestChip == 0 }
            .forEach { values += it }

        var currentMax = values.last()
        while (currentMax < finalSmallBlind * 2) {
            currentMax *= 2
            values += currentMax
        }

        return values.filter { it >= smallestChip }.sorted()
    }

    private fun roundUpToStandardValue(value: Int, smallestChip: Int): Int {
        val allowed = buildAllowedSmallBlindList(smallestChip, value * 2)
        return allowed.firstOrNull { it >= value } ?: allowed.last()
    }

    private fun buildFallbackSchedule(
        input: BlindStructureInput,
        finalSmallBlind: Int,
        regularLevelCutoff: Int
    ): List<BlindLevel> {
        val levels = mutableListOf(input.smallestChip)
        while (levels.last() < finalSmallBlind) {
            val nextCandidate = (levels.last() * MAX_GROWTH_PER_LEVEL).toInt().coerceAtLeast(levels.last() + input.smallestChip)
            val rounded = roundUpToStandardValue(nextCandidate, input.smallestChip)
            if (rounded <= levels.last()) {
                levels += levels.last() * 2
            } else {
                levels += rounded
            }
        }

        return levels.mapIndexed { index, smallBlind ->
            BlindLevel(
                level = index + 1,
                smallBlind = smallBlind,
                bigBlind = smallBlind * 2,
                ante = if (input.includeAnte && index >= ANTE_START_LEVEL_INDEX) {
                    roundAnte((smallBlind / 2).coerceAtLeast(input.smallestChip), input.smallestChip)
                } else 0,
                roundStartMinute = index * input.roundLengthMinutes,
                isSuddenDeath = index >= regularLevelCutoff
            )
        }
    }

    private fun roundAnte(targetAnte: Int, baseSmallBlind: Int): Int {
        if (targetAnte <= 0) return 0
        val alignmentUnit = baseSmallBlind
        val rounded = ((targetAnte + alignmentUnit - 1) / alignmentUnit) * alignmentUnit
        return max(alignmentUnit, rounded)
    }
}