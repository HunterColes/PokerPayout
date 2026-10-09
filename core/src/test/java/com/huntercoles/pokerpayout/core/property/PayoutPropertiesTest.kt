package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.design.components.detectInvalidWeights
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutTable
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import org.junit.jupiter.api.Test
import java.math.BigInteger

/**
 * The one payout calculation ([CalculatePayoutsUseCase]) as properties: whatever the pool (a cent to
 * a billion dollars), the field, the structure (a preset, weights edited by hand, or old saved weights
 * in any order) and the rounding, the table must add up to the pool to the cent, pay whole units
 * below 1st, stay within a unit of each place's share, and never pay a place more than the one
 * above it. Seeded; a failure prints the smallest case that breaks it.
 */
class PayoutPropertiesTest {

    private val calculate = CalculatePayoutsUseCase()

    /** One payout question, and the table the app answers it with. */
    private data class Case(val poolCents: Long, val weights: List<Int>, val players: Int, val rounding: PayoutRounding) {
        val unit: Long get() = rounding.unitCents
        val paid: List<Int> get() = weights.take(PayoutPlaces.maxFor(players))
    }

    private fun Case.table(): PayoutTable = calculate(poolCents, weights, players, rounding)

    @Test
    fun `the table adds up to the pool to the cent and pays nobody less than nothing`() =
        forAll(seed = 2026_1008_01L, iterations = 4_000, gen = cases) { case ->
            val table = case.table()
            val amounts = table.places.map { it.amountCents }
            expect(table.places.map { it.place } == (1..case.paid.size).toList()) {
                "places ${table.places.map { it.place }}, expected 1..${case.paid.size}"
            }
            expect(table.totalCents == case.poolCents) { "pays ${table.totalCents} of a ${case.poolCents} pool: $amounts" }
            expect(amounts.all { it >= 0L }) { "a negative place: $amounts" }
            expect(table.places.map { it.weight } == case.paid) { "weights ${table.places.map { it.weight }}" }
        }

    @Test
    fun `every place below 1st is whole units and within one unit of its exact share`() =
        forAll(seed = 2026_1008_02L, iterations = 4_000, gen = cases) { case ->
            val table = case.table()
            val total = case.paid.sumOf { it.toLong() }
            table.places.drop(1).forEach { place ->
                expect(place.amountCents % case.unit == 0L) { "${place.ordinal} pays ${place.amountCents}: not whole units" }
                // |amount - pool * weight / total| < unit, in exact integers
                val miss = (place.amountCents.big * total.big - case.poolCents.big * place.weight.toLong().big).abs()
                expect(miss < case.unit.big * total.big) {
                    "${place.ordinal} pays ${place.amountCents}, a unit or more from its share " +
                        "${case.poolCents * place.weight / total.toDouble()}"
                }
            }
        }

    @Test
    fun `with falling weights no place pays more than the place above it`() =
        forAll(seed = 2026_1008_03L, iterations = 4_000, gen = cases) { case ->
            if (case.weights.zipWithNext().any { (above, below) -> below > above }) return@forAll
            val amounts = case.table().places.map { it.amountCents }
            expect(amounts.zipWithNext().all { (above, below) -> above >= below }) { "out of order: $amounts" }
        }

    /** Only the shares matter: 50/30/20 and 5/3/2 are the same structure, so they pay the same. */
    @Test
    fun `weights scaled by any factor pay exactly the same`() =
        forAll(seed = 2026_1008_04L, iterations = 3_000, gen = Arb.bind(cases, Arb.int(2..60), ::Pair)) { (case, factor) ->
            val scaled = case.copy(weights = case.weights.map { it * factor })
            val amounts = case.table().places.map { it.amountCents }
            val scaledAmounts = scaled.table().places.map { it.amountCents }
            expect(amounts == scaledAmounts) { "weights x$factor pay $scaledAmounts instead of $amounts" }
        }

    /** A pool that splits into whole units exactly needs no rounding: every place gets its share. */
    @Test
    fun `a pool that divides exactly pays every place its exact share`() =
        forAll(seed = 2026_1008_05L, iterations = 3_000, gen = Arb.bind(cases, quotients, ::Pair)) { (case, quotient) ->
            val total = case.paid.sumOf { it.toLong() }
            if (total == 0L) return@forAll
            val exact = case.copy(poolCents = total * case.unit * quotient)
            val amounts = exact.table().places.map { it.amountCents }
            val shares = exact.paid.map { it * case.unit * quotient }
            expect(amounts == shares) { "pool ${exact.poolCents} pays $amounts, not its exact shares $shares" }
        }

    /**
     * Changing the places paid ([PayoutSettings.withPlaces], the editor's stepper) always gives a
     * structure the editor accepts: as many weights as places, each at least 1 and smaller than the
     * one above. Fewer places keep the top of the table; more keep its shares and add smaller places
     * below.
     */
    @Test
    fun `more or fewer places always give a structure the editor accepts`() =
        forAll(seed = 2026_1008_06L, iterations = 3_000, gen = editedAndPlaces) { (weights, places) ->
            val target = places.coerceIn(1, PayoutPlaces.MAX)
            val resized = PayoutSettings(weights, preset = null, rounding = PayoutRounding.DEFAULT).withPlaces(places).weights
            expect(resized.size == target) { "$weights to $places places gave ${resized.size} weights: $resized" }
            expect(resized.all { it >= 1 } && detectInvalidWeights(resized).none { it }) {
                "$weights to $places places gave $resized, which the editor flags"
            }
            if (target <= weights.size) {
                expect(resized == weights.take(target)) { "$weights cut to $target gave $resized" }
            } else {
                // The places already there keep their shares: the same multiple of each weight
                val scale = resized.first() / weights.first()
                expect(resized.take(weights.size) == weights.map { it * scale }) { "$weights grown to $target gave $resized" }
            }
        }

    @Test
    fun `every preset pays a falling table for any number of places asked for`() =
        forAll(seed = 2026_1008_07L, iterations = 500, gen = Arb.bind(presets, Arb.int(-3..15), ::Pair)) { (preset, places) ->
            val weights = preset.weightsFor(places)
            expect(weights.size == places.coerceIn(1, PayoutPlaces.MAX)) { "$preset for $places: $weights" }
            expect(detectInvalidWeights(weights).none { it } && weights.all { it > 0 }) { "$preset for $places: $weights" }
        }

    @Test
    fun `the recommended places are always payable`() =
        forAll(seed = 2026_1008_08L, iterations = 1_000, gen = Arb.int(-5..2_000)) { players ->
            val recommended = PayoutPlaces.recommended(players)
            expect(recommended in 1..PayoutPlaces.maxFor(players)) { "$players players: $recommended places" }
        }

    private companion object {
        /** A pool from a cent to a billion dollars, smaller pools as often as large ones (rounding bites there). */
        val pools = Arb.bind(Arb.int(0..3), Arb.long(0L..99_999_999_999L)) { magnitude, raw ->
            raw % listOf(10_000L, 1_000_000L, 100_000_000L, 100_000_000_000L)[magnitude]
        }

        /** Weights edited by hand, as the editor allows them: 1 to 9 places, 1 to 999 each, strictly falling. */
        val editedWeights = Arb.list(Arb.int(1..999), 1..9).map { it.distinct().sortedDescending() }

        /** Any structure the app can hold: a preset's, hand-edited, or old saved weights in any order. */
        val structures = Arb.bind(
            Arb.int(0..2),
            Arb.element(PayoutPreset.entries),
            Arb.int(1..9),
            Arb.list(Arb.int(1..999), 1..9),
        ) { kind, preset, places, raw ->
            when (kind) {
                0 -> preset.weightsFor(places)
                1 -> raw.distinct().sortedDescending()
                else -> raw
            }
        }

        val cases = Arb.bind(pools, structures, Arb.int(1..80), Arb.element(PayoutRounding.entries), ::Case)

        val presets = Arb.element(PayoutPreset.entries)
        val quotients = Arb.long(0L..50_000L)
        val editedAndPlaces = Arb.bind(editedWeights, Arb.int(1..12), ::Pair)

        val Long.big: BigInteger get() = BigInteger.valueOf(this)
    }
}
