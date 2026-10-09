package com.huntercoles.pokerpayout.core.domain.usecase

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import com.huntercoles.pokerpayout.core.domain.model.ProgressiveBounty
import com.huntercoles.pokerpayout.core.domain.model.Standings

/**
 * What every player's knockouts are worth under the game's [BountyMode] (PP-035), worked out from
 * what the Bank recorded: who went out in what order, and who is credited with each knockout.
 *
 * Every bounty in the pool ends with exactly one player: once the champion is known, the knockout
 * winnings, the champion's own bounty and the unclaimed bounties add up to the bounty pool to the
 * cent, in every mode.
 */
internal class BountyLedger(
    /** Knockouts credited to each player. */
    val knockouts: Map<Int, Int>,
    /** What those knockouts pay each player. */
    val knockoutCents: Map<Int, Long>,
    /** The bounty on each player's head now, or when they went out. */
    val heads: Map<Int, Long>,
    /** The champion's own bounty (mystery: the envelopes left); 0 until there is a champion. */
    val championCents: Long,
    /** Bounties of knockouts nobody was credited with, for the champion; 0 until there is one. */
    val unclaimedCents: Long,
    /** Mystery: the envelopes not drawn yet. */
    val envelopesLeft: List<Long>,
) {
    companion object {
        fun of(players: List<BankPlayer>, standings: Standings, money: MoneySettings): BountyLedger {
            val byId = players.associateBy { it.id }
            val champion = standings.championId
            // In the order they went out. The champion was never knocked out, whatever a stale record says.
            val knockouts = standings.eliminated.filter { it != champion }.map { victim ->
                Knockout(victim, byId[victim]?.eliminatedBy?.takeIf { it != victim && it in byId })
            }
            val bounty = money.bountyCents.coerceAtLeast(0L)
            return when (money.bountyMode) {
                BountyMode.STANDARD -> standard(byId.keys, knockouts, champion, bounty)
                BountyMode.PROGRESSIVE -> progressive(byId.keys, knockouts, champion, bounty)
                BountyMode.MYSTERY -> mystery(byId, knockouts, champion, bounty)
            }
        }

        /** Each knockout wins the whole bounty; the champion keeps theirs and takes the unclaimed ones. */
        private fun standard(ids: Set<Int>, knockouts: List<Knockout>, champion: Int?, bounty: Long): BountyLedger {
            val counts = knockouts.mapNotNull { it.eliminator }.groupingBy { it }.eachCount()
            val unclaimed = knockouts.count { it.eliminator == null }
            return BountyLedger(
                knockouts = counts,
                knockoutCents = counts.mapValues { (_, count) -> count * bounty },
                heads = ids.associateWith { bounty },
                championCents = if (champion != null) bounty else 0L,
                unclaimedCents = if (champion != null) unclaimed * bounty else 0L,
                envelopesLeft = emptyList(),
            )
        }

        /**
         * Replays the knockouts in order. Each pays half the knocked-out player's bounty to the
         * eliminator and puts the other half on the eliminator's head ([ProgressiveBounty.split]).
         * An eliminator already out by then (a knockout recorded late) has no head left to grow,
         * so takes the whole bounty in cash. Bounties nobody was credited with wait for the
         * champion, who also takes their own final bounty.
         */
        private fun progressive(ids: Set<Int>, knockouts: List<Knockout>, champion: Int?, bounty: Long): BountyLedger {
            val heads = ids.associateWith { bounty }.toMutableMap()
            val cash = mutableMapOf<Int, Long>()
            val out = mutableSetOf<Int>()
            var unclaimed = 0L
            knockouts.forEach { (victim, eliminator) ->
                val onHead = heads.getValue(victim)
                when {
                    eliminator == null -> unclaimed += onHead
                    eliminator in out -> cash.add(eliminator, onHead)
                    else -> {
                        val split = ProgressiveBounty.split(onHead)
                        cash.add(eliminator, split.cashCents)
                        heads[eliminator] = heads.getValue(eliminator) + split.headCents
                    }
                }
                out += victim
            }
            return BountyLedger(
                knockouts = knockouts.mapNotNull { it.eliminator }.groupingBy { it }.eachCount(),
                knockoutCents = cash,
                heads = heads,
                championCents = champion?.let { heads.getValue(it) } ?: 0L,
                unclaimedCents = if (champion != null) unclaimed else 0L,
                envelopesLeft = emptyList(),
            )
        }

        /**
         * Each credited knockout wins the envelope drawn for it. Knockouts nobody was credited with
         * draw nothing, so their envelopes stay in the pool; the champion takes whatever is left.
         * A player joining after envelopes were drawn adds to the pool ([MysteryBounty.left]).
         */
        private fun mystery(
            byId: Map<Int, BankPlayer>,
            knockouts: List<Knockout>,
            champion: Int?,
            bounty: Long,
        ): BountyLedger {
            val pool = MysteryBounty.envelopes(byId.size, bounty).sum()
            val claimed = knockouts.mapNotNull { (victim, eliminator) ->
                eliminator?.let { Draw(it, byId[victim]?.bountyDrawCents?.coerceAtLeast(0L)) }
            }
            val drawn = claimed.mapNotNull { it.cents }
            return BountyLedger(
                knockouts = claimed.groupingBy { it.eliminator }.eachCount(),
                knockoutCents = claimed.groupBy({ it.eliminator }, { it.cents ?: 0L })
                    .mapValues { (_, cents) -> cents.sum() },
                heads = emptyMap(),
                // The envelopes left, as the pool holds them: everything dealt minus everything drawn
                championCents = if (champion != null) (pool - drawn.sum()).coerceAtLeast(0L) else 0L,
                unclaimedCents = 0L,
                envelopesLeft = MysteryBounty.left(byId.size, bounty, drawn),
            )
        }

        private fun MutableMap<Int, Long>.add(id: Int, cents: Long) {
            this[id] = (this[id] ?: 0L) + cents
        }
    }

    /** [victim] went out; [eliminator] is credited with it, if anyone still at the table is. */
    private data class Knockout(val victim: Int, val eliminator: Int?)

    /** A credited mystery knockout and the envelope drawn for it ([cents] null: none drawn). */
    private class Draw(val eliminator: Int, val cents: Long?)
}
