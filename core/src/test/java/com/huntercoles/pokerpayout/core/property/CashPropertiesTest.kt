package com.huntercoles.pokerpayout.core.property

import com.huntercoles.pokerpayout.core.domain.cash.CashLedger
import com.huntercoles.pokerpayout.core.domain.cash.CashPlayer
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer
import com.huntercoles.pokerpayout.core.domain.cash.SettleCashUseCase
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import org.junit.jupiter.api.Test

/**
 * The cash game's settle-up ([SettleCashUseCase]) as metamorphic properties: SettleCashUseCaseTest
 * already checks every payment against the results over random ledgers; these check that the
 * payments don't depend on anything they shouldn't. The same night in bigger money settles in the
 * same payments, scaled; players' numbers are only labels; and a player who broke even changes
 * nobody's payments.
 */
class CashPropertiesTest {

    private val settle = SettleCashUseCase()

    /**
     * A balanced night: each player's buy-ins, and the cash-outs cut from the money in at [cuts]
     * (so the chip check balances), handed out in [order].
     */
    private data class Night(val buyIns: List<List<Long>>, val cuts: List<Long>, val order: Int) {
        fun ledger(scale: Long = 1L, ids: (Int) -> Int = { it + 1 }): CashLedger {
            val cashIn = buyIns.sumOf { it.sum() }
            val points = (cuts.map { it.mod(cashIn + 1) } + 0L + cashIn).sorted()
            val cutUp = points.zipWithNext { a, b -> b - a }
            val outs = cutUp.drop(order.mod(cutUp.size)) + cutUp.take(order.mod(cutUp.size))
            return CashLedger(
                buyIns.mapIndexed { seat, cents ->
                    CashPlayer(ids(seat), "P${ids(seat)}", cents.map { it * scale }, outs[seat] * scale)
                },
            )
        }
    }

    private fun transfers(ledger: CashLedger): List<CashTransfer> {
        val result = settle(ledger)
        expect(result is CashSettlement.Settled) { "a balanced ledger didn't settle: $result" }
        return (result as CashSettlement.Settled).transfers
    }

    @Test
    fun `the same night in bigger money settles in the same payments, scaled`() =
        forAll(seed = 2026_1008_51L, iterations = 2_000, gen = Arb.bind(nights, Arb.long(2L..1_000L), ::Pair)) { (night, scale) ->
            val small = transfers(night.ledger())
            val big = transfers(night.ledger(scale))
            expect(big == small.map { it.copy(amountCents = it.amountCents * scale) }) { "x$scale: $big, expected $small scaled" }
        }

    @Test
    fun `players' numbers are only labels`() =
        forAll(seed = 2026_1008_52L, iterations = 2_000, gen = Arb.bind(nights, Arb.int(1..1_000), ::Pair)) { (night, offset) ->
            // New numbers, the other way round: seat 1 gets the biggest
            val count = night.buyIns.size
            val renumber = { seat: Int -> offset + (count - seat) * 7 }
            val plain = transfers(night.ledger())
            val renumbered = transfers(night.ledger(ids = renumber))
            val expected = plain.map { it.copy(fromId = renumber(it.fromId - 1), toId = renumber(it.toId - 1)) }
            expect(renumbered == expected) { "renumbered: $renumbered, expected $expected" }
        }

    @Test
    fun `a player who broke even changes nobody's payments`() =
        forAll(seed = 2026_1008_53L, iterations = 2_000, gen = Arb.bind(nights, stakes, ::Pair)) { (night, stake) ->
            val ledger = night.ledger()
            val even = CashPlayer(id = EVEN_ID, name = "Even", buyInsCents = listOf(stake), cashOutCents = stake)
            val withEven = CashLedger(ledger.players + even)
            val plain = transfers(ledger)
            val after = transfers(withEven)
            expect(after == plain) { "with an even player: $after, without: $plain" }
        }

    private companion object {
        const val EVEN_ID = 999
        val buyIn = Arb.long(1L..20_000L)
        val stakes = Arb.long(1L..50_000L)
        val nights = Arb.bind(
            Arb.list(Arb.list(buyIn, 1..4), 2..12),
            Arb.list(Arb.long(0L..Long.MAX_VALUE / 2), 11..11),
            Arb.int(0..11),
        ) { buyIns, cuts, order -> Night(buyIns, cuts.take(buyIns.size - 1), order) }
    }
}
