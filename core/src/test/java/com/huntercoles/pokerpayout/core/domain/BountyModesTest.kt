package com.huntercoles.pokerpayout.core.domain

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.ProgressiveBounty
import com.huntercoles.pokerpayout.core.domain.model.Settlement
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Progressive (PKO) and mystery bounties (PP-035) in the settlement: exact answers for small nights,
 * then money conservation over thousands of seeded random knockout orders.
 */
class BountyModesTest {

    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())

    private fun money(bounty: Long, mode: BountyMode, buyIn: Long = 2_000) = MoneySettings(
        buyInCents = buyIn,
        foodCents = 0,
        bountyCents = bounty,
        rebuyCents = 0,
        addOnCents = 0,
        bountyMode = mode
    )

    private fun settleNight(players: List<BankPlayer>, order: List<Int>, money: MoneySettings): Settlement =
        settle(players, order, money, listOf(1), PayoutRounding.ONE_DOLLAR)

    private fun Settlement.bountyWinnings(): Long =
        players.sumOf { it.knockoutBountyCents + it.kingsBountyCents + it.unclaimedBountyCents }

    // ---- Progressive ----------------------------------------------------------------------------

    @Test
    fun `the cash half is rounded down to the cent and the odd cent goes on the head`() {
        assertEquals(ProgressiveBounty.Split(cashCents = 250, headCents = 250), ProgressiveBounty.split(500))
        assertEquals(ProgressiveBounty.Split(cashCents = 387, headCents = 388), ProgressiveBounty.split(775))
        assertEquals(ProgressiveBounty.Split(cashCents = 0, headCents = 1), ProgressiveBounty.split(1))
        assertEquals(ProgressiveBounty.Split(cashCents = 0, headCents = 0), ProgressiveBounty.split(0))
        for (cents in 0L..2_001L) {
            val split = ProgressiveBounty.split(cents)
            assertEquals(cents, split.cashCents + split.headCents)
            assertTrue(split.headCents - split.cashCents in 0L..1L)
        }
    }

    @Test
    fun `each knockout pays half the bounty and puts half on the eliminator's head`() {
        // 4 players at $5. Player 1 knocks out 4, player 2 knocks out 1 (with 1's grown bounty), then 3.
        val players = listOf(
            BankPlayer(1, eliminatedBy = 2),
            BankPlayer(2),
            BankPlayer(3, eliminatedBy = 2),
            BankPlayer(4, eliminatedBy = 1)
        )
        val result = settleNight(players, listOf(4, 1, 3), money(500, BountyMode.PROGRESSIVE))
        val byId = result.players.associateBy { it.playerId }

        assertEquals(2, result.championId)
        // Player 1: $2.50 from player 4's $5, and went out carrying $7.50
        assertEquals(250L, byId.getValue(1).knockoutBountyCents)
        assertEquals(750L, byId.getValue(1).headBountyCents)
        // Player 2: $3.75 from player 1's $7.50 and $2.50 from player 3's $5; their bounty grew to $11.25
        assertEquals(2, byId.getValue(2).knockouts)
        assertEquals(625L, byId.getValue(2).knockoutBountyCents)
        assertEquals(1_125L, byId.getValue(2).headBountyCents)
        assertEquals(1_125L, byId.getValue(2).kingsBountyCents)
        assertEquals(0L, byId.getValue(2).unclaimedBountyCents)
        assertEquals(500L, byId.getValue(3).headBountyCents)
        assertEquals(BountyMode.PROGRESSIVE, result.bountyMode)
        assertEquals(result.pool.bountyCents, result.bountyWinnings())
        assertEquals(result.pool.payableCents, result.assignedCents)
    }

    @Test
    fun `a knockout nobody was credited with leaves its bounty for the champion`() {
        // 3 players at $5: player 1 knocks out 3, then 2 goes out with nobody credited
        val players = listOf(BankPlayer(1), BankPlayer(2), BankPlayer(3, eliminatedBy = 1))
        val result = settleNight(players, listOf(3, 2), money(500, BountyMode.PROGRESSIVE))
        val champion = result.players.first { it.playerId == 1 }
        assertEquals(250L, champion.knockoutBountyCents)
        assertEquals(750L, champion.kingsBountyCents)
        assertEquals(500L, champion.unclaimedBountyCents)
        assertEquals(1_500L, result.bountyWinnings())
    }

    @Test
    fun `an eliminator already out takes the whole bounty in cash`() {
        // Player 2 went out first (to player 1); then player 3's knockout was credited to player 2
        val players = listOf(BankPlayer(1), BankPlayer(2, eliminatedBy = 1), BankPlayer(3, eliminatedBy = 2))
        val result = settleNight(players, listOf(2, 3), money(500, BountyMode.PROGRESSIVE))
        val byId = result.players.associateBy { it.playerId }
        assertEquals(500L, byId.getValue(2).knockoutBountyCents)
        assertEquals(500L, byId.getValue(2).headBountyCents)
        assertEquals(250L, byId.getValue(1).knockoutBountyCents)
        assertEquals(750L, byId.getValue(1).kingsBountyCents)
        assertEquals(1_500L, result.bountyWinnings())
    }

    @Test
    fun `before the champion is known only the cash halves are owed, and heads show the bounties`() {
        val players = (1..5).map { BankPlayer(it, eliminatedBy = if (it == 5) 1 else null) }
        val result = settleNight(players, listOf(5), money(500, BountyMode.PROGRESSIVE))
        assertEquals(listOf(750L, 500L, 500L, 500L, 500L), result.players.map { it.headBountyCents })
        assertEquals(250L, result.bountyWinnings())
        assertEquals(0L, result.players.sumOf { it.kingsBountyCents + it.unclaimedBountyCents })
    }

    // ---- Mystery --------------------------------------------------------------------------------

    @Test
    fun `each credited knockout wins its envelope and the champion takes the envelopes left`() {
        // 4 players at $5: envelopes $12, $4, $2, $2
        assertEquals(listOf(1_200L, 400L, 200L, 200L), MysteryBounty.envelopes(4, 500))
        val players = listOf(
            BankPlayer(1),
            BankPlayer(2, eliminatedBy = 1, bountyDrawCents = 200),
            BankPlayer(3), // nobody credited: no envelope drawn
            BankPlayer(4, eliminatedBy = 1, bountyDrawCents = 1_200)
        )
        val result = settleNight(players, listOf(4, 3, 2), money(500, BountyMode.MYSTERY))
        val champion = result.players.first { it.playerId == 1 }
        assertEquals(2, champion.knockouts)
        assertEquals(1_400L, champion.knockoutBountyCents)
        assertEquals(listOf(400L, 200L), result.envelopesLeft)
        assertEquals(600L, champion.kingsBountyCents)
        assertEquals(0L, champion.unclaimedBountyCents)
        assertEquals(2_000L, result.bountyWinnings())
        assertEquals(result.pool.payableCents, result.assignedCents)
    }

    @Test
    fun `an envelope recorded on a knockout nobody is credited with goes back in the pool`() {
        val players = listOf(BankPlayer(1), BankPlayer(2), BankPlayer(3), BankPlayer(4, bountyDrawCents = 1_200))
        val result = settleNight(players, listOf(4), money(500, BountyMode.MYSTERY))
        assertEquals(listOf(1_200L, 400L, 200L, 200L), result.envelopesLeft)
        assertEquals(0L, result.bountyWinnings())
    }

    @Test
    fun `a saved game with no mode settles exactly as before`() {
        val players = listOf(BankPlayer(1, eliminatedBy = 2), BankPlayer(2), BankPlayer(3), BankPlayer(4, eliminatedBy = 1))
        val plain = MoneySettings(buyInCents = 10_000, foodCents = 0, bountyCents = 1_000, rebuyCents = 0, addOnCents = 0)
        assertEquals(BountyMode.STANDARD, plain.bountyMode)
        val result = settleNight(players, listOf(4, 1, 3), plain)
        val byId = result.players.associateBy { it.playerId }
        assertEquals(1_000L, byId.getValue(1).knockoutBountyCents)
        assertEquals(1_000L, byId.getValue(2).knockoutBountyCents)
        assertEquals(1_000L, byId.getValue(2).kingsBountyCents)
        assertEquals(1_000L, byId.getValue(2).unclaimedBountyCents)
        assertTrue(result.players.all { it.headBountyCents == 1_000L })
        assertEquals(emptyList(), result.envelopesLeft)
    }

    // ---- Conservation, property-style -----------------------------------------------------------

    /**
     * Random nights in each mode, recorded one knockout at a time in a random order: credited to
     * someone still in, to someone already out, or to nobody; players brought back now and then
     * (their envelope back in the pool); mystery envelopes drawn from what the settlement says is
     * left, with a seeded RNG. After every step nothing is owed twice; at the end the bounties
     * add up to the bounty pool to the cent, and the mystery pool is down to the champion's.
     */
    @Test
    fun `bounties add up to the bounty pool over random knockout orders`() {
        val random = Random(35_2026)
        BountyMode.entries.forEach { mode ->
            repeat(NIGHTS) { night ->
                val sim = RandomNight(random, mode)
                repeat(random.nextInt(0, MAX_STEPS)) { step ->
                    sim.step().assertConsistent("$mode night $night step $step ${sim.describe()}")
                }
                sim.finish()
                val result = sim.settle()
                val context = "$mode night $night finished ${sim.describe()}"
                result.assertConsistent(context)
                assertTrue(result.isComplete, context)
                assertEquals(result.pool.bountyCents, result.bountyWinnings(), context)
                assertEquals(result.pool.payableCents, result.assignedCents, context)
                if (mode == BountyMode.MYSTERY && result.pool.bountyCents > 0L) {
                    val credited = result.players.sumOf { it.knockouts }
                    val champion = result.players.first { it.playerId == result.championId }
                    assertEquals(sim.playerCount - credited, result.envelopesLeft.size, context)
                    assertEquals(result.envelopesLeft.sum(), champion.kingsBountyCents, context)
                }
            }
        }
    }

    private fun Settlement.assertConsistent(context: String) {
        assertTrue(assignedCents <= pool.payableCents, "assigned $assignedCents > ${pool.payableCents}: $context")
        assertTrue(bountyWinnings() <= pool.bountyCents, "bounties ${bountyWinnings()} > ${pool.bountyCents}: $context")
        assertTrue(players.all { it.knockoutBountyCents >= 0 && it.kingsBountyCents >= 0 && it.headBountyCents >= 0 }, context)
        if (bountyMode == BountyMode.PROGRESSIVE && championId == null) {
            // Every bounty is on the head of someone still in, paid out in cash, or waiting for the champion
            val onHeads = players.filter { it.place == null }.sumOf { it.headBountyCents }
            val waiting = pool.bountyCents - onHeads - players.sumOf { it.knockoutBountyCents }
            assertTrue(waiting >= 0, context)
        }
    }

    /** A Bank tab driven at random: knockouts in any order, with any credit, and bring-backs. */
    private inner class RandomNight(private val random: Random, private val mode: BountyMode) {
        val playerCount = random.nextInt(2, 31)
        private val money = money(bounty = random.pick(0L, 1L, 3L, 200L, 333L, 500L, 775L, 1_000L, 2_500L), mode = mode)
        private val players = (1..playerCount).associateWith { BankPlayer(it) }.toMutableMap()
        private val order = mutableListOf<Int>()

        fun settle(): Settlement = settleNight(players.values.sortedBy { it.id }, order, money)

        fun describe() = "players=$playerCount bounty=${money.bountyCents} out=$order"

        private fun stillIn() = players.keys.filterNot { it in order }

        fun step(): Settlement {
            val id = random.nextInt(1, playerCount + 1)
            if (id in order && random.nextInt(4) == 0) {
                // Back in the game: the knockout and its envelope are taken back
                order.remove(id)
                players[id] = players.getValue(id).copy(eliminatedBy = null, bountyDrawCents = null)
            } else {
                knockOut(id)
            }
            return settle()
        }

        private fun knockOut(id: Int) {
            if (id in order || stillIn().size <= 1) return
            val credit = when (random.nextInt(4)) {
                0 -> null // "Nobody"
                1 -> order.randomOrNull(random) // someone already out
                else -> stillIn().filter { it != id }.randomOrNull(random)
            }
            // As the Bank does: a credited mystery knockout draws one of the envelopes left
            val draw = if (mode == BountyMode.MYSTERY && credit != null) {
                MysteryBounty.draw(settle().envelopesLeft, random)
            } else {
                null
            }
            order += id
            players[id] = players.getValue(id).copy(eliminatedBy = credit, bountyDrawCents = draw)
        }

        fun finish() {
            while (stillIn().size > 1) knockOut(stillIn().random(random))
        }
    }

    private fun Random.pick(vararg options: Long) = options[nextInt(options.size)]

    private companion object {
        const val NIGHTS = 700
        const val MAX_STEPS = 40
    }
}
