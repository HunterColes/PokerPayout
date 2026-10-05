package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The one payout calculation shared by the Tournament and Bank tabs. Amounts are in cents. */
class CalculatePayoutsUseCaseTest {

    private val useCase = CalculatePayoutsUseCase()

    private fun amounts(pool: Long, weights: List<Int>, players: Int, rounding: PayoutRounding = PayoutRounding.ONE_DOLLAR) =
        useCase(pool, weights, players, rounding).places.map { it.amountCents }

    @Test
    fun `default weights for six players pay two places`() {
        // 6 x $20 = $120 split 35:20 -> 2nd 43.64 -> $44, 1st gets the rest
        val table = useCase(12_000, PayoutPreset.STANDARD.weightsFor(PayoutPlaces.recommended(6)), 6)
        assertEquals(listOf(1, 2), table.places.map { it.place })
        assertEquals(listOf(7_600L, 4_400L), table.places.map { it.amountCents })
    }

    @Test
    fun `truncated default weights pay exactly that many places`() {
        val weights = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.take(6)
        val table = useCase(18 * 2_000L, weights, 18)
        assertEquals(weights, table.places.map { it.weight })
        assertEquals(18 * 2_000L, table.totalCents)
    }

    @Test
    fun `custom weights within the player count are all paid`() {
        // $120, 40/30/20/10: exact dollars, nothing to round
        assertEquals(listOf(4_800L, 3_600L, 2_400L, 1_200L), amounts(12_000, listOf(40, 30, 20, 10), 6))
    }

    @Test
    fun `shares are the structure's percentages`() {
        val table = useCase(30_000, listOf(50, 30, 20), 3)
        assertEquals(listOf(15_000L, 9_000L, 6_000L), table.places.map { it.amountCents })
        assertEquals(listOf(50.0, 30.0, 20.0), table.places.map { it.sharePercent })
        assertEquals(listOf("1st", "2nd", "3rd"), table.places.map { it.ordinal })
    }

    @Test
    fun `never pays more places than there are players`() {
        // PP-016: 3 players with 6 custom weights used to pay 6 places, so 35% of the pool went
        // to places nobody could finish in.
        val table = useCase(6_000, listOf(40, 25, 15, 10, 6, 4), 3)

        assertEquals(3, table.places.size)
        assertEquals(6_000L, table.totalCents)
        // 40:25:15 of $60 -> 2nd 18.75 -> $19, 3rd 11.25 -> $11, 1st $30
        assertEquals(listOf(3_000L, 1_900L, 1_100L), table.places.map { it.amountCents })
    }

    @Test
    fun `worked example - remainder cents go to 1st`() {
        // 7 x $13 = $91, 35:20 -> 2nd 33.09 -> $33, 1st $58
        assertEquals(listOf(5_800L, 3_300L), amounts(9_100, listOf(35, 20), 7))
        // $100.50 pool, 2:1 -> 2nd 33.50 -> $34 (half up), 1st $66.50 keeps the odd 50 cents
        assertEquals(listOf(6_650L, 3_400L), amounts(10_050, listOf(2, 1), 4))
    }

    @Test
    fun `worked example - $5 rounding rounds lower places to the nearest $5`() {
        // $230, 50/30/20 -> 2nd 69.00 -> $70, 3rd 46.00 -> $45, 1st $115
        assertEquals(listOf(11_500L, 7_000L, 4_500L), amounts(23_000, listOf(50, 30, 20), 9, PayoutRounding.FIVE_DOLLARS))
    }

    @Test
    fun `worked example - $10 rounding of the standard 9-place table`() {
        // 30 x $30 = $900, 35/20/15/10/8/6/3/2/1
        val table = amounts(90_000, TournamentConstants.DEFAULT_PAYOUT_WEIGHTS, 30, PayoutRounding.TEN_DOLLARS)
        assertEquals(listOf(31_000L, 18_000L, 14_000L, 9_000L, 7_000L, 5_000L, 3_000L, 2_000L, 1_000L), table)
        assertEquals(90_000L, table.sum())
    }

    @Test
    fun `rounding up the lower places never leaves 1st with less than 2nd`() {
        // $60, four equal places, $10 units: nearest would pay 20/20/20 and leave 1st with $0.
        // The lower places are rounded down instead: 10/10/10, 1st $30.
        assertEquals(listOf(3_000L, 1_000L, 1_000L, 1_000L), amounts(6_000, listOf(1, 1, 1, 1), 4, PayoutRounding.TEN_DOLLARS))
    }

    @Test
    fun `no weights, no players or no money`() {
        assertEquals(emptyList(), amounts(10_000, emptyList(), 5))
        assertEquals(emptyList(), amounts(10_000, listOf(3, 2, 1), 0))
        assertEquals(listOf(0L, 0L), amounts(0, listOf(3, 2), 5))
        assertEquals(listOf(0L), amounts(-500, listOf(1), 5))
    }

    /**
     * Seeded property sweep: for any pool, player count, structure and rounding the table adds up
     * to the pool exactly, pays at most min(players, 9) places, never pays a negative amount, pays
     * lower places in whole units, and pays more to better places when the weights say so.
     */
    @Test
    fun `payout tables always add up to the prize pool`() {
        val random = Random(20261004)
        repeat(20_000) { iteration ->
            val players = random.nextInt(1, 31)
            val pool = when (random.nextInt(4)) {
                0 -> random.nextLong(0, 1_000L) // pocket change
                1 -> players * random.nextLong(1, 200L) * 100L // whole-dollar buy-ins
                else -> random.nextLong(0, 10_000_000L) // anything, cents included
            }
            val weights = when (random.nextInt(3)) {
                0 -> PayoutPreset.entries.random(random).weightsFor(random.nextInt(1, 10))
                1 -> List(random.nextInt(1, 12)) { random.nextInt(1, 1_000) }.sortedDescending()
                else -> List(random.nextInt(0, 12)) { random.nextInt(-5, 1_000) } // unsorted, junk
            }
            val rounding = PayoutRounding.entries.random(random)
            val table = useCase(pool, weights, players, rounding)
            val context = "#$iteration pool=$pool players=$players weights=$weights $rounding"

            assertEquals(pool, table.prizePoolCents, context)
            if (table.places.isNotEmpty()) assertEquals(pool, table.totalCents, context)
            assertTrue(table.places.size <= minOf(players, PayoutPlaces.MAX), context)
            assertTrue(table.places.all { it.amountCents >= 0 }, context)
            assertTrue(table.places.drop(1).all { it.amountCents % rounding.unitCents == 0L }, context)
            val positive = weights.filter { it > 0 }
            if (positive == positive.sortedDescending()) {
                assertTrue(table.places.zipWithNext().all { (a, b) -> a.amountCents >= b.amountCents }, context)
            }
        }
    }
}
