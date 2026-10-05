package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.ColorUpPlanner

/** Breaks every [everyLevels] levels (0 = none), each [lengthMinutes] long, showing [message]. */
data class BreakSettings(
    val everyLevels: Int = 0,
    val lengthMinutes: Int = DEFAULT_LENGTH_MINUTES,
    val message: String = ""
) {
    val enabled: Boolean get() = everyLevels > 0 && lengthMinutes > 0

    companion object {
        const val DEFAULT_LENGTH_MINUTES = 10
        const val MAX_MESSAGE_LENGTH = 60
    }
}

/** One stretch of the clock: a blind level or a break. Times are seconds of play from the start. */
sealed interface ClockSegment {
    val startSeconds: Int
    val durationSeconds: Int
    val endSeconds: Int get() = startSeconds + durationSeconds

    /** Chip values no longer needed from here on, to color up during this segment. */
    val colorUp: List<Int>
}

data class LevelSegment(
    /** Index into [ClockTimeline.levels]. */
    val index: Int,
    val level: BlindLevel,
    val isOvertime: Boolean,
    override val startSeconds: Int,
    override val durationSeconds: Int,
    override val colorUp: List<Int> = emptyList()
) : ClockSegment

data class BreakSegment(
    /** 1-based. */
    val number: Int,
    /** 1-based number of the level the break follows. */
    val afterLevel: Int,
    val message: String,
    override val startSeconds: Int,
    override val durationSeconds: Int,
    override val colorUp: List<Int> = emptyList()
) : ClockSegment

/**
 * The whole tournament laid out in time: the regular levels with breaks between them, then up to
 * [BlindStructureCalculator.MAX_OVERTIME_LEVELS] doubling overtime levels. The clock is a single number,
 * seconds of play, and everything on screen is looked up from it here.
 */
class ClockTimeline private constructor(
    val segments: List<ClockSegment>,
    /** When the last regular level ends and overtime begins. */
    val regularEndSeconds: Int
) {
    val levels: List<LevelSegment> = segments.filterIsInstance<LevelSegment>()

    /** When the last overtime level ends: the clock finishes here. */
    val endSeconds: Int = segments.lastOrNull()?.endSeconds ?: 0

    val isEmpty: Boolean get() = segments.isEmpty()

    /** Index of the segment running at [elapsedSeconds]; the last one once the clock has finished. */
    fun segmentIndexAt(elapsedSeconds: Int): Int {
        if (segments.isEmpty()) return -1
        val index = segments.indexOfLast { it.startSeconds <= elapsedSeconds }
        return index.coerceIn(0, segments.lastIndex)
    }

    fun segmentAt(elapsedSeconds: Int): ClockSegment? = segments.getOrNull(segmentIndexAt(elapsedSeconds))

    /** The first level after segment [segmentIndex], or null at the end. */
    fun nextLevelAfter(segmentIndex: Int): LevelSegment? =
        segments.drop(segmentIndex + 1).firstOrNull { it is LevelSegment } as LevelSegment?

    /** Overtime levels that have started by [elapsedSeconds]; later ones stay hidden. */
    fun overtimeLevelsRevealedAt(elapsedSeconds: Int): Int =
        levels.count { it.isOvertime && it.startSeconds <= elapsedSeconds }

    /** Segments to show at [elapsedSeconds]: everything except overtime levels that haven't started. */
    fun visibleSegmentsAt(elapsedSeconds: Int): List<ClockSegment> =
        segments.filter { it !is LevelSegment || !it.isOvertime || it.startSeconds <= elapsedSeconds }

    companion object {
        val EMPTY = ClockTimeline(emptyList(), 0)

        fun build(
            regularLevels: List<BlindLevel>,
            roundLengthMinutes: Int,
            breaks: BreakSettings = BreakSettings(),
            smallestChip: Int = regularLevels.firstOrNull()?.smallBlind ?: 1,
            bigBlindAnteFromLevel: Int = 0
        ): ClockTimeline {
            if (regularLevels.isEmpty() || roundLengthMinutes <= 0) return EMPTY

            val allLevels = regularLevels.toMutableList()
            repeat(BlindStructureCalculator.MAX_OVERTIME_LEVELS) {
                BlindStructureCalculator.generateNextOvertimeLevel(allLevels, roundLengthMinutes, bigBlindAnteFromLevel)
                    ?.let { allLevels += it }
            }
            val breakAfter = if (breaks.enabled) {
                (0 until regularLevels.lastIndex).filter { (it + 1) % breaks.everyLevels == 0 }
            } else {
                emptyList()
            }
            val colorUps = placeColorUps(ColorUpPlanner.plan(allLevels, smallestChip), breakAfter)

            val roundSeconds = roundLengthMinutes * SECONDS_PER_MINUTE
            val breakSeconds = breaks.lengthMinutes * SECONDS_PER_MINUTE
            val segments = mutableListOf<ClockSegment>()
            var clock = 0
            var regularEnd = 0
            allLevels.forEachIndexed { index, level ->
                val isOvertime = index >= regularLevels.size
                val colorUp = colorUps.levels[index].orEmpty()
                segments += LevelSegment(index, level, isOvertime, clock, roundSeconds, colorUp)
                clock += roundSeconds
                if (index == regularLevels.lastIndex) regularEnd = clock
                if (index in breakAfter) {
                    val number = breakAfter.indexOf(index) + 1
                    segments += BreakSegment(
                        number = number,
                        afterLevel = level.level,
                        message = breaks.message.trim(),
                        startSeconds = clock,
                        durationSeconds = breakSeconds,
                        colorUp = colorUps.breaks[number].orEmpty()
                    )
                    clock += breakSeconds
                }
            }
            return ClockTimeline(segments, regularEnd)
        }

        private class ColorUpPlacement(val levels: Map<Int, List<Int>>, val breaks: Map<Int, List<Int>>)

        /**
         * A chip that drops out from level i is colored up at the first break whose next level is i or
         * later (a break is the time to do it); with no such break, it's flagged on level i itself.
         */
        private fun placeColorUps(plan: Map<Int, List<Int>>, breakAfter: List<Int>): ColorUpPlacement {
            val onLevels = mutableMapOf<Int, MutableList<Int>>()
            val onBreaks = mutableMapOf<Int, MutableList<Int>>()
            plan.forEach { (levelIndex, chips) ->
                val breakIndex = breakAfter.indexOfFirst { it + 1 >= levelIndex }
                if (breakIndex >= 0) {
                    onBreaks.getOrPut(breakIndex + 1) { mutableListOf() } += chips
                } else {
                    onLevels.getOrPut(levelIndex) { mutableListOf() } += chips
                }
            }
            return ColorUpPlacement(onLevels, onBreaks)
        }

        private const val SECONDS_PER_MINUTE = 60
    }
}
