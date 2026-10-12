package com.huntercoles.pokerpayout.core.domain.model

import com.huntercoles.pokerpayout.core.constants.TournamentDefaults
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.MoneyFormat

/**
 * What each player pays, in cents, how the bounties pay out ([bountyMode], PP-035: standard unless
 * the host picks progressive or mystery), and the house rake on the prize pool ([rake], PP-145: none
 * unless the host sets one).
 */
data class MoneySettings(
    val buyInCents: Long,
    val foodCents: Long,
    val bountyCents: Long,
    val rebuyCents: Long,
    val addOnCents: Long,
    val bountyMode: BountyMode = BountyMode.STANDARD,
    val rake: HouseRake = HouseRake.NONE
) {
    /** What every player pays to sit down: buy-in, food and bounty. Rebuys and add-ons come on top. */
    val entryCents: Long get() = buyInCents + foodCents + bountyCents

    companion object {
        val DEFAULT = MoneySettings(
            buyInCents = TournamentDefaults.BUY_IN_CENTS,
            foodCents = TournamentDefaults.FOOD_PER_PLAYER_CENTS,
            bountyCents = TournamentDefaults.BOUNTY_PER_PLAYER_CENTS,
            rebuyCents = TournamentDefaults.REBUY_PER_PLAYER_CENTS,
            addOnCents = TournamentDefaults.ADDON_PER_PLAYER_CENTS
        )
    }
}

/**
 * Where the money in the box comes from, in cents, and the house's cut of it ([rakeCents], PP-145).
 *
 * The rake is worked out here, once, where the pool is added up ([HouseRake.centsOf] on
 * [prizeInCents]), so every screen that shows the pool shows the same rake, and the one payout
 * calculation pays what is left ([prizePoolCents]). The money taken in is accounted for to the cent:
 * [prizeInCents] = [prizePoolCents] + [rakeCents], and [totalCents] = the prize pool + the rake + the
 * bounties + the food.
 */
data class PoolBreakdown(
    val buyInCents: Long,
    val foodCents: Long,
    val bountyCents: Long,
    val rebuyCents: Long,
    val addOnCents: Long,
    /** The house rake (PP-145), off [prizeInCents]; 0 without one. */
    val rakeCents: Long = 0L,
    /** The rate [rakeCents] was worked out at, for the screens that say so ("5% house rake"). */
    val rake: HouseRake = HouseRake.NONE
) {
    /** Taken in for prizes: the buy-ins, rebuys and add-ons, before the house rake. */
    val prizeInCents: Long get() = buyInCents + rebuyCents + addOnCents

    /** Paid out by place: the buy-ins, rebuys and add-ons, less the house rake. */
    val prizePoolCents: Long get() = prizeInCents - rakeCents

    /** Paid out by knockout: one bounty per player. Never raked. */
    val bountyPoolCents: Long get() = bountyCents

    /** Everything the bank pays back out (the food is spent and the rake is the house's). */
    val payableCents: Long get() = prizePoolCents + bountyPoolCents

    /** Everything taken in: the prize pool, the rake, the bounties and the food. */
    val totalCents: Long get() = prizeInCents + foodCents + bountyCents

    /** This pool with [rake] taken off its prize money, in the host's [currency] (whole yen have no cents). */
    fun withRake(rake: HouseRake, currency: AppCurrency = MoneyFormat.current): PoolBreakdown =
        copy(rakeCents = rake.centsOf(prizeInCents, currency), rake = rake)

    companion object {
        val EMPTY = PoolBreakdown(0L, 0L, 0L, 0L, 0L)

        /**
         * The pool with the rebuys and add-ons the Bank recorded, at the prices they were bought at
         * (PP-085): [rebuyCents] and [addOnCents] are their totals. [playerCount] counts entries:
         * [recordedEntries] are the late entries and re-entries among them, at the prices they paid
         * (PP-116); the others pay today's amounts. The house rake is [money]'s (PP-145), in the host's
         * currency ([MoneyFormat.current]: whole yen have no cents).
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
            ).withRake(money.rake)
        }

        /** The pool with [rebuyCount] rebuys and [addOnCount] add-ons, all at today's prices, raked as [money] says. */
        fun of(
            money: MoneySettings,
            playerCount: Int,
            rebuyCount: Int,
            addOnCount: Int,
            currency: AppCurrency = MoneyFormat.current,
        ): PoolBreakdown {
            val players = playerCount.coerceAtLeast(0).toLong()
            return PoolBreakdown(
                buyInCents = players * money.buyInCents,
                foodCents = players * money.foodCents,
                bountyCents = players * money.bountyCents,
                rebuyCents = rebuyCount.coerceAtLeast(0) * money.rebuyCents,
                addOnCents = addOnCount.coerceAtLeast(0) * money.addOnCents
            ).withRake(money.rake, currency)
        }
    }
}
