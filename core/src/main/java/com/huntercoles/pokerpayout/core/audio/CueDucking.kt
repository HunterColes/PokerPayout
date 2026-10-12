package com.huntercoles.pokerpayout.core.audio

/**
 * Told when a clock cue starts and stops sounding ([SoundManager]), so the music dips under it and
 * comes back after. Called on the main thread.
 */
interface CueDucking {
    fun cueStarted()

    fun cueEnded()
}
