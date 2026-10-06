package com.huntercoles.pokerpayout.core.utils

import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings

/**
 * How many stacks the chip set keeps back for rebuys and add-ons until you say (PP-091 #3): an
 * estimate from the Tournament's own settings.
 *
 * Those settings give each a price (no rebuys or no add-ons at $0) and rebuys a cutoff ("rebuys
 * until level N", 0 for none), but no limit on how many a player buys and no chip amount. So a
 * rebuy or an add-on counts as one starting stack, the unit the chip set plans in, and the counts
 * are an estimate:
 *
 * - **Rebuys** with a cutoff: about half the players rebuy once before it, so players / 2, rounded
 *   up. With no cutoff rebuys are open all game: one each.
 * - **Add-ons**: one each, as the break screen counts them ("3 of 9 players have taken the add-on").
 * - Together at most [ChipSetSettings.RESERVE_RANGE]'s top.
 *
 * @property rebuyStacks stacks for rebuys; 0 when rebuys are off.
 * @property addOnStacks stacks for add-ons; 0 when add-ons are off.
 * @property rebuyCutoff rebuys stop at a level (so about half the players rebuy), rather than all game.
 */
data class KeptBackEstimate(val rebuyStacks: Int, val addOnStacks: Int, val rebuyCutoff: Boolean) {

    /** Rebuys and add-ons together, before the cap. */
    val wanted: Int get() = rebuyStacks + addOnStacks

    /** The stacks to keep back: [wanted], at most [MAX_STACKS]. */
    val stacks: Int get() = wanted.coerceAtMost(MAX_STACKS)

    /** [wanted] was more than the chip set keeps back. */
    val capped: Boolean get() = wanted > MAX_STACKS

    companion object {
        /** The most stacks the chip set keeps back. */
        val MAX_STACKS: Int = ChipSetSettings.RESERVE_RANGE.last

        /** No rebuys and no add-ons: nothing to keep back. */
        val NONE = KeptBackEstimate(rebuyStacks = 0, addOnStacks = 0, rebuyCutoff = false)

        /**
         * The estimate for [players] players with rebuys at [rebuyCents] and add-ons at [addOnCents]
         * (0 = off), and rebuys until level [rebuyUntilLevel] (0 = no cutoff).
         */
        fun of(players: Int, rebuyCents: Long, addOnCents: Long, rebuyUntilLevel: Int): KeptBackEstimate {
            val seats = players.coerceAtLeast(0)
            val cutoff = rebuyUntilLevel > 0
            val rebuys = when {
                rebuyCents <= 0L -> 0
                cutoff -> (seats + 1) / 2
                else -> seats
            }
            val addOns = if (addOnCents > 0L) seats else 0
            return KeptBackEstimate(rebuyStacks = rebuys, addOnStacks = addOns, rebuyCutoff = cutoff)
        }
    }
}
