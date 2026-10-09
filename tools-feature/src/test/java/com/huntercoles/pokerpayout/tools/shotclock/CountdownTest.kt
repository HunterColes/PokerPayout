package com.huntercoles.pokerpayout.tools.shotclock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The shot clock's countdown, its warnings and the cards-played codec, as plain functions of time. */
class CountdownTest {

    @Test
    fun `a ready clock waits, full, and doesn't move`() {
        val ready = Countdown.ready(30_000)
        assertEquals(ShotClockPhase.Ready, ready.phaseAt(1_000_000))
        assertEquals(30_000, ready.leftAt(1_000_000))
        assertEquals(ready, ready.resumedAt(5), "nothing to resume before the first decision")
    }

    @Test
    fun `time left is worked out from the monotonic clock, not counted`() {
        val clock = Countdown.startedAt(30_000, now = 1_000)
        assertEquals(30_000, clock.leftAt(1_000))
        assertEquals(17_500, clock.leftAt(13_500))
        assertEquals(ShotClockPhase.Running, clock.phaseAt(13_500))
        assertEquals(0, clock.leftAt(31_000))
        assertEquals(ShotClockPhase.TimeUp, clock.phaseAt(31_000))
        assertEquals(-4_000, clock.signedLeftAt(35_000), "how long ago it ran out")
    }

    @Test
    fun `a pause keeps the time left however long it lasts, and a resume carries on from it`() {
        val paused = Countdown.startedAt(45_000, now = 0).pausedAt(20_000)
        assertEquals(ShotClockPhase.Paused, paused.phaseAt(20_000))
        assertEquals(25_000, paused.leftAt(999_999))
        val resumed = paused.resumedAt(500_000)
        assertEquals(25_000, resumed.leftAt(500_000))
        assertEquals(15_000, resumed.leftAt(510_000))
    }

    @Test
    fun `a time-bank card adds its time, and runs the clock again when time was up`() {
        val running = Countdown.startedAt(30_000, now = 0)
        val extended = running.extendedAt(25_000, 30_000)
        assertEquals(35_000, extended.leftAt(25_000))
        assertEquals(60_000, extended.totalMillis)
        assertEquals(ShotClockPhase.Running, extended.phaseAt(25_000))

        val late = running.extendedAt(40_000, 30_000)
        assertEquals(ShotClockPhase.Running, late.phaseAt(40_000), "out of time, the card starts it again")
        assertEquals(30_000, late.leftAt(40_000), "with just the card's time")

        val paused = running.pausedAt(10_000).extendedAt(50_000, 30_000)
        assertEquals(ShotClockPhase.Paused, paused.phaseAt(60_000), "paused, it stays paused")
        assertEquals(50_000, paused.leftAt(60_000))
    }

    @Test
    fun `the seconds shown round up, so the full time shows at the start and 0 only at the end`() {
        assertEquals(30, ShotClockTiming.shownSeconds(30_000))
        assertEquals(30, ShotClockTiming.shownSeconds(29_001))
        assertEquals(29, ShotClockTiming.shownSeconds(29_000))
        assertEquals(1, ShotClockTiming.shownSeconds(1))
        assertEquals(0, ShotClockTiming.shownSeconds(0))
        assertEquals(0, ShotClockTiming.shownSeconds(-500))
        assertEquals(1_000, ShotClockTiming.untilNextSecond(30_000))
        assertEquals(400, ShotClockTiming.untilNextSecond(29_400))
        assertEquals(1, ShotClockTiming.untilNextSecond(1))
    }

    @Test
    fun `the warnings fall at ten seconds and at zero, once each`() {
        assertNull(ShotClockTiming.crossed(12_000, 11_000))
        assertEquals(ShotClockCue.TenSeconds, ShotClockTiming.crossed(11_000, 10_000))
        assertNull(ShotClockTiming.crossed(10_000, 9_000), "already given")
        assertEquals(ShotClockCue.TimeUp, ShotClockTiming.crossed(1_000, 0))
        assertNull(ShotClockTiming.crossed(0, -1_000), "already given")
    }

    @Test
    fun `one late look that passes both gives only time up, and a look too late gives nothing`() {
        assertEquals(ShotClockCue.TimeUp, ShotClockTiming.crossed(12_000, -500))
        assertEquals(ShotClockCue.TenSeconds, ShotClockTiming.crossed(25_000, 8_500), "1.5 s late is still on time")
        assertNull(ShotClockTiming.crossed(25_000, 4_000), "6 s after the warning's moment: the phone was asleep")
        assertNull(ShotClockTiming.crossed(25_000, -5_000), "5 s after time ran out")
    }

    @Test
    fun `the cards played round-trip by name, and a damaged entry is skipped`() {
        val used = mapOf("Dana" to 1, "Ann, the \"Rock\"" to 2, "Zoë=Z" to 3)
        assertEquals(used, UsedCardsCodec.decode(UsedCardsCodec.encode(used)))
        assertEquals(mapOf("Sam" to 2), UsedCardsCodec.decode("Sam=2,=4,Kim=x,Lee=0,%zz=1,justaname"))
        assertTrue(UsedCardsCodec.decode("").isEmpty())
        assertFalse(UsedCardsCodec.encode(mapOf("a,b" to 1)).contains("a,b"), "commas in names are encoded")
    }
}
