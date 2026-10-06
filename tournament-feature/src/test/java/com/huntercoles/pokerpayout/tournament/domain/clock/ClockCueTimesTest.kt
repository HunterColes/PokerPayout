package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When the clock's cues fall (PP-083): the chime 4 s before each change (as it always was), the level
 * change itself, and the one-minute warning; crossing-based and never more than 2 s late.
 */
class ClockCueTimesTest {

    /** 3 h of 20-minute rounds, 50 to 5,000, then three overtime levels: 12 levels of 1,200 s. */
    private val levels = BlindStructureCalculator.generateSchedule(BlindStructureInput(10, 180, 50, 5_000, 20))

    private fun timeline(breaks: BreakSettings = BreakSettings()) =
        ClockTimeline.build(levels, roundLengthMinutes = 20, breaks = breaks, smallestChip = 50)

    private val round = 1_200_000L

    /** The clock looked at once a second, as the running clock does, from [fromMillis] to [toMillis]. */
    private fun everySecond(timeline: ClockTimeline, fromMillis: Long, toMillis: Long): List<ClockCue> =
        (fromMillis / 1_000 + 1..toMillis / 1_000).flatMap { second ->
            ClockCueTimes.crossed(timeline, second * 1_000 - 1_000, second * 1_000)
        }

    @Test
    fun `each level has its minute warning, its chime and its change, in that order`() {
        assertEquals(
            listOf(
                ClockCue(ClockCueKind.ONE_MINUTE, round - 60_000),
                ClockCue(ClockCueKind.CHIME, round - 4_000),
                ClockCue(ClockCueKind.LEVEL_CHANGE, round),
            ),
            everySecond(timeline(), 0L, round),
        )
    }

    @Test
    fun `a look sees only what it passed, the first moment it passes it`() {
        val timeline = timeline()
        val warning = round - 60_000

        assertTrue(ClockCueTimes.crossed(timeline, 0L, warning - 1).isEmpty())
        val passed = ClockCueTimes.crossed(timeline, warning - 1_000, warning)
        assertEquals(listOf(ClockCue(ClockCueKind.ONE_MINUTE, warning)), passed)
        // The next look starts where this one ended: the warning isn't passed again
        assertTrue(ClockCueTimes.crossed(timeline, warning, warning + 1_000).isEmpty())
        // Standing still (paused) passes nothing
        assertTrue(ClockCueTimes.crossed(timeline, warning - 1_000, warning - 1_000).isEmpty())
    }

    @Test
    fun `a cue slept through by more than two seconds stays silent`() {
        val timeline = timeline()
        val change = round

        assertEquals(
            listOf(ClockCueKind.CHIME, ClockCueKind.LEVEL_CHANGE),
            ClockCueTimes.crossed(timeline, change - 5_000, change + ClockCueTimes.GRACE_MILLIS).map { it.kind },
        )
        assertTrue(ClockCueTimes.crossed(timeline, change - 5_000, change + ClockCueTimes.GRACE_MILLIS + 1).isEmpty())
        // The minute warning has its own two seconds: 3 s late is too late, even though the chime isn't due yet
        val warning = round - 60_000
        assertTrue(ClockCueTimes.crossed(timeline, warning - 1_000, warning + 3_000).isEmpty())
    }

    @Test
    fun `breaks have the same cues`() {
        val withBreaks = timeline(BreakSettings(everyLevels = 4, lengthMinutes = 10))
        val breakOne = withBreaks.segments.filterIsInstance<BreakSegment>().first()
        val breakEnd = breakOne.endSeconds * 1_000L

        assertEquals(
            listOf(
                ClockCue(ClockCueKind.ONE_MINUTE, breakEnd - 60_000),
                ClockCue(ClockCueKind.CHIME, breakEnd - 4_000),
                ClockCue(ClockCueKind.LEVEL_CHANGE, breakEnd),
            ),
            everySecond(withBreaks, breakOne.startSeconds * 1_000L, breakEnd),
        )
    }

    @Test
    fun `a minute-long level has no minute warning, as it would fall on the change before it`() {
        val oneMinute = ClockTimeline.build(
            listOf(BlindLevel(1, 50, 100, 0, 0), BlindLevel(2, 100, 200, 0, 1)),
            roundLengthMinutes = 1,
        )
        val cues = everySecond(oneMinute, 0L, oneMinute.endSeconds * 1_000L)

        assertTrue(cues.none { it.kind == ClockCueKind.ONE_MINUTE })
        assertEquals(oneMinute.segments.size, cues.count { it.kind == ClockCueKind.LEVEL_CHANGE })
        assertEquals(oneMinute.segments.size, cues.count { it.kind == ClockCueKind.CHIME })
    }

    @Test
    fun `the next cue is the earliest one still ahead`() {
        val timeline = timeline()
        val end = timeline.endSeconds * 1_000L

        assertEquals(round - 60_000, ClockCueTimes.nextAfter(timeline, 0L))
        assertEquals(round - 4_000, ClockCueTimes.nextAfter(timeline, round - 60_000))
        assertEquals(round, ClockCueTimes.nextAfter(timeline, round - 4_000))
        assertEquals(2 * round - 60_000, ClockCueTimes.nextAfter(timeline, round))
        assertEquals(end, ClockCueTimes.nextAfter(timeline, end - 1))
        assertNull(ClockCueTimes.nextAfter(timeline, end))
    }

    @Test
    fun `the chime keeps its old timing exactly`() {
        // Before PP-083 the clock chimed when a look passed (change - 4 s), unless the look was more
        // than 2 s after the change itself. Every change of a timeline with breaks, overtime included.
        val timeline = timeline(BreakSettings(everyLevels = 3, lengthMinutes = 5))
        val end = timeline.endSeconds * 1_000L

        val bySecond = everySecond(timeline, 0L, end).filter { it.kind == ClockCueKind.CHIME }.map { it.atMillis }
        assertEquals(timeline.segments.map { it.endSeconds * 1_000L - 4_000 }, bySecond)
        // One look across the whole game comes too late for all of them but the last
        val oneLook = ClockCueTimes.crossed(timeline, -1L, end).filter { it.kind == ClockCueKind.CHIME }
        assertEquals(listOf(end - 4_000), oneLook.map { it.atMillis })
    }
}
