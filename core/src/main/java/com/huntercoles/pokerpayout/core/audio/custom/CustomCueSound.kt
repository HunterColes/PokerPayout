package com.huntercoles.pokerpayout.core.audio.custom

/**
 * A sound file the host picked for the clock's change cues (level up, break start and end, game over).
 *
 * @property uri the file, as the system's file picker lent it (a content URI).
 * @property lengthMillis how long it is, measured when it was picked.
 * @property peakMillis where in the file the change should land: the cue starts this long before it.
 * @property endMillis where it stops, with a short fade; 0 plays it to the end.
 */
data class CustomCueSound(
    val uri: String,
    val lengthMillis: Long,
    val peakMillis: Long = 0,
    val endMillis: Long = 0,
) {
    /** The same sound with its peak in 0..length and its end in peak..length (0 stays "to the end"). */
    fun clamped(): CustomCueSound {
        val length = lengthMillis.coerceAtLeast(0)
        val peak = peakMillis.coerceIn(0, length)
        val end = if (endMillis <= 0) 0 else endMillis.coerceIn(peak, length)
        return copy(lengthMillis = length, peakMillis = peak, endMillis = end)
    }
}
