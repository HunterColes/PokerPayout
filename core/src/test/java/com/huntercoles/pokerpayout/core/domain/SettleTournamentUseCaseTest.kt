package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Who is owed what, and the money-conservation invariant over random tournaments. */
class SettleTournamentUseCaseTest {

    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())

    private fun money(buyIn: Long, food: Long = 0, bounty: Long = 0, rebuy: Long = 0, addOn: Long = 0) =
        MoneySettings(buyInCents = buyIn, foodCents = food, bountyCents = bounty, rebuyCents = rebuy, addOnCents = addOn)

    @Test
    fun `places, knockout bounties and the champion's bounties`() {
        // 4 players, $100 buy-in, $10 bounty, 3:2:1. Player 1 knocks out 4, player 2 knocks out 1,
        // player 3 goes out with nobody credited.
        val players = listOf(
            BankPlayer(1, eliminatedBy = 2),
            BankPlayer(2),
            BankPlayer(3),
            BankPlayer(4, eliminatedBy = 1)
        )
        val result = settle(players, listOf(4, 1, 3), money(10_000, bounty = 1_000), listOf(3, 2, 1), PayoutRounding.ONE_DOLLAR)

        assertEquals(2, result.championId)
        val byId = result.players.associateBy { it.playerId }
        // $400 pool: 2nd 133.33 -> $133, 3rd 66.67 -> $67, 1st $200
        // Out first to last: 4 (4th), 1 (3rd), 3 (2nd); player 2 is the champion
        assertEquals(listOf(20_000L, 6_700L, 13_300L, 0L), listOf(2, 1, 3, 4).map { byId.getValue(it).prizeCents })
        assertEquals(1, byId.getValue(1).knockouts)
        assertEquals(1_000L, byId.getValue(1).knockoutBountyCents)
        assertEquals(1_000L, byId.getValue(2).knockoutBountyCents)
        assertEquals(1_000L, byId.getValue(2).kingsBountyCents)
        // Player 3's bounty had no taker; the champion collects it
        assertEquals(1_000L, byId.getValue(2).unclaimedBountyCents)
        assertEquals(23_000L, byId.getValue(2).winningsCents)
        assertEquals(44_000L, result.assignedCents) // the whole $400 prize pool + $40 bounty pool
        assertEquals(result.pool.payableCents, result.assignedCents)
    }

    @Test
    fun `net pay is winnings minus everything paid in, and sums to minus the food pool`() {
        val players = listOf(
            BankPlayer(1, boughtIn = true, rebuys = 1),
            BankPlayer(2, boughtIn = true, addOns = 1, eliminatedBy = 1),
            BankPlayer(3, boughtIn = true, eliminatedBy = 1),
            BankPlayer(4, boughtIn = true, eliminatedBy = 1)
        )
        val result = settle(
            players, listOf(4, 3, 2),
            money(10_000, food = 2_000, bounty = 1_000, rebuy = 5_000, addOn = 2_500),
            listOf(3, 2, 1), PayoutRounding.ONE_DOLLAR
        )
        // $475 prize pool: 2nd 158.33 -> $158, 3rd 79.17 -> $79, 1st $238
        // P1: 238 + 3 KOs x 10 + 10 king's bounty - (130 + 50) = 98
        assertEquals(listOf(9_800L, 300L, -5_100L, -13_000L), result.players.map { it.netCents })
        assertEquals(-result.pool.foodCents, result.players.sumOf { it.netCents })
        assertEquals(4 * 13_000L + 5_000L + 2_500L, result.paidInCents)
    }

    @Test
    fun `nothing is assigned twice when stale data credits a knockout of the champion`() {
        // All players out (a stale record): the last one out is the champion and keeps their bounty
        val players = listOf(BankPlayer(1, eliminatedBy = 2), BankPlayer(2, eliminatedBy = 1))
        val result = settle(players, listOf(2, 1), money(1_000, bounty = 500), listOf(1), PayoutRounding.ONE_DOLLAR)
        assertEquals(1, result.championId)
        assertEquals(result.pool.payableCents, result.assignedCents)
    }

    @Test
    fun `rebuys and add-ons count at the price each was bought at`() {
        // PP-085: one $10 rebuy, then the amount went to $15 and player 2 rebought twice
        val players = listOf(
            BankPlayer(1, boughtIn = true, rebuyPricesCents = listOf(1_000)),
            BankPlayer(2, boughtIn = true, rebuyPricesCents = listOf(1_500, 1_500), addOnPricesCents = listOf(500)),
            BankPlayer(3, boughtIn = true, eliminatedBy = 1)
        )
        // Today's amounts are different again; they don't matter for recorded purchases
        val result = settle(
            players,
            listOf(3),
            money(2_500, rebuy = 4_000, addOn = 9_900),
            listOf(2, 1),
            PayoutRounding.ONE_DOLLAR
        )

        assertEquals(4_000L, result.pool.rebuyCents)
        assertEquals(500L, result.pool.addOnCents)
        assertEquals(3 * 2_500L + 4_500L, result.pool.prizePoolCents)
        val byId = result.players.associateBy { it.playerId }
        assertEquals(2_500L + 1_000L, byId.getValue(1).costCents)
        assertEquals(1_000L, byId.getValue(1).rebuyCostCents)
        assertEquals(2_500L + 3_000L + 500L, byId.getValue(2).costCents)
        assertEquals(500L, byId.getValue(2).addOnCostCents)
        assertEquals(3 * 2_500L + 4_500L, result.paidInCents)
    }

    @Test
    fun `without recorded prices every purchase costs today's amount, as before`() {
        val result = settle(
            listOf(BankPlayer(1, rebuys = 2), BankPlayer(2, addOns = 1)),
            emptyList(), money(1_000, rebuy = 700, addOn = 300), listOf(1), PayoutRounding.ONE_DOLLAR
        )
        assertEquals(1_400L, result.pool.rebuyCents)
        assertEquals(300L, result.pool.addOnCents)
    }

    // ---- The conservation property --------------------------------------------------------------

    /**
     * Random tournaments, played one recorded action at a time: buy-ins, rebuys, add-ons,
     * knockouts (credited, uncredited, or credited to someone already out), payouts and undos. After
     * every action:
     *
     * - Σ paid out <= prize pool + bounty pool, and nobody is owed a negative amount;
     * - the payout table adds up to the prize pool;
     * - once every place is decided, everything owed adds up to prize pool + bounty pool exactly,
     *   to the cent; with everyone paid, Σ paid out equals it; and net pays sum to -food pool.
     *
     * Seeded, so a failure names a reproducible case.
     */
    @Test
    fun `money is conserved through random sequences of bank actions`() {
        val random = Random(1_204_2026)
        var finished = 0
        repeat(TOURNAMENTS) { tournament ->
            val sim = RandomTournament(random)
            repeat(random.nextInt(1, MAX_ACTIONS)) { step ->
                sim.act()
                checkInvariants(sim.settle(), "tournament $tournament step $step ${sim.describe()}")
            }
            // Finish it: knock out everyone but one, then pay everyone owed something
            sim.finish()
            val result = sim.settle()
            val context = "tournament $tournament finished ${sim.describe()}"
            checkInvariants(result, context)
            assertTrue(result.isComplete, context)
            assertEquals(result.pool.payableCents, result.assignedCents, context)
            assertEquals(result.pool.payableCents, result.paidOutCents, context)
            assertEquals(-result.pool.foodCents, result.players.sumOf { it.netCents }, context)
            finished++
        }
        assertEquals(TOURNAMENTS, finished)
    }

    private fun checkInvariants(result: Settlement, context: String) {
        val payable = result.pool.payableCents
        assertTrue(result.paidOutCents <= payable, "paid out ${result.paidOutCents} > $payable: $context")
        assertTrue(result.assignedCents <= payable, "assigned ${result.assignedCents} > $payable: $context")
        assertTrue(result.players.all { it.winningsCents >= 0 }, context)
        if (result.payoutTable.places.isNotEmpty()) {
            assertEquals(result.pool.prizePoolCents, result.payoutTable.totalCents, context)
        }
        if (result.isComplete) assertEquals(payable, result.assignedCents, context)
        // What the players paid for purchases is what the pool holds for them
        assertEquals(result.pool.rebuyCents, result.players.sumOf { it.rebuyCostCents }, context)
        assertEquals(result.pool.addOnCents, result.players.sumOf { it.addOnCostCents }, context)
    }

    /** A Bank tab driven at random. */
    private inner class RandomTournament(private val random: Random) {
        private val playerCount = random.nextInt(1, 31)
        private val money = money(
            buyIn = random.pick(0L, 1L, 500L, 1_000L, 2_000L, 2_500L, 1_414L, 10_000L, 1_000_000L),
            food = random.pick(0L, 500L, 707L, 1_000L),
            bounty = random.pick(0L, 0L, 200L, 500L, 354L, 1_000L),
            rebuy = random.pick(0L, 500L, 1_000L, 177L, 2_000L),
            addOn = random.pick(0L, 500L, 1_500L, 999L)
        )
        private val weights = when (random.nextInt(3)) {
            0 -> PayoutPreset.entries.random(random).weightsFor(random.nextInt(1, 10))
            1 -> List(random.nextInt(1, 10)) { random.nextInt(1, 999) }.sortedDescending()
            else -> List(random.nextInt(1, 10)) { random.nextInt(1, 999) }
        }
        private val rounding = PayoutRounding.entries.random(random)

        /** Half the tournaments record a price per purchase, and change the price as they go (PP-085). */
        private val priced = random.nextBoolean()
        private fun prices(
            count: Int,
            today: Long
        ) = List(count) { if (random.nextBoolean()) today else random.pick(0L, 500L, 999L, 1_234L) }
        private val players = (1..playerCount).associateWith { BankPlayer(it) }.toMutableMap()
        private val order = mutableListOf<Int>()

        fun settle(): Settlement = settle(players.values.sortedBy { it.id }, order, money, weights, rounding)

        fun describe() = "players=$playerCount $money weights=$weights $rounding out=$order"

        private fun stillIn() = players.keys.filterNot { it in order }

        @Suppress("CyclomaticComplexMethod")
        fun act() {
            val id = random.nextInt(1, playerCount + 1)
            val player = players.getValue(id)
            when (random.nextInt(8)) {
                0 -> players[id] = player.copy(boughtIn = !player.boughtIn)
                1 -> players[id] = random.nextInt(0, 21).let { n ->
                    if (priced) player.copy(rebuyPricesCents = prices(n, money.rebuyCents)) else player.copy(rebuys = n)
                }
                2 -> players[id] = random.nextInt(0, 21).let { n ->
                    if (priced) player.copy(addOnPricesCents = prices(n, money.addOnCents)) else player.copy(addOns = n)
                }
                3, 4 -> knockOut(id)
                5 -> if (id in order) { // back in the game
                    order.remove(id)
                    players[id] = player.copy(eliminatedBy = null)
                }
                6 -> players[id] = player.copy(paidOut = !player.paidOut)
                else -> payEveryoneOwed(fraction = random.nextDouble())
            }
        }

        private fun knockOut(id: Int) {
            if (id in order || stillIn().size <= 1) return
            val credit = when (random.nextInt(4)) {
                0 -> null // "Nobody"
                1 -> order.randomOrNull(random) // someone already out
                else -> stillIn().filter { it != id }.randomOrNull(random)
            }
            order += id
            players[id] = players.getValue(id).copy(eliminatedBy = credit)
        }

        private fun payEveryoneOwed(fraction: Double = 1.0) {
            settle().players.filter { it.winningsCents > 0 && random.nextDouble() < fraction }
                .forEach { owed -> players[owed.playerId] = players.getValue(owed.playerId).copy(paidOut = true) }
        }

        fun finish() {
            while (stillIn().size > 1) knockOut(stillIn().random(random))
            players.replaceAll { _, player -> player.copy(paidOut = false) }
            payEveryoneOwed()
        }
    }

    private fun Random.pick(vararg options: Long) = options[nextInt(options.size)]

    private companion object {
        const val TOURNAMENTS = 2_000
        const val MAX_ACTIONS = 60
    }
}
