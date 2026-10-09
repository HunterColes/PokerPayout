package com.huntercoles.pokerpayout.core.audio.music

import com.huntercoles.pokerpayout.core.audio.music.ClockPhase.BREAK
import com.huntercoles.pokerpayout.core.audio.music.ClockPhase.FINISHED
import com.huntercoles.pokerpayout.core.audio.music.ClockPhase.IDLE
import com.huntercoles.pokerpayout.core.audio.music.ClockPhase.LEVEL
import com.huntercoles.pokerpayout.core.audio.music.ClockPhase.PAUSED
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * "Play with the clock" against the clock's states: the music starts when the clock runs, pauses
 * when it is paused or over, does on breaks what the host chose, and acts only when the clock
 * changes, so the host's own Play and Pause stand in between. It never pauses music it didn't
 * start, and with the link off it does nothing at all.
 */
class MusicAutoPlayTest {

    private val play = MusicStep(play = true, quiet = false)
    private val pause = MusicStep(play = false, quiet = false)
    private val leave = MusicStep(play = null, quiet = false)
    private val quiet = MusicStep(play = null, quiet = true)

    /** What auto-play asks at each phase in turn, from a fresh start. */
    private fun steps(vararg phases: ClockPhase, breaks: BreakMusic = BreakMusic.KEEP, on: Boolean = true): List<MusicStep> {
        val autoPlay = MusicAutoPlay()
        return phases.map { autoPlay.update(it, on, breaks) }
    }

    @Test
    fun `the music starts with the clock and pauses with it`() {
        assertEquals(
            listOf(leave, leave, play, pause, play),
            steps(IDLE, PAUSED, LEVEL, PAUSED, LEVEL), // app open, started (saved before it runs), running, paused, resumed
        )
    }

    @Test
    fun `a new level changes nothing, so a host's pause in level 3 stands at level 4`() {
        assertEquals(listOf(play, leave, leave), steps(LEVEL, LEVEL, LEVEL))
    }

    @Test
    fun `on a break the music keeps playing, pauses, or plays quieter, as chosen`() {
        assertEquals(listOf(play, leave, leave), steps(LEVEL, BREAK, LEVEL, breaks = BreakMusic.KEEP))
        assertEquals(listOf(play, pause, play), steps(LEVEL, BREAK, LEVEL, breaks = BreakMusic.PAUSE))
        assertEquals(listOf(play, quiet, leave), steps(LEVEL, BREAK, LEVEL, breaks = BreakMusic.QUIET))
    }

    @Test
    fun `quieter lasts the break, through a pause in it`() {
        val resumedQuiet = MusicStep(play = true, quiet = true)
        assertEquals(
            listOf(play, quiet, quiet, pause, resumedQuiet, leave),
            steps(LEVEL, BREAK, BREAK, PAUSED, BREAK, LEVEL, breaks = BreakMusic.QUIET),
        )
    }

    @Test
    fun `a game that starts on a break waits for the first level when breaks pause`() {
        assertEquals(listOf(leave, play), steps(BREAK, LEVEL, breaks = BreakMusic.PAUSE))
    }

    @Test
    fun `the end of the game or a reset pauses the music it started`() {
        assertEquals(listOf(play, pause), steps(LEVEL, FINISHED))
        assertEquals(listOf(play, pause), steps(LEVEL, IDLE))
        assertEquals(listOf(play, pause, leave), steps(LEVEL, PAUSED, IDLE))
    }

    @Test
    fun `turned on while the clock is paused, it leaves the host's music alone`() {
        val autoPlay = MusicAutoPlay()
        assertEquals(leave, autoPlay.update(PAUSED, autoPlay = false, BreakMusic.KEEP))
        assertEquals(leave, autoPlay.update(PAUSED, autoPlay = true, BreakMusic.KEEP))
        assertEquals(play, autoPlay.update(LEVEL, autoPlay = true, BreakMusic.KEEP))
    }

    @Test
    fun `turned on while the clock runs, the music starts`() {
        val autoPlay = MusicAutoPlay()
        assertEquals(leave, autoPlay.update(LEVEL, autoPlay = false, BreakMusic.KEEP))
        assertEquals(play, autoPlay.update(LEVEL, autoPlay = true, BreakMusic.KEEP))
    }

    @Test
    fun `turned off, it does nothing more, and a quiet break is over`() {
        val autoPlay = MusicAutoPlay()
        assertEquals(play, autoPlay.update(LEVEL, autoPlay = true, BreakMusic.QUIET))
        assertEquals(quiet, autoPlay.update(BREAK, autoPlay = true, BreakMusic.QUIET))
        assertEquals(leave, autoPlay.update(BREAK, autoPlay = false, BreakMusic.QUIET))
        assertEquals(leave, autoPlay.update(PAUSED, autoPlay = false, BreakMusic.QUIET))
        assertEquals(leave, autoPlay.update(FINISHED, autoPlay = false, BreakMusic.QUIET))
    }

    @Test
    fun `off from the start, the clock never touches the music`() {
        assertEquals(
            List(6) { leave },
            steps(IDLE, LEVEL, BREAK, PAUSED, LEVEL, FINISHED, breaks = BreakMusic.PAUSE, on = false),
        )
    }

    @Test
    fun `changing what breaks do, mid-break, takes effect at once`() {
        val autoPlay = MusicAutoPlay()
        assertEquals(play, autoPlay.update(LEVEL, true, BreakMusic.KEEP))
        assertEquals(leave, autoPlay.update(BREAK, true, BreakMusic.KEEP))
        assertEquals(pause, autoPlay.update(BREAK, true, BreakMusic.PAUSE))
        assertEquals(play, autoPlay.update(BREAK, true, BreakMusic.KEEP))
        assertEquals(quiet, autoPlay.update(BREAK, true, BreakMusic.QUIET))
    }
}
