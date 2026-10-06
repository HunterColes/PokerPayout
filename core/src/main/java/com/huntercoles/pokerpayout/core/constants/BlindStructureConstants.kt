package com.huntercoles.pokerpayout.core.constants

/**
 * The limits every blind ladder keeps: how much a level may grow over the one before, and above
 * which value blinds must be round.
 */
object BlindStructureConstants {

    /**
     * Minimum growth rate between consecutive blind levels.
     * Blinds should increase by at least 30% (1.3x) from one level to the next.
     */
    const val MIN_BLIND_GROWTH_RATE = 1.3

    /**
     * Maximum growth rate between consecutive blind levels.
     * Blinds should not increase by more than 100% (2.0x) from one level to the next.
     */
    const val MAX_BLIND_GROWTH_RATE = 2.0

    /**
     * Threshold above which blind values must be "smooth" (end in 0).
     * Values <= this threshold can have any ending digit (e.g., 15, 25).
     */
    const val SMOOTH_NUMBER_THRESHOLD = 25
}
