package com.huntercoles.pokerpayout.core.audio

/**
 * Told when a clock cue starts and stops sounding ([SoundManager]), so the music dips under it and
 * comes back after. Called on the main thread.
 */
interface CueDucking {
    fun cueStarted()

    fun cueEnded()

    companion object {
        /** No music to dip, as in tests of the cues alone. */
        val NONE: CueDucking = object : CueDucking {
            override fun cueStarted() = Unit

            override fun cueEnded() = Unit
        }
    }
}
