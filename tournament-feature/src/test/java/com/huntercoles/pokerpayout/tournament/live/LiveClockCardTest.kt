package com.huntercoles.pokerpayout.tournament.live

import com.huntercoles.pokerpayout.core.time.ClockAnchor
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.core.utils.BlindStructureCalculator
import com.huntercoles.pokerpayout.core.utils.BlindStructureInput
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockTimeline
import com.huntercoles.pokerpayout.tournament.domain.clock.SavedClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the live clock notification says (PP-081), from the saved clock alone: the level or break, its
 * blinds and what's next, and when it ends (running) or what's left (paused). The card must stay the
 * same from one look to the next while nothing changes, or the notification would be posted again.
 */
class LiveClockCardTest {

    /** 3 h of 20-minute rounds, 50 to 5,000 (9 levels), a 10-minute break after levels 4 and 8, an ante from 5. */
    private val levels = BlindStructureCalculator.generateSchedule(BlindStructureInput(10, 180, 50, 5_000, 20, 5))
    private val timeline = ClockTimeline.build(
        levels,
        roundLengthMinutes = 20,
        breaks = BreakSettings(everyLevels = 4, lengthMinutes = 10, message = "Last rebuy"),
        smallestChip = 50,
        bigBlindAnteFromLevel = 5,
    )

    private class Time(var realtime: Long) : TimeSource {
        override fun elapsedRealtimeMillis() = realtime
        override fun wallClockMillis() = 1_760_000_000_000L + realtime
        override fun bootCount() = 4
    }

    private val time = Time(realtime = 9_000_000L)

    private fun running(elapsedMillis: Long) =
        SavedClock.State(ClockAnchor.runningFrom(elapsedMillis, time), timeline, finished = false)

    private fun paused(elapsedMillis: Long) = SavedClock.State(ClockAnchor.stopped(elapsedMillis), timeline, finished = false)

    private fun SavedClock.State.card(): LiveClockCard? =
        LiveClockCard.of(this, anchor.elapsedAt(time), time.realtime)

    @Test
    fun `a running level shows its blinds, the next ones and when it ends`() {
        val card = running(elapsedMillis = 7 * 60_000L + 19_000).card()!! // level 1, 12:41 left

        val showing = assertIs<LiveClockCard.Showing.Level>(card.showing)
        assertEquals(1, showing.blinds.level)
        assertEquals(50, showing.blinds.smallBlind)
        assertEquals(100, (card.next as LiveClockCard.Next.Level).blinds.smallBlind)
        assertTrue(card.running)
        assertEquals(9_000_000L + (12 * 60 + 41) * 1_000L, card.endsAtRealtime)
        assertNull(card.pausedLeftSeconds)
    }

    @Test
    fun `a running card stays the same from look to look until the level changes`() {
        val clock = running(elapsedMillis = 0L)
        val first = clock.card()

        time.realtime += 1_199_000 // 19:59 later, still level 1
        assertEquals(first, clock.card())
        time.realtime += 1_000 // level 2
        val second = clock.card()!!
        assertNotEquals(first, second)
        assertEquals(2, (second.showing as LiveClockCard.Showing.Level).blinds.level)
    }

    @Test
    fun `a paused card says what's left, the way the clock does`() {
        val card = paused(elapsedMillis = 7 * 60_000L + 19_400).card()!!

        assertNull(card.endsAtRealtime)
        assertEquals(12 * 60 + 41, card.pausedLeftSeconds)
        assertTrue(!card.running)
        // Time passing changes nothing while paused
        time.realtime += 3_600_000
        assertEquals(card, paused(elapsedMillis = 7 * 60_000L + 19_400).card())
    }

    @Test
    fun `the last level before a break says the break is next, and the break says where play resumes`() {
        val levelFour = timeline.levels[3]
        val beforeBreak = running(levelFour.startSeconds * 1_000L).card()!!
        assertEquals(LiveClockCard.Next.Break(minutes = 10), beforeBreak.next)

        val onBreak = running(levelFour.endSeconds * 1_000L + 5_000).card()!!
        val showing = assertIs<LiveClockCard.Showing.Break>(onBreak.showing)
        assertEquals(1, showing.number)
        assertEquals("Last rebuy", showing.note)
        val back = assertIs<LiveClockCard.Next.Level>(onBreak.next)
        assertEquals(5, back.blinds.level)
        assertEquals(back.blinds.bigBlind, back.blinds.ante) // the big-blind ante from level 5
    }

    @Test
    fun `overtime levels say so, and the last one has nothing next`() {
        val firstOvertime = timeline.levels.first { it.isOvertime }
        val overtime = running(firstOvertime.startSeconds * 1_000L).card()!!
        assertEquals(true, (overtime.showing as LiveClockCard.Showing.Level).overtime)

        val last = running(timeline.levels.last().startSeconds * 1_000L).card()!!
        assertEquals(LiveClockCard.Next.None, last.next)
    }

    @Test
    fun `an empty timeline has no card`() {
        val empty = SavedClock.State(ClockAnchor.stopped(0L), ClockTimeline.EMPTY, finished = false)
        assertNull(LiveClockCard.of(empty, 0L, time.realtime))
    }
}
