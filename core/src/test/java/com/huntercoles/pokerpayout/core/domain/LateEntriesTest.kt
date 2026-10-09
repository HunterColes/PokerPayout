package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.domain.history.NightResults
import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.EntryPrice
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PoolBreakdown
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import com.huntercoles.pokerpayout.core.domain.settle.SettleUpUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Late entries and re-entries (PP-116) in the one settlement, by example: a late entry pays the
 * price it was bought at, a re-entry is an entry of its own whose earlier bust stays, and the
 * settle-up and History add a player's entries together. EntriesPropertiesTest throws random nights
 * at the same rules.
 */
class LateEntriesTest {

    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())

    /** $20 buy-in, $5 food, $5 bounty: $30 to sit down. */
    private val money = MoneySettings(buyInCents = 2_000L, foodCents = 500L, bountyCents = 500L, rebuyCents = 0L, addOnCents = 0L)

    @Test
    fun `a late entry pays the price it was bought at, in the pool and in what it cost`() {
        // Player 5 joined late when the buy-in was $15 and the bounty $7; the amounts changed since
        val late = EntryPrice(buyInCents = 1_500L, foodCents = 500L, bountyCents = 700L)
        val players = (1..4).map { BankPlayer(it, boughtIn = true) } + BankPlayer(5, boughtIn = true, entryPrice = late)
        val result = settle(players, emptyList(), money, listOf(1), PayoutRounding.ONE_DOLLAR)

        assertEquals(
            PoolBreakdown(buyInCents = 9_500L, foodCents = 2_500L, bountyCents = 2_700L, rebuyCents = 0L, addOnCents = 0L),
            result.pool,
        )
        assertEquals(2_700L, result.forPlayer(5)?.costCents)
        assertEquals(3_000L, result.forPlayer(1)?.costCents)
        assertEquals(4 * 3_000L + 2_700L, result.paidInCents)
        // Its bounty is its own: knocking it out pays $7
        assertEquals(700L, result.forPlayer(5)?.headBountyCents)
    }

    @Test
    fun `knocking out a late entry pays its own bounty, and the champion keeps theirs`() {
        val late = EntryPrice(buyInCents = 2_000L, foodCents = 500L, bountyCents = 700L)
        val players = listOf(
            BankPlayer(1),
            BankPlayer(2, eliminatedBy = 1),
            BankPlayer(3, eliminatedBy = 1, entryPrice = late),
        )
        val result = settle(players, listOf(3, 2), money, listOf(1), PayoutRounding.ONE_DOLLAR)
        assertEquals(1_200L, result.forPlayer(1)?.knockoutBountyCents)
        assertEquals(500L, result.forPlayer(1)?.kingsBountyCents)
        assertEquals(result.pool.payableCents, result.assignedCents)
    }

    /**
     * Four players. Theo (4) is knocked out by Dana (1) and re-enters as entry 5; Priya (3) goes out,
     * then Theo's second entry (by Dana again), then Marcus (2). Five entries: Theo's first finishes
     * 5th, Priya 4th, his second 3rd, Marcus 2nd, Dana wins with both of Theo's bounties.
     */
    @Test
    fun `a re-entry is an entry of its own, and the bust before it keeps its place and knockout`() {
        val players = listOf(
            BankPlayer(1, boughtIn = true),
            BankPlayer(2, boughtIn = true),
            BankPlayer(3, boughtIn = true),
            BankPlayer(4, boughtIn = true, eliminatedBy = 1),
            BankPlayer(5, boughtIn = true, eliminatedBy = 1, entryPrice = EntryPrice.of(money), reEntryOf = 4),
        )
        val result = settle(players, listOf(4, 3, 5, 2), money, listOf(50, 30, 20), PayoutRounding.ONE_DOLLAR)

        assertEquals(mapOf(4 to 5, 3 to 4, 5 to 3, 2 to 2, 1 to 1), result.standings.placeByPlayer)
        assertEquals(10_000L, result.pool.prizePoolCents)
        assertEquals(2, result.forPlayer(1)?.knockouts)
        assertEquals(1_000L, result.forPlayer(1)?.knockoutBountyCents)
        // Theo's second entry finished 3rd: $20 of the $100
        assertEquals(2_000L, result.forPlayer(5)?.prizeCents)
        assertEquals(result.pool.payableCents, result.assignedCents)
    }

    @Test
    fun `a progressive re-entry starts with a fresh bounty`() {
        val pko = money.copy(bountyMode = BountyMode.PROGRESSIVE)
        val players = listOf(
            BankPlayer(1),
            BankPlayer(2, eliminatedBy = 1),
            BankPlayer(3, entryPrice = EntryPrice.of(pko), reEntryOf = 2),
        )
        // Marcus (2) out by Dana: Dana takes $2.50 and her bounty grows to $7.50; Marcus re-enters with $5 on his head
        val result = settle(players, listOf(2), pko, listOf(1), PayoutRounding.ONE_DOLLAR)
        assertEquals(750L, result.forPlayer(1)?.headBountyCents)
        assertEquals(500L, result.forPlayer(3)?.headBountyCents)
        assertEquals(250L, result.forPlayer(1)?.knockoutBountyCents)
    }

    @Test
    fun `a mystery late entry adds its envelope to the ones left`() {
        val mystery = money.copy(bountyMode = BountyMode.MYSTERY)
        val players = (1..9).map { BankPlayer(it) } + BankPlayer(10, entryPrice = EntryPrice.of(mystery))
        val result = settle(players, emptyList(), mystery, listOf(1), PayoutRounding.ONE_DOLLAR)
        // Nine players' deal (1 x $15, 2 x $6, 6 x $3) and the late entry's $5
        assertEquals(listOf(1_500L, 600L, 600L, 500L, 300L, 300L, 300L, 300L, 300L, 300L), result.envelopesLeft)
        assertEquals(result.pool.bountyPoolCents, result.envelopesLeft.sum())
    }

    @Test
    fun `a player who re-entered settles once, for both entries`() {
        val players = listOf(
            BankPlayer(1, boughtIn = true),
            BankPlayer(2, boughtIn = true),
            BankPlayer(3, boughtIn = false, eliminatedBy = 1),
            BankPlayer(4, boughtIn = false, eliminatedBy = 1, entryPrice = EntryPrice.of(money), reEntryOf = 3),
        )
        val result = settle(players, listOf(3, 4, 2), money, listOf(1), PayoutRounding.ONE_DOLLAR)
        val plan = requireNotNull(SettleUpUseCase()(result, players, money))
        // Player 3 owes both entries, $60, under their first entry's id
        assertEquals(setOf(1, 2, 3, SettleUp.BANK_ID), plan.balancesCents.keys)
        assertEquals(-6_000L, plan.balancesCents.getValue(3))
        assertEquals(0L, plan.balancesCents.values.sum())
    }

    @Test
    fun `History lists a player who re-entered once, at their best place, with both entries added`() {
        val players = listOf(
            BankPlayer(1, boughtIn = true, paidOut = true),
            BankPlayer(2, boughtIn = true, paidOut = true, eliminatedBy = 1),
            BankPlayer(3, boughtIn = true, paidOut = true, eliminatedBy = 1),
            BankPlayer(4, boughtIn = true, paidOut = true, eliminatedBy = 2, entryPrice = EntryPrice.of(money), reEntryOf = 3),
        )
        // Theo's first entry out 4th, his second 3rd, Marcus 2nd
        val result = settle(players, listOf(3, 4, 2), money, listOf(70, 30), PayoutRounding.ONE_DOLLAR)
        val lines = requireNotNull(NightResults.of(result, players, mapOf(1 to "Dana", 2 to "Marcus", 3 to "Theo", 4 to "Theo")))

        assertEquals(listOf("Dana" to 1, "Marcus" to 2, "Theo" to 3), lines.map { it.name to it.place })
        val theo = lines.last()
        assertEquals(2 * 3_000L, theo.entryCents)
        assertEquals(0L, theo.prizeCents)
        assertEquals(result.pool.payableCents, lines.sumOf { it.wonCents })
    }
}
