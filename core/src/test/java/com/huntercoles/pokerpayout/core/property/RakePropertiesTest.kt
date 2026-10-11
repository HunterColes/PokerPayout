package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.SettleUpUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import org.junit.jupiter.api.Test
import java.math.BigInteger

/**
 * The house rake (PP-145) as properties: whatever the pool (a cent to a billion dollars), the places,
 * the structure, the rounding, the currency and the rake (any step of the scale, or any rate put on
 * it), the one payout calculation pays the pool after the rake, and
 *
 * - the places plus the rake are exactly the money taken in for prizes, to the cent;
 * - the rake is its percentage of that money rounded down to the cent (the whole yen in yen), never
 *   a coin more and never a coin less;
 * - at 0% every table is exactly the one from before the rake;
 * - a higher rake never leaves the places more;
 * - over whole nights at the Bank, in every bounty mode, with late entries and re-entries, the money
 *   is conserved after every step, every bounty is paid whole and exactly as it would be without the
 *   rake, and at the settle-up the Bank keeps the rake and the food and nothing else.
 *
 * Seeded; a failure prints the smallest case that breaks it.
 */
class RakePropertiesTest {

    private val calculate = CalculatePayoutsUseCase()
    private val settle = SettleTournamentUseCase(calculate)
    private val settleUp = SettleUpUseCase()

    /** One payout question with a house rake on it. */
    private data class Case(
        val poolCents: Long,
        val weights: List<Int>,
        val players: Int,
        val rounding: PayoutRounding,
        val rake: HouseRake,
        val currency: AppCurrency,
    ) {
        /** The money taken in for prizes, raked as the Bank and the Payouts tab rake it. */
        val pool: PoolBreakdown get() = PoolBreakdown(poolCents, 0L, 0L, 0L, 0L).withRake(rake, currency)
    }

    @Test
    fun `the places plus the rake add up to the money taken in, to the cent`() =
        forAll(seed = 2026_1011_01L, iterations = 4_000, gen = cases) { case ->
            val pool = case.pool
            val table = calculate(pool, case.weights, case.players, case.rounding, case.currency)
            expect(table.places.isNotEmpty()) { "no places paid" }
            expect(table.rakeCents == pool.rakeCents) { "the table's rake ${table.rakeCents}, the pool's ${pool.rakeCents}" }
            expect(table.totalCents + table.rakeCents == case.poolCents) {
                "places ${table.places.map { it.amountCents }} = ${table.totalCents} + rake ${table.rakeCents} " +
                    "of ${case.poolCents} taken in"
            }
            expect(table.prizePoolCents == pool.prizePoolCents && table.takenInCents == case.poolCents) {
                "pool $pool, table $table"
            }
            expect(table.places.all { it.amountCents >= 0L }) { "a negative place: ${table.places}" }
        }

    @Test
    fun `the rake is its percentage rounded down to the cent, or to the whole yen`() =
        forAll(seed = 2026_1011_02L, iterations = 4_000, gen = cases) { case ->
            val rake = case.pool.rakeCents
            val coin = if (case.currency.hasCents) 1L else 100L
            // rake * 10,000 <= pool * bp < (rake + coin) * 10,000, in exact integers
            val exact = case.poolCents.big * case.rake.basisPoints.toLong().big
            val what = "${case.rake} of ${case.poolCents} took $rake"
            expect(rake.big * TEN_THOUSAND <= exact) { "$what: more than its share" }
            expect((rake + coin).big * TEN_THOUSAND > exact) { "$what: a coin or more short" }
            expect(rake % coin == 0L) { "a rake of $rake in ${case.currency}" }
            val points = case.rake.basisPoints
            expect(points in 0..HouseRake.MAX_BASIS_POINTS && points % HouseRake.STEP_BASIS_POINTS == 0) {
                "$points basis points is off the scale"
            }
        }

    @Test
    fun `at 0 percent every table is exactly the one from before the rake`() =
        forAll(seed = 2026_1011_03L, iterations = 3_000, gen = cases) { case ->
            val none = case.copy(rake = HouseRake.NONE)
            val withNone = calculate(none.pool, case.weights, case.players, case.rounding, case.currency)
            val before = calculate(case.poolCents, case.weights, case.players, case.rounding, case.currency)
            expect(withNone == before) { "at 0%: $withNone, before the rake: $before" }
            expect(none.pool.prizePoolCents == case.poolCents && none.pool.rakeCents == 0L) { "0% took something: ${none.pool}" }
        }

    @Test
    fun `a higher rake never leaves the places more`() =
        forAll(seed = 2026_1011_04L, iterations = 3_000, gen = Arb.bind(cases, rakes, ::Pair)) { (case, other) ->
            val (low, high) = listOf(case.rake, other).sortedBy { it.basisPoints }
            val lowTable = calculate(case.copy(rake = low).pool, case.weights, case.players, case.rounding, case.currency)
            val highTable = calculate(case.copy(rake = high).pool, case.weights, case.players, case.rounding, case.currency)
            expect(highTable.totalCents <= lowTable.totalCents) {
                "$high pays ${highTable.totalCents}, $low pays ${lowTable.totalCents}"
            }
        }

    /**
     * Whole nights at the Bank with a rake, in every bounty mode, late entries and re-entries too:
     * after every step the prize money in is the places' pool plus the rake, nothing is owed beyond
     * what the bank pays back out (the rake and the food aside), and with a champion every cent of the
     * prize pool and every bounty has an owner. The bounties are the very ones the night pays at 0%.
     */
    @Test
    fun `whole nights with a rake conserve the money and pay every bounty whole`() =
        forAll(seed = 2026_1011_05L, iterations = 1_500, gen = nights) { script ->
            val night = BankNight(script)
            night.play { step, settlement -> checkRaked(night, settlement) { "after step $step ${script.steps[step]}" } }
            night.finish()
            val finished = night.settlement()
            expect(finished.isComplete) { "no champion after the knockouts" }
            checkRaked(night, finished) { "at the end" }
            expect(finished.assignedCents == finished.pool.payableCents) {
                "a champion, but ${finished.assignedCents} owed of ${finished.pool.payableCents}"
            }
        }

    /**
     * The settle-up of any finished night with a rake: the balances square, and when everyone paid in
     * and nobody has been paid yet, what the bank keeps after paying every winner is exactly the rake
     * and the food.
     */
    @Test
    fun `at the settle-up the bank keeps the rake and the food, nothing else`() =
        forAll(seed = 2026_1011_06L, iterations = 1_000, gen = nights) { script ->
            val night = BankNight(script).apply {
                play()
                finish()
                players.replaceAll { _, player -> player.copy(boughtIn = true, paidOut = false) }
            }
            val settlement = night.settlement()
            val up = settleUp(settlement, night.players.values.toList(), script.money)
            expect(up != null) { "a finished night has no settle-up" }
            val balances = (up as SettleUp).balancesCents
            expect(balances.values.sum() == 0L) { "balances $balances don't add up to 0" }
            val kept = settlement.paidInCents + balances.getValue(SettleUp.BANK_ID)
            val pool = settlement.pool
            expect(kept == pool.rakeCents + pool.foodCents) { "the bank keeps $kept, not the rake and food of $pool" }
        }

    private fun checkRaked(night: BankNight, settlement: Settlement, where: () -> String) {
        val pool = settlement.pool
        expect(pool.prizeInCents == pool.prizePoolCents + pool.rakeCents) { "${where()}: $pool" }
        expect(pool.rakeCents == night.script.money.rake.centsOf(pool.prizeInCents)) { "${where()}: the rake of $pool" }
        val everything = pool.prizePoolCents + pool.rakeCents + pool.bountyPoolCents + pool.foodCents
        expect(pool.totalCents == everything) { "${where()}: $pool" }
        if (settlement.payoutTable.places.isNotEmpty()) {
            val table = settlement.payoutTable
            expect(table.totalCents + table.rakeCents == pool.prizeInCents) {
                "${where()}: places and rake ${table.totalCents} + ${table.rakeCents}"
            }
        }
        expect(settlement.assignedCents <= pool.payableCents) {
            "${where()}: owes ${settlement.assignedCents} of ${pool.payableCents}"
        }
        // The bounties are exactly the ones the same night pays without a rake
        val unraked = settle(
            night.players.values.toList(),
            night.order,
            night.script.money.copy(rake = HouseRake.NONE),
            night.script.weights,
            night.script.rounding,
        )
        settlement.players.forEach { raked ->
            val plain = unraked.forPlayer(raked.playerId)
            val same = plain != null &&
                raked.knockoutBountyCents == plain.knockoutBountyCents &&
                raked.kingsBountyCents == plain.kingsBountyCents &&
                raked.unclaimedBountyCents == plain.unclaimedBountyCents &&
                raked.headBountyCents == plain.headBountyCents
            expect(same) { "${where()}: player ${raked.playerId}'s bounties $raked, without the rake $plain" }
        }
        expect(settlement.envelopesLeft == unraked.envelopesLeft) { "${where()}: envelopes ${settlement.envelopesLeft}" }
    }

    private companion object {
        val TEN_THOUSAND: BigInteger = BigInteger.valueOf(10_000L)
        val Long.big: BigInteger get() = BigInteger.valueOf(this)

        /** A pool from a cent to a billion dollars, smaller pools as often as large ones (rounding bites there). */
        val pools = Arb.bind(Arb.int(0..3), Arb.long(0L..99_999_999_999L)) { magnitude, raw ->
            raw % listOf(10_000L, 1_000_000L, 100_000_000L, 100_000_000_000L)[magnitude]
        }

        /** A preset's structure, or weights edited by hand. */
        val structures = Arb.bind(
            Arb.element(PayoutPreset.entries),
            Arb.int(1..9),
            Arb.list(Arb.int(1..999), 1..9),
            Arb.int(0..1),
        ) { preset, places, raw, edited ->
            if (edited == 1) raw.distinct().sortedDescending() else preset.weightsFor(places)
        }

        val rakes = BankNight.rakes

        val cases = Arb.bind(
            pools,
            structures,
            Arb.int(1..80),
            Arb.element(PayoutRounding.entries),
            rakes,
            Arb.element(AppCurrency.DOLLAR, AppCurrency.EURO, AppCurrency.YEN),
            ::Case,
        )

        /** Nights in every bounty mode, with a rake, late entries and re-entries. */
        val nights = BankNight.scripts(entries = true, raked = true)
    }
}
