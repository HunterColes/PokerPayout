package com.huntercoles.pokerpayout.core.domain.model

/**
 * Progressive knockout (PKO, PP-035): a knockout splits the knocked-out player's bounty in two.
 *
 * The rounding rule: the cash half is rounded down to whole cents and the odd cent goes onto the
 * eliminator's bounty, so the two halves always add up to the bounty exactly. A $7.75 bounty pays
 * $3.87 now and puts $3.88 on the eliminator's head.
 */
object ProgressiveBounty {

    /** How [bountyCents] splits on a knockout. */
    fun split(bountyCents: Long): Split {
        val bounty = bountyCents.coerceAtLeast(0L)
        val cash = bounty / 2
        return Split(cashCents = cash, headCents = bounty - cash)
    }

    /** [cashCents] paid to the eliminator now, [headCents] added to the eliminator's own bounty. */
    data class Split(val cashCents: Long, val headCents: Long)
}
