package com.huntercoles.pokerpayout.core.design.components

/** What the payout structure editor previews amounts against. */
data class PayoutPreview(val prizePoolCents: Long, val playerCount: Int)

/** True for each weight that isn't strictly between its neighbours (each place must weigh less than the one above). */
internal fun detectInvalidWeights(weights: List<Int>): List<Boolean> = weights.mapIndexed { index, weight ->
    val violatesPrev = index > 0 && weight >= weights[index - 1]
    val violatesNext = index < weights.lastIndex && weight <= weights[index + 1]
    violatesPrev || violatesNext
}
