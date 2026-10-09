package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
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

    /** What each chip of [colorUp] is exchanged for, in the same order. */
    val colorUpSwaps: List<ColorUpSwap>
}

/** At a color-up, [chip]s are exchanged for [into]s (25s for 100s: four for one). */
data class ColorUpSwap(val chip: Int, val into: Int)

data class LevelSegment(
    /** Index into [ClockTimeline.levels]. */
    val index: Int,
    val level: BlindLevel,
    val isOvertime: Boolean,
    override val startSeconds: Int,
    override val durationSeconds: Int,
    override val colorUp: List<Int> = emptyList(),
    override val colorUpSwaps: List<ColorUpSwap> = emptyList()
) : ClockSegment

data class BreakSegment(
    /** 1-based. */
    val number: Int,
    /** 1-based number of the level the break follows. */
    val afterLevel: Int,
    val message: String,
    override val startSeconds: Int,
    override val durationSeconds: Int,
    override val colorUp: List<Int> = emptyList(),
    override val colorUpSwaps: List<ColorUpSwap> = emptyList()
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
    fun nextLevelAfter(segmentIndex: Int): LevelSegment? {
        for (i in (segmentIndex + 1).coerceAtLeast(0) until segments.size) {
            (segments[i] as? LevelSegment)?.let { return it }
        }
        return null
    }

    /** Overtime levels that have started by [elapsedSeconds]; later ones stay hidden. */
    fun overtimeLevelsRevealedAt(elapsedSeconds: Int): Int =
        levels.count { it.isOvertime && it.startSeconds <= elapsedSeconds }

    /** Segments to show at [elapsedSeconds]: everything except overtime levels that haven't started. */
    fun visibleSegmentsAt(elapsedSeconds: Int): List<ClockSegment> =
        segments.filter { it !is LevelSegment || !it.isOvertime || it.startSeconds <= elapsedSeconds }

    companion object {
        val EMPTY = ClockTimeline(emptyList(), 0)

        /**
         * The timeline for [regularLevels]. Color-ups follow [chipSet], your chip set, when it has a
         * chip that pays [smallestChip] (PP-091 #9): the chips you own, planned as the chip set's
         * color-up plan plans them. Otherwise they follow a common home set's chips.
         */
        @Suppress("LongParameterList") // the clock's setup, each part with a default
        fun build(
            regularLevels: List<BlindLevel>,
            roundLengthMinutes: Int,
            breaks: BreakSettings = BreakSettings(),
            smallestChip: Int = regularLevels.firstOrNull()?.smallBlind ?: 1,
            bigBlindAnteFromLevel: Int = 0,
            chipSet: ChipSetChips? = null
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
            val chain = chipSet?.chainFor(smallestChip)?.takeIf { it.isNotEmpty() }
            val plan = chain?.let { ColorUpPlanner.planFor(allLevels, it) } ?: ColorUpPlanner.plan(allLevels, smallestChip)
            val colorUps = placeColorUps(plan, breakAfter)
            val swaps = SwapTracker(chain)

            val roundSeconds = roundLengthMinutes * SECONDS_PER_MINUTE
            val breakSeconds = breaks.lengthMinutes * SECONDS_PER_MINUTE
            val segments = mutableListOf<ClockSegment>()
            var clock = 0
            var regularEnd = 0
            allLevels.forEachIndexed { index, level ->
                val isOvertime = index >= regularLevels.size
                val chips = colorUps.levels[index].orEmpty()
                segments += LevelSegment(index, level, isOvertime, clock, roundSeconds, chips, swaps.after(chips))
                clock += roundSeconds
                if (index == regularLevels.lastIndex) regularEnd = clock
                if (index in breakAfter) {
                    val number = breakAfter.indexOf(index) + 1
                    val onBreak = colorUps.breaks[number].orEmpty()
                    segments += BreakSegment(
                        number = number,
                        afterLevel = level.level,
                        message = breaks.message.trim(),
                        startSeconds = clock,
                        durationSeconds = breakSeconds,
                        colorUp = onBreak,
                        colorUpSwaps = swaps.after(onBreak)
                    )
                    clock += breakSeconds
                }
            }
            return ClockTimeline(segments, regularEnd)
        }

        private class ColorUpPlacement(val levels: Map<Int, List<Int>>, val breaks: Map<Int, List<Int>>)

        /**
         * What colored-up chips are exchanged for, color-up by color-up in the clock's order. With
         * your chip set ([chain]), the next chip of it that stays in play and is a multiple, as the
         * chip set's plan does it (25s and 50s colored up together both go into 100s); otherwise the
         * next chip up of a common home set (25s into 100s, then 100s into 500s), as before.
         */
        private class SwapTracker(private val chain: List<Int>?) {
            private val dropped = mutableSetOf<Int>()

            fun after(chips: List<Int>): List<ColorUpSwap> {
                dropped += chips
                return chips.mapNotNull { chip -> into(chip)?.let { ColorUpSwap(chip, it) } }
            }

            private fun into(chip: Int): Int? =
                if (chain == null) {
                    ColorUpPlanner.nextChipUp(chip)
                } else {
                    chain.firstOrNull { it > chip && it % chip == 0 && it !in dropped }
                }
        }

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
