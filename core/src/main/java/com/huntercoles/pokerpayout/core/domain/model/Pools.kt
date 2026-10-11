package com.huntercoles.pokerpayout.core.domain.model

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.utils.Money

/**
 * What each player pays, in cents, and how the bounties pay out ([bountyMode], PP-035: standard
 * unless the host picks progressive or mystery).
 */
data class MoneySettings(
    val buyInCents: Long,
    val foodCents: Long,
    val bountyCents: Long,
    val rebuyCents: Long,
    val addOnCents: Long,
    val bountyMode: BountyMode = BountyMode.STANDARD
) {
    /** What every player pays to sit down: buy-in, food and bounty. Rebuys and add-ons come on top. */
    val entryCents: Long get() = buyInCents + foodCents + bountyCents

    companion object {
        val DEFAULT = MoneySettings(
            buyInCents = Money.centsOf(TournamentDefaults.BUY_IN),
            foodCents = Money.centsOf(TournamentDefaults.FOOD_PER_PLAYER),
            bountyCents = Money.centsOf(TournamentDefaults.BOUNTY_PER_PLAYER),
            rebuyCents = Money.centsOf(TournamentDefaults.REBUY_PER_PLAYER),
            addOnCents = Money.centsOf(TournamentDefaults.ADDON_PER_PLAYER)
        )
    }
}

/** Where the money in the box comes from, in cents. */
data class PoolBreakdown(
    val buyInCents: Long,
    val foodCents: Long,
    val bountyCents: Long,
    val rebuyCents: Long,
    val addOnCents: Long
) {
    /** Paid out by place: buy-ins, rebuys and add-ons. */
    val prizePoolCents: Long get() = buyInCents + rebuyCents + addOnCents

    /** Paid out by knockout: one bounty per player. */
    val bountyPoolCents: Long get() = bountyCents

    /** Everything the bank pays back out (food is spent, not paid out). */
    val payableCents: Long get() = prizePoolCents + bountyPoolCents

    val totalCents: Long get() = prizePoolCents + foodCents + bountyCents

    companion object {
        val EMPTY = PoolBreakdown(0L, 0L, 0L, 0L, 0L)

        /**
         * The pool with the rebuys and add-ons the Bank recorded, at the prices they were bought at
         * (PP-085): [rebuyCents] and [addOnCents] are their totals. [playerCount] counts entries:
         * [recordedEntries] are the late entries and re-entries among them, at the prices they paid
         * (PP-116); the others pay today's amounts.
         */
        fun withRecordedPurchases(
            money: MoneySettings,
            playerCount: Int,
            rebuyCents: Long,
            addOnCents: Long,
            recordedEntries: List<EntryPrice> = emptyList(),
        ): PoolBreakdown {
            val players = playerCount.coerceAtLeast(0)
            val recorded = recordedEntries.take(players)
            val atToday = (players - recorded.size).toLong()
            return PoolBreakdown(
                buyInCents = atToday * money.buyInCents + recorded.sumOf { it.buyInCents.coerceAtLeast(0L) },
                foodCents = atToday * money.foodCents + recorded.sumOf { it.foodCents.coerceAtLeast(0L) },
                bountyCents = atToday * money.bountyCents + recorded.sumOf { it.bountyCents.coerceAtLeast(0L) },
                rebuyCents = rebuyCents.coerceAtLeast(0L),
                addOnCents = addOnCents.coerceAtLeast(0L)
            )
        }

        /** The pool with [rebuyCount] rebuys and [addOnCount] add-ons, all at today's prices. */
        fun of(money: MoneySettings, playerCount: Int, rebuyCount: Int, addOnCount: Int): PoolBreakdown {
            val players = playerCount.coerceAtLeast(0).toLong()
            return PoolBreakdown(
                buyInCents = players * money.buyInCents,
                foodCents = players * money.foodCents,
                bountyCents = players * money.bountyCents,
                rebuyCents = rebuyCount.coerceAtLeast(0) * money.rebuyCents,
                addOnCents = addOnCount.coerceAtLeast(0) * money.addOnCents
            )
        }
    }
}
