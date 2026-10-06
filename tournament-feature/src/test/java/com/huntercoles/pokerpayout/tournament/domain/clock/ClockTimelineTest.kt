package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClockTimelineTest {

    /** Default tournament: 3 h of 20-minute rounds, 50 -> 5,000: 50, 100, 150, 300, 500, 800, 1,500, 3,000, 5,000. */
    private val levels = BlindStructureCalculator.generateSchedule(BlindStructureInput(10, 180, 50, 5_000, 20))

    private fun timeline(breaks: BreakSettings = BreakSettings()) =
        ClockTimeline.build(levels, roundLengthMinutes = 20, breaks = breaks, smallestChip = 50)

    @Test
    fun `without breaks levels run back to back, then three overtime levels`() {
        val timeline = timeline()

        assertEquals(12, timeline.segments.size)
        assertEquals((0 until 12).map { it * 1_200 }, timeline.segments.map { it.startSeconds })
        assertEquals(10_800, timeline.regularEndSeconds)
        assertEquals(14_400, timeline.endSeconds)
        val overtime = timeline.levels.filter { it.isOvertime }
        assertEquals(listOf(10_000, 20_000, 40_000), overtime.map { it.level.smallBlind })
    }

    @Test
    fun `breaks follow every Nth level but never the last regular level`() {
        val timeline = timeline(BreakSettings(everyLevels = 4, lengthMinutes = 10, message = " Last rebuy "))

        val breaks = timeline.segments.filterIsInstance<BreakSegment>()
        assertEquals(listOf(4, 8), breaks.map { it.afterLevel })
        assertEquals(listOf(4_800, 10_200), breaks.map { it.startSeconds })
        assertTrue(breaks.all { it.durationSeconds == 600 && it.message == "Last rebuy" })
        // Level 5 starts after 4 levels and a break; the schedule ends 20 minutes later than without breaks
        assertEquals(5_400, timeline.levels[4].startSeconds)
        assertEquals(12_000, timeline.regularEndSeconds)

        // Every 9 levels: no break at all (it would come after the final level)
        assertTrue(timeline(BreakSettings(everyLevels = 9)).segments.none { it is BreakSegment })
    }

    @Test
    fun `looking up the clock crosses into a break and out of it on the exact second`() {
        val timeline = timeline(BreakSettings(everyLevels = 4))

        assertEquals(4, (timeline.segmentAt(4_799) as LevelSegment).level.level)
        val onBreak = assertIs<BreakSegment>(timeline.segmentAt(4_800))
        assertEquals(1, onBreak.number)
        assertIs<BreakSegment>(timeline.segmentAt(5_399))
        assertEquals(5, (timeline.segmentAt(5_400) as LevelSegment).level.level)
        assertEquals(5, timeline.nextLevelAfter(timeline.segmentIndexAt(4_800))?.level?.level)
        // Past the end the last segment stays current
        assertEquals(timeline.segments.last(), timeline.segmentAt(1_000_000))
    }

    @Test
    fun `overtime levels are revealed as the clock reaches them`() {
        val timeline = timeline()

        assertEquals(0, timeline.overtimeLevelsRevealedAt(10_799))
        assertEquals(1, timeline.overtimeLevelsRevealedAt(10_800))
        assertEquals(3, timeline.overtimeLevelsRevealedAt(14_400))
        assertEquals(9, timeline.visibleSegmentsAt(0).size)
        assertEquals(10, timeline.visibleSegmentsAt(10_800).size)
    }

    @Test
    fun `color-ups land on the level the chip drops out at, or the first break after it`() {
        val noBreaks = timeline()
        assertEquals(
            mapOf(4 to listOf(50), 7 to listOf(100), 8 to listOf(500), 9 to listOf(1_000)),
            noBreaks.levels.filter { it.colorUp.isNotEmpty() }.associate { it.level.level to it.colorUp }
        )

        // With breaks after levels 4 and 8: the 50s go at the first break (level 4 on doesn't need
        // them); 100s, 500s and 1,000s at the second, since level 9 on needs none of them.
        val withBreaks = timeline(BreakSettings(everyLevels = 4))
        val breaks = withBreaks.segments.filterIsInstance<BreakSegment>()
        assertEquals(listOf(listOf(50), listOf(100, 500, 1_000)), breaks.map { it.colorUp })
        assertTrue(withBreaks.levels.all { it.colorUp.isEmpty() })
    }

    @Test
    fun `no levels, no timeline`() {
        assertTrue(ClockTimeline.build(emptyList(), 20).isEmpty)
        assertEquals(-1, ClockTimeline.EMPTY.segmentIndexAt(0))
    }
}
