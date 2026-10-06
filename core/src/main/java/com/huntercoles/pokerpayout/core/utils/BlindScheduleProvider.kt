package com.huntercoles.pokerpayout.core.utils

/**
 * The blind schedule the tournament clock runs, for features outside `tournament-feature` (the
 * chip set's color-up plan, PP-033), so they never import it. `tournament-feature` implements it
 * and binds it with Hilt.
 */
interface BlindScheduleProvider {
    /**
     * The schedule as the clock has it right now: frozen as it was when the clock first started,
     * else from the current Tournament setup. Null when that setup can't make a schedule (the
     * Tournament tab explains why).
     */
    fun currentSchedule(): BlindSchedule?
}

/**
 * Every level the clock can reach, in order: the regular levels, then the overtime levels it
 * adds after them. [breaks] are the breaks between levels.
 */
data class BlindSchedule(
    val levels: List<BlindLevel>,
    val regularLevelCount: Int,
    val breaks: List<ScheduledBreak>,
    /** The first small blind: the smallest chip the game needs. */
    val smallestChip: Int,
    val startingChips: Int
)

/** Break [number] (1-based) comes after level [afterLevel] (1-based). */
data class ScheduledBreak(val number: Int, val afterLevel: Int)
