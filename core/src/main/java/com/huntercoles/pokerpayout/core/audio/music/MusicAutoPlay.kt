package com.huntercoles.pokerpayout.core.audio.music

/** Where the tournament clock is, as far as the music cares. */
enum class ClockPhase {
    /** Not started, or reset. */
    IDLE,

    /** Running, in a blind level. */
    LEVEL,

    /** Running, in a break. */
    BREAK,

    /** Started and paused. */
    PAUSED,

    /** The last level is over. */
    FINISHED,
}

/** What the music does on a break while it plays with the clock. Saved by name: never rename one. */
enum class BreakMusic {
    /** Keeps playing as in a level. */
    KEEP,

    /** Pauses for the break and starts again with the next level. */
    PAUSE,

    /** Plays on, quieter, so the table can talk. */
    QUIET,
}

/** What the clock asks of the music at one look: play or pause it ([play], null to leave it), and whether it is [quiet]er. */
data class MusicStep(val play: Boolean?, val quiet: Boolean)

/**
 * "Play with the clock": the music starts when the clock runs and pauses when it is paused, and on a
 * break does what [BreakMusic] says.
 *
 * It acts on changes, not on every look: the clock moving from one state to another plays or
 * pauses the music once, and in between the host's own Play and Pause stand (a host who pauses the
 * music in level 3 isn't overruled at level 4). It only pauses music it would itself have played,
 * so turning the link on, or opening the app, while the clock is paused never stops music the host
 * put on. Quieter is a state, not a change: on for as long as a break lasts.
 */
class MusicAutoPlay {
    /** Whether the clock wanted music at the last look; null while the link is off. */
    private var wanted: Boolean? = null

    fun update(phase: ClockPhase, autoPlay: Boolean, breakMusic: BreakMusic): MusicStep {
        val want = if (autoPlay) wants(phase, breakMusic) else null
        val play = when {
            want == null || want == wanted -> null
            want -> true
            // Pause only what the clock itself wanted playing
            wanted == true -> false
            else -> null
        }
        wanted = want
        return MusicStep(play = play, quiet = autoPlay && phase == ClockPhase.BREAK && breakMusic == BreakMusic.QUIET)
    }

    private fun wants(phase: ClockPhase, breakMusic: BreakMusic): Boolean = when (phase) {
        ClockPhase.LEVEL -> true
        ClockPhase.BREAK -> breakMusic != BreakMusic.PAUSE
        ClockPhase.IDLE, ClockPhase.PAUSED, ClockPhase.FINISHED -> false
    }
}
