package com.huntercoles.pokerpayout.core.domain.cash

/**
 * One player in a cash game (PP-029): every buy-in in the order it was made, the first being the
 * buy-in and the rest top-ups, and what their chips were worth when they left. All in whole cents.
 *
 * [cashOutCents] is null until their chips are counted; a player who lost everything is counted
 * at 0.
 */
data class CashPlayer(
    val id: Int,
    val name: String,
    val buyInsCents: List<Long>,
    val cashOutCents: Long? = null,
) {
    /** Everything this player put in. */
    val inCents: Long get() = buyInsCents.sum()

    /** Buy-ins after the first. */
    val topUps: Int get() = (buyInsCents.size - 1).coerceAtLeast(0)

    val isCounted: Boolean get() = cashOutCents != null

    /** Up (positive) or down (negative) on the night, before any split; null until counted. */
    val netCents: Long? get() = cashOutCents?.let { it - inCents }
}

/**
 * The chip check: is what the players cashed out what went into the game? The settle-up waits for
 * it, because a miscount would otherwise land on whoever is paid last.
 */
sealed interface ChipCheck {
    /** Nobody has bought in yet. */
    data object Empty : ChipCheck

    /** [uncounted] players still have chips to count. */
    data class Counting(val uncounted: Int) : ChipCheck

    /** Counted out is exactly the cash in. */
    data object Balanced : ChipCheck

    /**
     * Counted out minus cash in: positive when more was counted than went in (the bank is short),
     * negative when less was (chips are missing, or someone left without counting).
     */
    data class Off(val differenceCents: Long) : ChipCheck
}

/** A cash game's ledger: the players in the order they sat down. */
data class CashLedger(val players: List<CashPlayer> = emptyList()) {
    /** Every buy-in and top-up. */
    val cashInCents: Long get() = players.sumOf { it.inCents }

    /** The chips counted so far, at their cash value. */
    val countedOutCents: Long get() = players.sumOf { it.cashOutCents ?: 0L }

    val uncounted: Int get() = players.count { !it.isCounted }

    val chipCheck: ChipCheck
        get() = when {
            players.isEmpty() -> ChipCheck.Empty
            uncounted > 0 -> ChipCheck.Counting(uncounted)
            countedOutCents == cashInCents -> ChipCheck.Balanced
            else -> ChipCheck.Off(countedOutCents - cashInCents)
        }

    fun player(id: Int): CashPlayer? = players.firstOrNull { it.id == id }
}
