package com.huntercoles.pokerpayout.core.domain.model

import kotlinx.coroutines.flow.Flow

/**
 * Where the tournament clock stands, as other tabs need it (the Bank's rebuy and add-on cutoffs).
 * Read-only: the clock itself lives in the Tournament tab.
 */
data class ClockStatus(
    /** False until the clock is first started (and again after a reset). */
    val started: Boolean = false,
    /** The level being played, 1-based; during a break, the level just played. */
    val level: Int = 1,
    val onBreak: Boolean = false,
    /** The level each break follows, in order: break 1 comes after `breakAfterLevels[0]`. */
    val breakAfterLevels: List<Int> = emptyList(),
    /** The clock ran past its last level. */
    val finished: Boolean = false
) {
    companion object {
        val NOT_STARTED = ClockStatus()
    }
}

/**
 * The clock as seen from outside the Tournament tab. `tournament-feature` implements it from the
 * saved clock, so the Bank doesn't depend on that module.
 */
interface ClockStatusProvider {
    /** The current status, emitted again whenever the level, break or start state changes. */
    val status: Flow<ClockStatus>
}
