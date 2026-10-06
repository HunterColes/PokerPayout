package com.huntercoles.pokerpayout.core.domain.model

/**
 * Whether rebuys (or add-ons) can still be bought, and until when (PP-030).
 *
 * The cutoff is "rebuys until level N" (`TournamentPreferences.rebuyUntilLevel`, 0 for none):
 * - **Rebuys** are open through the end of level N and close when it ends, which is when the break
 *   after it starts, if there is one.
 * - **Add-ons** stay open until the end of the first break after the cutoff: the break that follows
 *   level N or a later level, where hosts sell them. With no such break they close with the rebuys.
 *
 * Before the clock starts everything is open; a finished clock closes both.
 */
sealed interface PurchaseWindow {
    val isOpen: Boolean

    /** No cutoff is set: open all night. */
    data object NoCutoff : PurchaseWindow {
        override val isOpen = true
    }

    /** Open through the end of [level]. */
    data class OpenUntilLevel(val level: Int) : PurchaseWindow {
        override val isOpen = true
    }

    /** Open until break [number] ends; it follows level [afterLevel]. */
    data class OpenUntilBreak(val number: Int, val afterLevel: Int) : PurchaseWindow {
        override val isOpen = true
    }

    /** Closed when [level] ended. */
    data class ClosedAfterLevel(val level: Int) : PurchaseWindow {
        override val isOpen = false
    }

    /** Closed when break [number] ended. */
    data class ClosedAfterBreak(val number: Int) : PurchaseWindow {
        override val isOpen = false
    }

    companion object {
        /** The rebuy window for a cutoff of [cutoffLevel] (0 = none) with the clock at [clock]. */
        fun rebuys(cutoffLevel: Int, clock: ClockStatus): PurchaseWindow {
            if (cutoffLevel <= 0) return NoCutoff
            val ended = clock.started &&
                (clock.finished || clock.level > cutoffLevel || (clock.level == cutoffLevel && clock.onBreak))
            return if (ended) ClosedAfterLevel(cutoffLevel) else OpenUntilLevel(cutoffLevel)
        }

        /** The add-on window: rebuys' cutoff, stretched to the end of the first break after it. */
        fun addOns(cutoffLevel: Int, clock: ClockStatus): PurchaseWindow {
            val breakIndex = clock.breakAfterLevels.indexOfFirst { it >= cutoffLevel }
            if (cutoffLevel <= 0 || breakIndex < 0) return rebuys(cutoffLevel, clock)
            val afterLevel = clock.breakAfterLevels[breakIndex]
            val number = breakIndex + 1
            // The break after level B is played while the clock still shows level B.
            val ended = clock.started && (clock.finished || clock.level > afterLevel)
            return if (ended) ClosedAfterBreak(number) else OpenUntilBreak(number, afterLevel)
        }
    }
}
