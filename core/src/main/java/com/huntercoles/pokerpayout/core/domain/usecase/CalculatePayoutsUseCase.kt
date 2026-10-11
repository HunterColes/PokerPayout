package com.huntercoles.pokerpayout.core.domain.usecase

import com.huntercoles.pokerpayout.core.domain.model.PayoutPlace
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import com.huntercoles.pokerpayout.core.utils.MoneyFormat
import javax.inject.Inject

/**
 * The one payout calculation, used by both the Tournament and the Bank tab.
 *
 * 1. Pay `min(weights, players)` places, never more places than there are players.
 * 2. Each place below 1st gets its weighted share of the pool rounded to the nearest unit,
 *    [PayoutRounding.unitCentsIn] the host's [AppCurrency] (halves round up): $1, $5 or $10, or
 *    in yen ¥100, ¥500 or ¥1,000 (PP-114).
 * 3. 1st gets everything that is left, so the amounts add up to the prize pool exactly, cents
 *    included.
 * 4. If rounding up the lower places would leave 1st with less than 2nd (a small pool with a large
 *    unit), every lower place is rounded down instead. 1st then gets at least its own share.
 * 5. A place that would round to nothing isn't paid: while the pool has money, the last places that
 *    would get $0 are dropped and the table is worked out again for fewer places. (27 players at $10
 *    rounded to $10 used to pay 9th place $0.)
 *
 * Example: pool $230, weights 50/30/20, $5 units. 2nd: 69.00 -> 70, 3rd: 46.00 -> 45, 1st: 115.
 */
class CalculatePayoutsUseCase @Inject constructor() {

    operator fun invoke(
        prizePoolCents: Long,
        weights: List<Int>,
        playerCount: Int,
        rounding: PayoutRounding = PayoutRounding.DEFAULT,
        currency: AppCurrency = MoneyFormat.current,
    ): PayoutTable {
        val pool = prizePoolCents.coerceAtLeast(0L)
        var paying = weights.filter { it > 0 }.take(PayoutPlaces.maxFor(playerCount))
        if (paying.isEmpty() || playerCount < 1) {
            return PayoutTable(prizePoolCents = pool, places = emptyList(), rounding = rounding)
        }
        val unit = rounding.unitCentsIn(currency)
        var table = table(pool, paying, rounding, unit)
        while (pool > 0L && table.places.size > 1 && table.places.last().amountCents == 0L) {
            paying = paying.dropLast(1)
            table = table(pool, paying, rounding, unit)
        }
        return table
    }

    /**
     * The most places, up to [PayoutPlaces.maxFor] [playerCount], that each pay something with
     * [settings] and this pool (rule 5): where the Payouts tab's stepper stops.
     */
    fun payablePlaces(prizePoolCents: Long, settings: PayoutSettings, playerCount: Int): Int =
        (PayoutPlaces.maxFor(playerCount) downTo 1).first { count ->
            invoke(prizePoolCents, settings.withPlaces(count).weights, playerCount, settings.rounding).places.size == count
        }

    private fun table(pool: Long, paying: List<Int>, rounding: PayoutRounding, unit: Long): PayoutTable {
        val totalWeight = paying.sumOf { it.toLong() }
        val lowerPlaces = roundLowerPlaces(pool, paying, totalWeight, unit)
        val amounts = listOf(pool - lowerPlaces.sum()) + lowerPlaces

        val places = paying.mapIndexed { index, weight ->
            PayoutPlace(
                place = index + 1,
                amountCents = amounts[index],
                weight = weight,
                sharePercent = weight * PERCENT / totalWeight
            )
        }
        return PayoutTable(prizePoolCents = pool, places = places, rounding = rounding)
    }

    private fun roundLowerPlaces(pool: Long, weights: List<Int>, totalWeight: Long, unit: Long): List<Long> {
        val lowerWeights = weights.drop(1)
        val nearest = lowerWeights.map { roundToUnit(pool * it, totalWeight, unit, halfUp = true) }
        val firstPlaceLeft = pool - nearest.sum()
        return if (nearest.all { it <= firstPlaceLeft }) {
            nearest
        } else {
            lowerWeights.map { roundToUnit(pool * it, totalWeight, unit, halfUp = false) }
        }
    }

    /** `numerator / denominator` cents, rounded to a whole number of [unit]s, in exact integer math. */
    private fun roundToUnit(numerator: Long, denominator: Long, unit: Long, halfUp: Boolean): Long {
        val step = denominator * unit
        val units = if (halfUp) (2 * numerator + step) / (2 * step) else numerator / step
        return units * unit
    }

    private companion object {
        const val PERCENT = 100.0
    }
}
