package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.settle.MinimumPayments
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.SettleUpUseCase
import com.huntercoles.pokerpayout.core.domain.settle.Transfer
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import org.junit.jupiter.api.Test

/**
 * The settle-up (1.4: [MinimumPayments], [SettleUpUseCase]) as properties. MinimumPaymentsTest checks
 * the fewest payments against a brute force; these check that the payments depend on nothing they
 * shouldn't, and that a finished night at the Bank always settles square:
 *
 * - the same balances in bigger money settle in the same payments, scaled;
 * - ids are only labels: renumbered parties pay and are paid the same;
 * - a party already square changes nobody's payments;
 * - any finished Bank night ([BankNight]), with any mix of entries ticked and winners paid, settles in
 *   payments that leave every party square, never more than the greedy pass needs.
 */
class SettleUpPropertiesTest {

    private val settleUp = SettleUpUseCase()

    /** Balances that add up to 0: each party's amount, the last one squaring the rest. */
    private data class Balances(val amounts: List<Long>) {
        fun map(scale: Long = 1L, ids: (Int) -> Int = { it + 1 }): Map<Int, Long> {
            val all = amounts + -amounts.sum()
            return all.withIndex().associate { (seat, cents) -> ids(seat) to cents * scale }
        }
    }

    @Test
    fun `the same balances in bigger money settle in the same payments, scaled`() =
        forAll(seed = 2026_1008_51L, iterations = 2_000, gen = Arb.bind(balances, scales, ::Pair)) { (b, scale) ->
            val small = MinimumPayments.of(b.map())
            val big = MinimumPayments.of(b.map(scale))
            expect(big == small.map { it.copy(amountCents = it.amountCents * scale) }) { "x$scale: $big, not $small scaled" }
        }

    @Test
    fun `ids are only labels`() =
        forAll(seed = 2026_1008_52L, iterations = 2_000, gen = Arb.bind(balances, offsets, ::Pair)) { (b, offset) ->
            // New ids, the other way round: the first seat gets the biggest
            val count = b.amounts.size + 1
            val renumber = { seat: Int -> offset + (count - seat) * 7 }
            val plain = MinimumPayments.of(b.map())
            val renumbered = MinimumPayments.of(b.map(ids = renumber))
            val expected = plain.map { it.copy(fromId = renumber(it.fromId - 1), toId = renumber(it.toId - 1)) }
            expect(renumbered == expected) { "renumbered: $renumbered, expected $expected" }
        }

    @Test
    fun `a party already square changes nobody's payments`() =
        forAll(seed = 2026_1008_53L, iterations = 2_000, gen = balances) { b ->
            val plain = MinimumPayments.of(b.map())
            val withSquare = MinimumPayments.of(b.map() + (SQUARE_ID to 0L))
            expect(withSquare == plain) { "with a square party: $withSquare, without: $plain" }
        }

    @Test
    fun `any finished night settles square, in no more payments than the greedy pass`() =
        forAll(seed = 2026_1008_54L, iterations = 1_000, gen = nightsAndFlags) { (script, flags) ->
            val night = BankNight(script).apply {
                play()
                finish()
                // Some entries ticked, some winners paid: one bit of [flags] each
                players.replaceAll { id, player ->
                    player.copy(boughtIn = flags.bit(2 * id), paidOut = flags.bit(2 * id + 1))
                }
            }
            val result = settleUp(night.settlement(), night.players.values.toList(), script.money)
            expect(result != null) { "a finished night has no settle-up" }
            val up = result as SettleUp
            expect(up.balancesCents.values.sum() == 0L) { "balances ${up.balancesCents} don't add up to 0" }
            expect(squares(up.balancesCents, up.transfers)) { "${up.transfers} don't square ${up.balancesCents}" }
            val greedy = MinimumPayments.greedy(up.balancesCents).size
            expect(up.transfers.size <= greedy) { "${up.transfers.size} payments, the greedy pass needs $greedy" }
        }

    /** Every payment goes from someone who owes to someone owed, and afterwards everyone is at 0. */
    private fun squares(balances: Map<Int, Long>, transfers: List<Transfer>): Boolean {
        val left = balances.toMutableMap()
        val direction = transfers.all { transfer ->
            (balances[transfer.fromId] ?: 0L) < 0L && (balances[transfer.toId] ?: 0L) > 0L && transfer.amountCents > 0L
        }
        transfers.forEach { transfer ->
            left[transfer.fromId] = left.getValue(transfer.fromId) + transfer.amountCents
            left[transfer.toId] = left.getValue(transfer.toId) - transfer.amountCents
        }
        return direction && left.values.all { it == 0L }
    }

    private fun Long.bit(index: Int): Boolean = (this ushr (index % Long.SIZE_BITS)) and 1L == 1L

    private companion object {
        const val SQUARE_ID = 999

        /** 2 to 14 parties, the last squaring the rest: the exact search's range (up to 11) and the greedy pass's beyond. */
        val balances = Arb.list(Arb.long(-50_000L..50_000L), 1..13).map(::Balances)
        val scales = Arb.long(2L..1_000L)
        val offsets = Arb.int(1..1_000)
        val nightsAndFlags = Arb.bind(BankNight.scripts(), Arb.long(0L..Long.MAX_VALUE), ::Pair)
    }
}
