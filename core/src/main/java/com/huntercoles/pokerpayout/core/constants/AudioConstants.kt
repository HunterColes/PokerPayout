package com.huntercoles.pokerpayout.core.constants

/**
 * Constants related to audio playback and timing.
 */
object AudioConstants {
    /**
     * Lead time in seconds before a blind level change to play the sound effect.
     * This value is chosen so the sound's midpoint aligns with the actual level change,
     * creating a more immersive transition experience.
     */
    const val LEVEL_CHANGE_SOUND_LEAD_SECONDS = 4
}
