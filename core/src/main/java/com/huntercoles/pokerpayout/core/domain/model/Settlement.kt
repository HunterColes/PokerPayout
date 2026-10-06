package com.huntercoles.pokerpayout.core.domain.model

/**
 * One player as the Bank records them. Who is out comes from the elimination order.
 *
 * Each rebuy and add-on keeps the price it was bought at (PP-085): [rebuyPricesCents] and
 * [addOnPricesCents] list them, oldest first, and when given they are the purchases ([rebuys] and
 * [addOns] are then ignored). Without them, every purchase costs today's amount from
 * [MoneySettings], as before PP-085.
 */
data class BankPlayer(
    val id: Int,
    val boughtIn: Boolean = false,
    val paidOut: Boolean = false,
    val rebuys: Int = 0,
    val addOns: Int = 0,
    /** Who knocked this player out; null if nobody was credited. */
    val eliminatedBy: Int? = null,
    val rebuyPricesCents: List<Long>? = null,
    val addOnPricesCents: List<Long>? = null
) {
    /** What this player's rebuys cost, at the prices they were bought at. */
    fun rebuyCostCents(money: MoneySettings): Long = rebuyPricesCents?.sum() ?: (rebuys * money.rebuyCents)

    /** What this player's add-ons cost, at the prices they were bought at. */
    fun addOnCostCents(money: MoneySettings): Long = addOnPricesCents?.sum() ?: (addOns * money.addOnCents)
}

/** What one player has won and paid, in cents. */
data class PlayerSettlement(
    val playerId: Int,
    /** Finishing place, once decided. */
    val place: Int?,
    /** This place's row of the payout table; 0 outside the money or while undecided. */
    val prizeCents: Long,
    val knockouts: Int,
    val knockoutBountyCents: Long,
    /** The champion's own bounty: nobody knocked them out, so they keep it. */
    val kingsBountyCents: Long,
    /** Bounties of players knocked out with nobody credited. They go to the champion. */
    val unclaimedBountyCents: Long,
    /** Buy-in, food and bounty, plus this player's rebuys and add-ons. */
    val costCents: Long,
    val paidOut: Boolean,
    /** The part of [costCents] spent on rebuys, at the prices paid. */
    val rebuyCostCents: Long = 0L,
    /** The part of [costCents] spent on add-ons, at the prices paid. */
    val addOnCostCents: Long = 0L
) {
    val winningsCents: Long get() = prizeCents + knockoutBountyCents + kingsBountyCents + unclaimedBountyCents
    val netCents: Long get() = winningsCents - costCents
}

/**
 * Who is owed what. Every place in [payoutTable] and every bounty in the pool belongs to exactly
 * one player, so:
 * - the winnings of any group of players never exceed [PoolBreakdown.payableCents];
 * - once the champion is known ([isComplete]), all winnings add up to it exactly.
 */
data class Settlement(
    val pool: PoolBreakdown,
    val payoutTable: PayoutTable,
    val standings: Standings,
    val players: List<PlayerSettlement>,
    /** Entry fees of players marked as bought in, plus every recorded rebuy and add-on. */
    val paidInCents: Long
) {
    val championId: Int? get() = standings.championId

    /** Every place is decided, so every prize and bounty has an owner. */
    val isComplete: Boolean get() = championId != null

    /** Winnings of the players marked as paid. */
    val paidOutCents: Long get() = players.filter { it.paidOut }.sumOf { it.winningsCents }

    /** Winnings assigned so far; equals [PoolBreakdown.payableCents] when [isComplete]. */
    val assignedCents: Long get() = players.sumOf { it.winningsCents }

    fun forPlayer(playerId: Int): PlayerSettlement? = players.firstOrNull { it.playerId == playerId }
}
