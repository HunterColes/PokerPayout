package com.huntercoles.pokerpayout.core.domain.model

/**
 * What one entry paid to sit down (PP-116): the buy-in, food and bounty, recorded when a player
 * joined late or re-entered, at the amounts set then. Entries made before the start have none and
 * cost today's amounts ([MoneySettings]).
 */
data class EntryPrice(val buyInCents: Long, val foodCents: Long, val bountyCents: Long) {
    val totalCents: Long get() = buyInCents + foodCents + bountyCents

    companion object {
        /** An entry at [money]'s amounts now. */
        fun of(money: MoneySettings): EntryPrice = EntryPrice(money.buyInCents, money.foodCents, money.bountyCents)
    }
}

/**
 * One entry as the Bank records it: a seat in the tournament with its own stack, bounty and
 * finishing place. Who is out comes from the elimination order.
 *
 * Each rebuy and add-on keeps the price it was bought at (PP-085): [rebuyPricesCents] and
 * [addOnPricesCents] list them, oldest first, and when given they are the purchases ([rebuys] and
 * [addOns] are then ignored). Without them, every purchase costs today's amount from
 * [MoneySettings], as before PP-085.
 *
 * **Late entries and re-entries** (PP-116). An entry made once the clock is running keeps the price
 * it was bought at ([entryPrice]), as rebuys do, and in a mystery game its bounty is one more
 * envelope (the envelopes already dealt stay as they are). A re-entry is a new entry for a player
 * who was knocked out: a new stack, a new buy-in and a bounty of its own, with [reEntryOf] naming
 * that player's first entry. The earlier entry keeps its knockout and its place, so places count
 * entries; the settle-up and History add a player's entries together ([people]).
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
    val addOnPricesCents: List<Long>? = null,
    /**
     * Mystery bounties (PP-035): the envelope drawn when this player was knocked out, won by
     * whoever is credited with the knockout; null if none was drawn.
     */
    val bountyDrawCents: Long? = null,
    /** A late entry or a re-entry: what it paid to sit down, at the price then; null: today's amounts. */
    val entryPrice: EntryPrice? = null,
    /** A re-entry: the id of the same player's first entry; null for a first entry. */
    val reEntryOf: Int? = null
) {
    /** What this entry paid (or owes) to sit down. */
    fun entry(money: MoneySettings): EntryPrice = entryPrice ?: EntryPrice.of(money)

    companion object {
        /**
         * Who each entry in [players] belongs to (PP-116): entry id to the id of that player's first
         * entry. A re-entry of a re-entry belongs to the first entry too; one naming an entry that
         * isn't an earlier one in [players] (stale saved data) counts as a player of its own.
         */
        fun people(players: List<BankPlayer>): Map<Int, Int> {
            val person = mutableMapOf<Int, Int>()
            players.sortedBy { it.id }.forEach { player ->
                person[player.id] = player.reEntryOf?.takeIf { it < player.id }?.let { person[it] } ?: player.id
            }
            return person
        }
    }

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
    /**
     * What this player's knockouts pay: a bounty each (standard), the cash halves (progressive) or
     * the envelopes drawn (mystery).
     */
    val knockoutBountyCents: Long,
    /**
     * The champion's own bounty: nobody knocked them out, so they keep it. Progressive: their final
     * bounty, grown by their knockouts. Mystery: every envelope left in the pool.
     */
    val kingsBountyCents: Long,
    /** Bounties of players knocked out with nobody credited. They go to the champion. */
    val unclaimedBountyCents: Long,
    /** Buy-in, food and bounty, plus this player's rebuys and add-ons. */
    val costCents: Long,
    val paidOut: Boolean,
    /** The part of [costCents] spent on rebuys, at the prices paid. */
    val rebuyCostCents: Long = 0L,
    /** The part of [costCents] spent on add-ons, at the prices paid. */
    val addOnCostCents: Long = 0L,
    /**
     * The bounty on this player's head: what knocking them out is worth now, or was worth when they
     * went out. Standard: the bounty amount. Progressive: that plus half of each bounty they took
     * while still in. Mystery: 0 (a knockout draws an envelope instead).
     */
    val headBountyCents: Long = 0L
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
    val paidInCents: Long,
    val bountyMode: BountyMode = BountyMode.STANDARD,
    /** Mystery bounties: the envelopes not drawn yet, biggest first; empty in the other modes. */
    val envelopesLeft: List<Long> = emptyList()
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
