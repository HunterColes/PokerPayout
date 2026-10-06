package com.huntercoles.pokerpayout.core.domain.cash

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cash game's settle-up (PP-029): the greedy pass, its guarantees over random ledgers, the chip
 * check, and splitting a difference.
 */
class SettleCashUseCaseTest {

    private val settle = SettleCashUseCase()

    private fun player(id: Int, vararg buyInsDollars: Int, out: Int?) = CashPlayer(
        id = id,
        name = "P$id",
        buyInsCents = buyInsDollars.map { it * 100L },
        cashOutCents = out?.let { it * 100L },
    )

    private fun settled(ledger: CashLedger, split: Boolean = false) =
        assertIs<CashSettlement.Settled>(settle(ledger, splitDifference = split))

    private fun CashTransfer.text() = "$fromId pays $toId ${amountCents / 100}"

    // The mockup's night (S13) ---------------------------------------------------------------------

    /** S13: Dana +72, Priya +28, Jo +12, Sam −20, Marcus −45, Theo −47. */
    private val s13 = CashLedger(
        listOf(
            player(DANA, 40, out = 112),
            player(PRIYA, 20, out = 48),
            player(JO, 20, 20, out = 52),
            player(SAM, 20, out = 0),
            player(MARCUS, 40, 20, out = 15),
            player(THEO, 40, 20, 20, out = 33),
        )
    )

    @Test
    fun `the mockup's night settles in the five payments it shows`() {
        assertEquals(ChipCheck.Balanced, s13.chipCheck)
        assertEquals(26_000L, s13.cashInCents)
        val result = settled(s13)
        assertEquals(
            mapOf(DANA to 7_200L, PRIYA to 2_800L, JO to 1_200L, SAM to -2_000L, MARCUS to -4_500L, THEO to -4_700L),
            result.netsCents,
        )
        // Debts largest first (Theo, Marcus, Sam) against credits largest first (Dana, Priya, Jo):
        // Theo's 47 to Dana, Marcus finishes Dana's 72 and pays Priya the rest, Sam finishes Priya
        // and pays Jo. Listed payer by payer, each payer's largest payment first.
        assertEquals(
            listOf(
                "$THEO pays $DANA 47",
                "$MARCUS pays $DANA 25",
                "$MARCUS pays $PRIYA 20",
                "$SAM pays $JO 12",
                "$SAM pays $PRIYA 8",
            ),
            result.transfers.map { it.text() },
        )
        assertEquals(0L, result.splitCents)
    }

    // Edge cases --------------------------------------------------------------------------------------

    @Test
    fun `everyone even pays nobody`() {
        val ledger = CashLedger(listOf(player(1, 20, out = 20), player(2, 40, 10, out = 50), player(3, 5, out = 5)))
        val result = settled(ledger)
        assertEquals(emptyList(), result.transfers)
        assertTrue(result.netsCents.values.all { it == 0L })
    }

    @Test
    fun `one winner is paid by every loser, once each`() {
        val ledger = CashLedger(
            listOf(player(1, 20, out = 0), player(2, 20, out = 5), player(3, 20, out = 95), player(4, 40, out = 0))
        )
        assertEquals(listOf("4 pays 3 40", "1 pays 3 20", "2 pays 3 15"), settled(ledger).transfers.map { it.text() })
    }

    @Test
    fun `one loser pays every winner`() {
        val ledger = CashLedger(listOf(player(1, 100, out = 0), player(2, 20, out = 50), player(3, 20, out = 90)))
        assertEquals(listOf("1 pays 3 70", "1 pays 2 30"), settled(ledger).transfers.map { it.text() })
    }

    @Test
    fun `amounts keep their cents`() {
        val ledger = CashLedger(
            listOf(
                CashPlayer(1, "A", listOf(2_050L), cashOutCents = 3_333L),
                CashPlayer(2, "B", listOf(1_999L), cashOutCents = 1L),
                CashPlayer(3, "C", listOf(1L, 1L), cashOutCents = 717L),
            )
        )
        assertEquals(ChipCheck.Balanced, ledger.chipCheck)
        assertEquals(
            listOf(CashTransfer(2, 1, 1_283L), CashTransfer(2, 3, 715L)),
            settled(ledger).transfers,
        )
    }

    @Test
    fun `nobody in yet, and chips still to count`() {
        assertEquals(CashSettlement.Empty, settle(CashLedger()))
        val counting = CashLedger(listOf(player(1, 20, out = 25), player(2, 20, out = null), player(3, 20, out = null)))
        assertEquals(ChipCheck.Counting(2), counting.chipCheck)
        assertEquals(CashSettlement.Counting(2), settle(counting, splitDifference = true))
    }

    @Test
    fun `greedy is at most n minus 1 payments, not always the fewest`() {
        // +5 +4 −4 −3 −2 could settle in 3 (the 4 pays the 4; the 3 and the 2 pay the 5); greedy
        // pays the 4 to the 5 first and needs 4. Still within the n − 1 bound.
        val nets = linkedMapOf(1 to 500L, 2 to 400L, 3 to -400L, 4 to -300L, 5 to -200L)
        val payments = settle.transfers(nets)
        assertEquals(listOf("3 pays 1 4", "4 pays 2 2", "4 pays 1 1", "5 pays 2 2"), payments.map { it.text() })
        assertTrue(payments.size <= nets.size - 1)
    }

    @Test
    fun `results that don't add up to zero are refused`() {
        assertThrows<IllegalArgumentException> { settle.transfers(mapOf(1 to 100L, 2 to -99L)) }
    }

    // The chip check and the split ---------------------------------------------------------------------

    @Test
    fun `an unbalanced count waits until it is recounted or split`() {
        // $5 more counted than went in
        val off = CashLedger(s13.players.map { if (it.id == DANA) it.copy(cashOutCents = 11_700L) else it })
        assertEquals(ChipCheck.Off(500L), off.chipCheck)
        assertEquals(CashSettlement.Unbalanced(500L, canSplit = true), settle(off))

        // Recounted: settles as before
        assertEquals(settled(s13).transfers, settled(off.copy(players = s13.players)).transfers)

        // Split: the $5 comes off the stacks in proportion; Sam's $0 stays $0
        val split = settled(off, split = true)
        assertEquals(500L, split.splitCents)
        assertEquals(0L, split.netsCents.values.sum())
        assertEquals(-500L, split.adjustmentsCents.values.sum())
        assertEquals(0L, split.adjustmentFor(SAM))
        assertTrue(split.adjustmentsCents.values.all { it <= 0L })
        // Every stack loses the same share, 5 / 265, to the cent: Dana's $117 loses $2.21
        assertEquals(-221L, split.adjustmentFor(DANA))
        assertTrue(split.transfers.size <= off.players.size - 1)
    }

    @Test
    fun `missing chips split the other way, adding to the stacks`() {
        val missing = CashLedger(listOf(player(1, 50, out = 0), player(2, 50, out = 30), player(3, 50, out = 110)))
        assertEquals(ChipCheck.Off(-1_000L), missing.chipCheck)
        val split = settled(missing, split = true)
        // $150 over $140 of chips: 30 -> 32.14, 110 -> 117.86 (the leftover cent to the larger remainder)
        assertEquals(mapOf(1 to 0L, 2 to 214L, 3 to 786L), split.adjustmentsCents)
        assertEquals(mapOf(1 to -5_000L, 2 to -1_786L, 3 to 6_786L), split.netsCents)
    }

    @Test
    fun `with no chips counted at all there is nothing to split over`() {
        val nothing = CashLedger(listOf(player(1, 20, out = 0), player(2, 20, out = 0)))
        assertEquals(CashSettlement.Unbalanced(-4_000L, canSplit = false), settle(nothing, splitDifference = true))
    }

    @Test
    fun `scaling rounds to the cent and adds up exactly`() {
        // $10 in, three equal stacks counted at $3.34: 333.33 each; the leftover cent goes to the first
        val players = (1..3).map { CashPlayer(it, "P$it", listOf(333L), cashOutCents = 334L) }
        assertEquals(mapOf(1 to 334L, 2 to 333L, 3 to 333L), settle.scaleCashOuts(players, 1_000L))
        assertNull(settle.scaleCashOuts(listOf(CashPlayer(1, "A", listOf(100L), 0L)), 100L))
    }

    // Properties over seeded random ledgers ------------------------------------------------------------

    @Test
    fun `random balanced ledgers settle every result to zero in at most n minus 1 payments`() {
        val random = Random(SEED)
        repeat(RUNS) { run ->
            val ledger = randomLedger(random, balanced = true)
            val result = settled(ledger)
            checkSettlement("run $run", ledger, result)
            // Same ledger, same payments
            assertEquals(result, settle(ledger))
        }
    }

    @Test
    fun `random unbalanced ledgers split into exact cents, then settle the same way`() {
        val random = Random(SEED + 1)
        repeat(RUNS) { run ->
            val ledger = randomLedger(random, balanced = false)
            val check = ledger.chipCheck
            if (check !is ChipCheck.Off) return@repeat
            assertIs<CashSettlement.Unbalanced>(settle(ledger))
            val result = settle(ledger, splitDifference = true)
            if (ledger.countedOutCents == 0L) {
                assertEquals(CashSettlement.Unbalanced(check.differenceCents, canSplit = false), result)
                return@repeat
            }
            assertIs<CashSettlement.Settled>(result)
            assertEquals(check.differenceCents, result.splitCents)
            assertEquals(-check.differenceCents, result.adjustmentsCents.values.sum(), "run $run")
            checkScaled("run $run", ledger)
            checkSettlement("run $run", ledger, result)
        }
    }

    private fun checkSettlement(what: String, ledger: CashLedger, result: CashSettlement.Settled) {
        val nets = result.netsCents
        assertEquals(ledger.players.map { it.id }, nets.keys.toList(), what)
        assertEquals(0L, nets.values.sum(), what)
        val received = mutableMapOf<Int, Long>()
        val paid = mutableMapOf<Int, Long>()
        result.transfers.forEach { transfer ->
            assertTrue(transfer.amountCents > 0L, "$what: $transfer")
            assertTrue(nets.getValue(transfer.fromId) < 0L, "$what: only players down pay ($transfer)")
            assertTrue(nets.getValue(transfer.toId) > 0L, "$what: only players up are paid ($transfer)")
            received.merge(transfer.toId, transfer.amountCents, Long::plus)
            paid.merge(transfer.fromId, transfer.amountCents, Long::plus)
            // Never paid more than owed, at any point
            assertTrue(received.getValue(transfer.toId) <= nets.getValue(transfer.toId), "$what: $transfer overpays")
            assertTrue(paid.getValue(transfer.fromId) <= -nets.getValue(transfer.fromId), "$what: $transfer overcharges")
        }
        nets.forEach { (id, net) ->
            assertEquals(net, (received[id] ?: 0L) - (paid[id] ?: 0L), "$what: player $id isn't settled to zero")
        }
        val notEven = nets.values.count { it != 0L }
        assertTrue(result.transfers.size <= (notEven - 1).coerceAtLeast(0), "$what: ${result.transfers.size} payments")
        assertTrue(result.transfers.size <= ledger.players.size - 1, what)
    }

    private fun checkScaled(what: String, ledger: CashLedger) {
        val scaled = requireNotNull(settle.scaleCashOuts(ledger.players, ledger.cashInCents))
        assertEquals(ledger.cashInCents, scaled.values.sum(), what)
        ledger.players.forEach { player ->
            val out = player.cashOutCents ?: 0L
            // Within a cent of the exact share
            val exact = (out.toBigDecimal() * ledger.cashInCents.toBigDecimal())
                .divide(ledger.countedOutCents.toBigDecimal(), EXACT_DIGITS, RoundingMode.HALF_EVEN)
            val share = scaled.getValue(player.id).toBigDecimal()
            assertTrue((share - exact).abs() < java.math.BigDecimal.ONE, "$what: ${player.id} gets $share for $exact")
            if (out == 0L) assertEquals(0L, scaled.getValue(player.id), "$what: an empty stack stays empty")
        }
        // A bigger stack never ends up with less (equal stacks may differ by the leftover cent)
        val byStack = ledger.players.groupBy { it.cashOutCents ?: 0L }.toSortedMap().values
            .map { stacks -> stacks.map { scaled.getValue(it.id) } }
        byStack.zipWithNext { smaller, bigger -> assertTrue(smaller.max() <= bigger.min(), "$what: $smaller vs $bigger") }
    }

    /**
     * 2 to 12 players, each with 1 to 4 buy-ins of any whole number of cents up to $200 (common
     * round amounts more often), and cash-outs that split the money in at random cut points, with
     * some players busted. Unbalanced ledgers then nudge the counted total up or down.
     */
    private fun randomLedger(random: Random, balanced: Boolean): CashLedger {
        val count = random.nextInt(2, MAX_PLAYERS + 1)
        val buyIns = List(count) {
            List(random.nextInt(1, 5)) {
                if (random.nextBoolean()) ROUND_AMOUNTS.random(random) else random.nextLong(1L, 20_001L)
            }
        }
        val cashIn = buyIns.sumOf { it.sum() }
        val counted = if (balanced) cashIn else (cashIn + random.nextLong(-cashIn, cashIn / 4 + 2)).coerceAtLeast(0L)
        val cuts = (List(count - 1) { random.nextLong(0L, counted + 1) } + 0L + counted).sorted()
        val outs = cuts.zipWithNext { a, b -> b - a }.shuffled(random)
        return CashLedger(List(count) { index -> CashPlayer(index + 1, "P${index + 1}", buyIns[index], outs[index]) })
    }

    private companion object {
        const val DANA = 1
        const val PRIYA = 2
        const val JO = 3
        const val SAM = 4
        const val MARCUS = 5
        const val THEO = 6
        const val SEED = 20_261_006L
        const val RUNS = 3_000
        const val MAX_PLAYERS = 12
        const val EXACT_DIGITS = 6
        val ROUND_AMOUNTS = listOf(500L, 1_000L, 2_000L, 2_500L, 4_000L, 5_000L, 10_000L)
    }
}
