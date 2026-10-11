package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.SettleUpUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import com.huntercoles.pokerpayout.core.utils.AppCurrency
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The house rake (PP-145) by example: the scale, the rounding, what it comes off (the prize pool, never
 * the bounties or the food), and that the payouts plus the rake are what was taken in. Amounts in cents.
 * The properties are in `RakePropertiesTest`.
 */
class HouseRakeTest {

    private val calculate = CalculatePayoutsUseCase()
    private val settle = SettleTournamentUseCase(calculate)

    @Test
    fun `none takes nothing, and is the default everywhere`() {
        assertTrue(HouseRake.NONE.isNone)
        assertEquals(0L, HouseRake.NONE.centsOf(45_000L))
        assertEquals(HouseRake.NONE, MoneySettings.DEFAULT.rake)
        assertEquals(HouseRake.NONE, PoolBreakdown.EMPTY.rake)
        assertEquals(0L, PoolBreakdown.EMPTY.rakeCents)
    }

    @Test
    fun `the rake rounds down to the cent, so the house never takes more than its percentage`() {
        assertEquals(2_000L, HouseRake.of(500).centsOf(40_000L)) // 5% of $400 is $20
        assertEquals(308L, HouseRake.of(250).centsOf(12_345L)) // 2.5% of $123.45 is $3.08625: $3.08
        assertEquals(0L, HouseRake.of(50).centsOf(199L)) // 0.5% of $1.99 is under a cent
        assertEquals(1L, HouseRake.of(50).centsOf(200L)) // 0.5% of $2 is a cent
        assertEquals(2_500L, HouseRake.of(2_500).centsOf(10_000L)) // the most: 25%
    }

    @Test
    fun `in yen, which has no cents, the rake rounds down to the whole yen`() {
        // 2.5% of ¥1,234 is ¥30.85: ¥30 to the house, the 85 sen stay in the pool
        assertEquals(3_000L, HouseRake.of(250).centsOf(123_400L, AppCurrency.YEN))
        assertEquals(3_085L, HouseRake.of(250).centsOf(123_400L, AppCurrency.DOLLAR))
    }

    @Test
    fun `any pool, however large, is raked exactly`() {
        val pool = Long.MAX_VALUE / 2
        val exact = pool.toBigInteger() * 2_500.toBigInteger() / 10_000.toBigInteger()
        assertEquals(exact.toLong(), HouseRake.of(2_500).centsOf(pool))
        assertEquals(0L, HouseRake.of(500).centsOf(-1_000L))
    }

    @Test
    fun `the scale is 0 to 25 percent in half-percent steps, anything else goes onto it`() {
        assertEquals(500, HouseRake.of(500).basisPoints)
        assertEquals(0, HouseRake.of(-50).basisPoints)
        assertEquals(2_500, HouseRake.of(3_000).basisPoints)
        assertEquals(250, HouseRake.of(225).basisPoints) // halves up
        assertEquals(200, HouseRake.of(224).basisPoints)
        assertEquals(50, HouseRake.of(25).basisPoints)
        assertEquals(0, HouseRake.of(24).basisPoints)
        assertEquals(2_500, HouseRake.of(Int.MAX_VALUE).basisPoints)
    }

    @Test
    fun `a typed percentage, either decimal mark, goes onto the scale`() {
        assertEquals(500, HouseRake.parse("5")?.basisPoints)
        assertEquals(250, HouseRake.parse("2.5")?.basisPoints)
        assertEquals(250, HouseRake.parse("2,5")?.basisPoints)
        assertEquals(500, HouseRake.parse(" 5% ")?.basisPoints)
        assertEquals(1_000, HouseRake.parse("10.")?.basisPoints)
        assertEquals(50, HouseRake.parse(".5")?.basisPoints)
        assertEquals(0, HouseRake.parse("")?.basisPoints)
        assertEquals(2_500, HouseRake.parse("30")?.basisPoints)
        assertEquals(250, HouseRake.parse("2.3")?.basisPoints)
        assertEquals(250, HouseRake.parse("2.25")?.basisPoints)
        assertNull(HouseRake.parse("."))
        assertNull(HouseRake.parse("five"))
        assertNull(HouseRake.parse("1.2.3"))
        assertNull(HouseRake.parse("-5"))
    }

    @Test
    fun `the field lets through what could become a percentage`() {
        listOf("", "2", "25", "2.", "2,5", "2.25", ".5", "100").forEach { assertTrue(HouseRake.isTypable(it), it) }
        listOf("1000", "2.255", "2.5.", "2,5,", "a", "-1", "5%").forEach { assertTrue(!HouseRake.isTypable(it), it) }
    }

    @Test
    fun `it reads as a percentage`() {
        assertEquals("5%", HouseRake.of(500).label)
        assertEquals("2.5%", HouseRake.of(250).label)
        assertEquals("0%", HouseRake.NONE.label)
        assertEquals("0", HouseRake.NONE.fieldText())
        assertEquals("25", HouseRake.of(2_500).fieldText())
        assertEquals("2.5", HouseRake.of(250).fieldText())
        assertEquals("2,5", HouseRake.of(250).fieldText(','))
    }

    @Test
    fun `the rake comes off the prize pool only, never the bounties or the food`() {
        // 10 players: $40 buy-in, $5 food, $10 bounty; 2 rebuys and 3 add-ons at $40 and $10; 5% rake
        val money = MoneySettings(4_000L, 500L, 1_000L, 4_000L, 1_000L, rake = HouseRake.of(500))
        val pool = PoolBreakdown.of(money, playerCount = 10, rebuyCount = 2, addOnCount = 3)
        assertEquals(51_000L, pool.prizeInCents) // $400 + $80 + $30
        assertEquals(2_550L, pool.rakeCents) // 5% of $510
        assertEquals(48_450L, pool.prizePoolCents)
        assertEquals(10_000L, pool.bountyPoolCents) // every bounty, whole
        assertEquals(5_000L, pool.foodCents)
        assertEquals(58_450L, pool.payableCents) // prize pool + bounties: what the bank pays back out
        assertEquals(66_000L, pool.totalCents) // everything taken in, unchanged by the rake
        assertEquals(pool.totalCents, pool.prizePoolCents + pool.rakeCents + pool.bountyPoolCents + pool.foodCents)
    }

    @Test
    fun `the places share the pool after the rake, and with the rake add up to what was taken in`() {
        // $450 in for prizes, a 5% rake: $22.50 to the house, $427.50 for the places. Standard 35/20/15 in $5s.
        val pool = PoolBreakdown(45_000L, 0L, 0L, 0L, 0L).withRake(HouseRake.of(500))
        val table = calculate(pool, listOf(35, 20, 15), 9, PayoutRounding.FIVE_DOLLARS)
        assertEquals(listOf(21_750L, 12_000L, 9_000L), table.places.map { it.amountCents })
        assertEquals(42_750L, table.prizePoolCents)
        assertEquals(2_250L, table.rakeCents)
        assertEquals(45_000L, table.totalCents + table.rakeCents)
        assertEquals(45_000L, table.takenInCents)
    }

    @Test
    fun `at 0 percent the table is exactly the one before the rake`() {
        val pool = PoolBreakdown(45_000L, 0L, 0L, 0L, 0L).withRake(HouseRake.NONE)
        val before = calculate(45_000L, listOf(35, 20, 15), 9, PayoutRounding.FIVE_DOLLARS)
        assertEquals(before, calculate(pool, listOf(35, 20, 15), 9, PayoutRounding.FIVE_DOLLARS))
        assertEquals(listOf(22_500L, 13_000L, 9_500L), before.places.map { it.amountCents })
    }

    @Test
    fun `a raked night pays every bounty whole, and the bank keeps the rake with the food`() {
        // 4 players, $20 buy-in, $5 food, $5 bounty, a 10% rake: $80 in for prizes, $8 to the house
        val money = MoneySettings(2_000L, 500L, 500L, 0L, 0L, BountyMode.STANDARD, HouseRake.of(1_000))
        val players = listOf(
            BankPlayer(1, boughtIn = true),
            BankPlayer(2, boughtIn = true, eliminatedBy = 1),
            BankPlayer(3, boughtIn = true, eliminatedBy = 1),
            BankPlayer(4, boughtIn = true, eliminatedBy = 2),
        )
        val settlement = settle(players, listOf(4, 3, 2), money, listOf(65, 35), PayoutRounding.ONE_DOLLAR)
        assertEquals(800L, settlement.pool.rakeCents)
        assertEquals(7_200L, settlement.payoutTable.totalCents) // $72: 2nd $25 (35% of $72, to the dollar), 1st $47
        assertEquals(listOf(4_700L, 2_500L), settlement.payoutTable.places.map { it.amountCents })
        // Bounties: 1 took 3's and 2's and keeps their own; 2 took 4's. All $20 of them, unraked.
        assertEquals(2_000L, settlement.players.sumOf { it.knockoutBountyCents + it.kingsBountyCents + it.unclaimedBountyCents })
        assertEquals(settlement.pool.payableCents, settlement.assignedCents)
        // Everyone paid in ($120); the bank owes the winners $92 and keeps the food ($20) and the rake ($8)
        val settleUp = checkNotNull(SettleUpUseCase()(settlement, players, money))
        assertEquals(0L, settleUp.balancesCents.values.sum())
        assertEquals(12_000L, settlement.paidInCents)
        assertEquals(-9_200L, settleUp.balancesCents.getValue(SettleUp.BANK_ID))
        assertEquals(2_000L + 800L, settlement.paidInCents + settleUp.balancesCents.getValue(SettleUp.BANK_ID))
    }
}
