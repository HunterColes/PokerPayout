package com.huntercoles.pokerpayout.tournament.domain.clock

import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When the clock's cues fall (PP-083): the cue sound 4 s before each change (as it always was), the level
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
    fun `each level has its minute warning, its cue sound and its change, in that order`() {
        assertEquals(
            listOf(
                ClockCue(ClockCueKind.ONE_MINUTE, round - 60_000),
                ClockCue(ClockCueKind.SOUND, round - 4_000),
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
            listOf(ClockCueKind.SOUND, ClockCueKind.LEVEL_CHANGE),
            ClockCueTimes.crossed(timeline, change - 5_000, change + ClockCueTimes.GRACE_MILLIS).map { it.kind },
        )
        assertTrue(ClockCueTimes.crossed(timeline, change - 5_000, change + ClockCueTimes.GRACE_MILLIS + 1).isEmpty())
        // The minute warning has its own two seconds: 3 s late is too late, even though the cue sound isn't due yet
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
                ClockCue(ClockCueKind.SOUND, breakEnd - 4_000, CueEvent.BREAK_END),
                ClockCue(ClockCueKind.LEVEL_CHANGE, breakEnd, CueEvent.BREAK_END),
            ),
            everySecond(withBreaks, breakOne.startSeconds * 1_000L, breakEnd),
        )
    }

    @Test
    fun `each change says what it is, for the sound pack`() {
        // Breaks after levels 4 and 8; 12 levels, the last three overtime
        val withBreaks = timeline(BreakSettings(everyLevels = 4, lengthMinutes = 10))
        val changes = everySecond(withBreaks, 0L, withBreaks.endSeconds * 1_000L)
            .filter { it.kind == ClockCueKind.SOUND }
            .map { it.event }
        val level = CueEvent.LEVEL_UP
        assertEquals(
            listOf(level, level, level, CueEvent.BREAK_START, CueEvent.BREAK_END) +
                listOf(level, level, level, CueEvent.BREAK_START, CueEvent.BREAK_END) +
                List(3) { level } + CueEvent.GAME_OVER,
            changes,
        )
        // The change itself names the same event as its cue sound; the minute warning is its own
        val all = everySecond(withBreaks, 0L, withBreaks.endSeconds * 1_000L)
        assertEquals(changes, all.filter { it.kind == ClockCueKind.LEVEL_CHANGE }.map { it.event })
        assertTrue(all.filter { it.kind == ClockCueKind.ONE_MINUTE }.all { it.event == CueEvent.ONE_MINUTE })
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
        assertEquals(oneMinute.segments.size, cues.count { it.kind == ClockCueKind.SOUND })
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
    fun `the cue sound keeps its old timing exactly`() {
        // Before PP-083 the clock played its cue sound when a look passed (change - 4 s), unless the look was more
        // than 2 s after the change itself. Every change of a timeline with breaks, overtime included.
        val timeline = timeline(BreakSettings(everyLevels = 3, lengthMinutes = 5))
        val end = timeline.endSeconds * 1_000L

        val bySecond = everySecond(timeline, 0L, end).filter { it.kind == ClockCueKind.SOUND }.map { it.atMillis }
        assertEquals(timeline.segments.map { it.endSeconds * 1_000L - 4_000 }, bySecond)
        // One look across the whole game comes too late for all of them but the last
        val oneLook = ClockCueTimes.crossed(timeline, -1L, end).filter { it.kind == ClockCueKind.SOUND }
        assertEquals(listOf(end - 4_000), oneLook.map { it.atMillis })
    }

    // #19: the cue sound's lead (a custom sound's peak), and the seek when a segment is shorter than it.

    private fun crossed(timeline: ClockTimeline, fromMillis: Long, toMillis: Long, leadMillis: Long) =
        ClockCueTimes.crossed(timeline, fromMillis, toMillis, leadMillis)

    private fun nextAfter(timeline: ClockTimeline, elapsedMillis: Long, leadMillis: Long) =
        ClockCueTimes.nextAfter(timeline, elapsedMillis, leadMillis)

    private fun soundOffset(timeline: ClockTimeline, cue: ClockCue, nowMillis: Long, leadMillis: Long) =
        ClockCueTimes.soundOffsetMillis(timeline, cue, nowMillis, leadMillis)

    private fun everySecond(timeline: ClockTimeline, fromMillis: Long, toMillis: Long, leadMillis: Long) =
        (fromMillis / 1_000 + 1..toMillis / 1_000).flatMap { second ->
            crossed(timeline, second * 1_000 - 1_000, second * 1_000, leadMillis)
        }

    /** Minute-long levels: two regular ones and the overtime ones after them. */
    private val minuteLevels = ClockTimeline.build(
        listOf(BlindLevel(1, 50, 100, 0, 0), BlindLevel(2, 100, 200, 0, 1)),
        roundLengthMinutes = 1,
    )

    @Test
    fun `A1 lead 8000 on a 60 s level - the sound at 52 000, and a late look seeks so the peak lands on the change`() {
        val lead = 8_000L
        val sounds = everySecond(minuteLevels, 0L, 60_000L, lead).filter { it.kind == ClockCueKind.SOUND }
        assertEquals(listOf(ClockCue(ClockCueKind.SOUND, 52_000L)), sounds)
        assertEquals(52_000L, nextAfter(minuteLevels, 51_000L, lead))

        val cue = sounds.single()
        assertEquals(0L, soundOffset(minuteLevels, cue, 52_000L, lead))
        assertEquals(1_500L, soundOffset(minuteLevels, cue, 53_500L, lead))
        // One late look at 53 500 starts the file 1 500 ms in: its peak (8 000 ms in) plays at 60 000
        val late = crossed(minuteLevels, 51_000L, 53_500L, lead).single { it.kind == ClockCueKind.SOUND }
        val peakAt = 53_500L + (lead - soundOffset(minuteLevels, late, 53_500L, lead))
        assertTrue(abs(peakAt - 60_000L) <= 100L, "peak at $peakAt")
    }

    @Test
    fun `A2 a segment shorter than the lead - its sound at the segment start, offset by the shortfall`() {
        // Segments are whole minutes, so the card's 5 s segment with lead 8 000 is a 60 s one with lead 63 000
        val lead = 63_000L
        val start = minuteLevels.segments[1].startSeconds * 1_000L

        val sounds = crossed(minuteLevels, start - 1_000L, start, lead).filter { it.kind == ClockCueKind.SOUND }
        assertEquals(listOf(ClockCue(ClockCueKind.SOUND, start, CueEvent.LEVEL_UP)), sounds)
        assertEquals(3_000L, soundOffset(minuteLevels, sounds.single(), start, lead))
        assertEquals(start, nextAfter(minuteLevels, start - 1_000L, lead))
    }

    @Test
    fun `A2 one look crossing two cue sounds - the earlier change's sound comes first, at its peak now`() {
        // Lead 63 000 on minute levels: the first level's sound at 0, the second's clamped to its start, 60 000.
        // One look from before 0 to 60 000 passes both; CueActions plays the first SOUND: the first level's.
        val lead = 63_000L
        val sounds = crossed(minuteLevels, -1L, 60_000L, lead).filter { it.kind == ClockCueKind.SOUND }
        assertEquals(listOf(0L, 60_000L), sounds.map { it.atMillis })
        // The earlier change is happening now: its sound starts at its peak
        assertEquals(lead, soundOffset(minuteLevels, sounds.first(), 60_000L, lead))
        assertEquals(3_000L, soundOffset(minuteLevels, sounds.last(), 60_000L, lead))
    }

    @Test
    fun `A3 paused before the sound and resumed after its time - crossed once with its offset, never again`() {
        val lead = 8_000L
        val timeline = timeline()
        val soundAt = round - lead

        // Looks once a second up to the pause, 500 ms before the sound
        val beforePause = everySecond(timeline, 0L, soundAt - 1_000L, lead) +
            crossed(timeline, soundAt - 1_000L, soundAt - 500L, lead)
        assertTrue(beforePause.none { it.kind == ClockCueKind.SOUND })
        // Paused: standing still passes nothing
        assertTrue(crossed(timeline, soundAt - 500L, soundAt - 500L, lead).isEmpty())
        // Resumed: the first look lands 700 ms past the sound's time
        val resumed = crossed(timeline, soundAt - 500L, soundAt + 700L, lead).filter { it.kind == ClockCueKind.SOUND }
        assertEquals(listOf(ClockCue(ClockCueKind.SOUND, soundAt)), resumed)
        assertEquals(700L, soundOffset(timeline, resumed.single(), soundAt + 700L, lead))
        // The looks after it, a pause inside the window and the resume never pass it again
        val after = crossed(timeline, soundAt + 700L, soundAt + 2_000L, lead) +
            crossed(timeline, soundAt + 2_000L, soundAt + 2_000L, lead) +
            everySecond(timeline, soundAt + 2_000L, round, lead)
        assertTrue(after.none { it.kind == ClockCueKind.SOUND })
    }

    @Test
    fun `A4 lead 0 - the sound falls on the change itself`() {
        val timeline = timeline()
        val atChange = crossed(timeline, round - 1_000L, round, 0L)
        assertEquals(
            listOf(ClockCue(ClockCueKind.SOUND, round), ClockCue(ClockCueKind.LEVEL_CHANGE, round)),
            atChange,
        )
        assertEquals(0L, soundOffset(timeline, atChange.first(), round, 0L))
        assertTrue(crossed(timeline, round - 5_000L, round - 1_000L, 0L).none { it.kind == ClockCueKind.SOUND })
    }

    @Test
    fun `A4 the default lead is the old 4 s - the same cues as leadMillis 4 000`() {
        val timeline = timeline(BreakSettings(everyLevels = 3, lengthMinutes = 5))
        val end = timeline.endSeconds * 1_000L
        assertEquals(everySecond(timeline, 0L, end), everySecond(timeline, 0L, end, 4_000L))
        (0L..end step 30_000L).forEach {
            assertEquals(ClockCueTimes.nextAfter(timeline, it), nextAfter(timeline, it, 4_000L))
        }
    }

    @Test
    fun `H1 a lead longer than the whole timeline - every sound at its segment start, no negative offset`() {
        val end = minuteLevels.endSeconds * 1_000L
        val lead = end + 10_000L
        val sounds = everySecond(minuteLevels, -1_000L, end, lead).filter { it.kind == ClockCueKind.SOUND }
        assertEquals(minuteLevels.segments.map { it.startSeconds * 1_000L }, sounds.map { it.atMillis })
        assertEquals(0L, nextAfter(minuteLevels, -1L, lead))
        minuteLevels.segments.zip(sounds).forEach { (segment, cue) ->
            val start = segment.startSeconds * 1_000L
            assertEquals(start - (segment.endSeconds * 1_000L - lead), soundOffset(minuteLevels, cue, start, lead))
        }
        // A look before a cue's own time never asks for a negative seek
        val last = sounds.last()
        assertTrue(soundOffset(minuteLevels, last, last.atMillis - 30_000L, lead) >= 0L)
    }

    @Test
    fun `H2 a lead at the last segment - the game over sound leads the end, no negative offset`() {
        val lead = 8_000L
        val timeline = timeline()
        val end = timeline.endSeconds * 1_000L
        val sound = crossed(timeline, end - lead - 1_000L, end - lead, lead).single()
        assertEquals(ClockCue(ClockCueKind.SOUND, end - lead, CueEvent.GAME_OVER), sound)
        assertEquals(end - lead, nextAfter(timeline, end - lead - 1L, lead))
        assertEquals(500L, soundOffset(timeline, sound, end - lead + 500L, lead))
        assertEquals(0L, soundOffset(timeline, sound, end - lead - 2_000L, lead))
        assertNull(nextAfter(timeline, end, lead))
    }
}
