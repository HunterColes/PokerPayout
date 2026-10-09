package com.huntercoles.pokerpayout.core.domain.settle

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settle-up from what the Bank recorded (1.4): each player owes their entry until it's ticked and
 * is owed their winnings until paid; the Bank is the party in the middle and keeps the food money.
 */
class SettleUpUseCaseTest {

    private val settleTournament = SettleTournamentUseCase(CalculatePayoutsUseCase())
    private val settleUp = SettleUpUseCase()

    /** 4 players, $20 buy-in, $5 food, $5 bounty; 2:1 over 2 places by $1. Player 1 wins. */
    private val money =
        MoneySettings(buyInCents = 2_000L, foodCents = 500L, bountyCents = 500L, rebuyCents = 1_000L, addOnCents = 0L)

    private fun night(players: List<BankPlayer>, order: List<Int> = listOf(4, 3, 2)): SettleUp? {
        val settlement = settleTournament(players, order, money, listOf(2, 1), PayoutRounding.ONE_DOLLAR)
        return settleUp(settlement, players, money)
    }

    private fun players(boughtIn: Boolean, paidOut: Boolean = false) = listOf(
        BankPlayer(1, boughtIn = boughtIn, paidOut = paidOut),
        BankPlayer(2, boughtIn = boughtIn, paidOut = paidOut, eliminatedBy = 1),
        BankPlayer(3, boughtIn = boughtIn, paidOut = paidOut, eliminatedBy = 1),
        BankPlayer(4, boughtIn = boughtIn, paidOut = paidOut, eliminatedBy = 2),
    )

    @Test
    fun `nothing to settle until there is a champion`() {
        assertNull(night(players(boughtIn = false), order = listOf(4, 3)))
    }

    @Test
    fun `everyone paid in, so the Bank pays each winner what they won`() {
        // Pool $80: 1st $53, 2nd $27. Bounties: 1 takes 2 and 3 ($10) and keeps their own ($5); 2 takes 4 ($5).
        val result = requireNotNull(night(players(boughtIn = true)))
        assertEquals(mapOf(1 to 6_800L, 2 to 3_200L, 3 to 0L, 4 to 0L, SettleUp.BANK_ID to -10_000L), result.balancesCents)
        assertEquals(
            listOf(Transfer(SettleUp.BANK_ID, 1, 6_800L), Transfer(SettleUp.BANK_ID, 2, 3_200L)),
            result.transfers,
        )
    }

    @Test
    fun `nobody paid in, so the players pay each other and the Bank gets the food`() {
        val result = requireNotNull(night(players(boughtIn = false)))
        // Everyone owes their $30 entry; the Bank, holding nothing, is owed the $20 food
        assertEquals(mapOf(1 to 3_800L, 2 to 200L, 3 to -3_000L, 4 to -3_000L, SettleUp.BANK_ID to 2_000L), result.balancesCents)
        assertEquals(4, result.transfers.size, "no group of them adds up to 0, so 5 parties need 4")
        assertEquals(2_000L, result.transfers.filter { it.toId == SettleUp.BANK_ID }.sumOf { it.amountCents })
    }

    @Test
    fun `everyone paid in and paid out is square`() {
        val result = requireNotNull(night(players(boughtIn = true, paidOut = true)))
        assertTrue(result.isSquare)
        assertTrue(result.balancesCents.values.all { it == 0L })
    }

    @Test
    fun `rebuys are paid when recorded, so the Bank holds them`() {
        val withRebuy = players(boughtIn = true).map { if (it.id == 3) it.copy(rebuyPricesCents = listOf(1_000L)) else it }
        val result = requireNotNull(night(withRebuy))
        // Pool $90 now: 1st $60, 2nd $30; the Bank holds $130, keeps $20 food and pays $110
        assertEquals(-11_000L, result.balancesCents.getValue(SettleUp.BANK_ID))
    }

    @Test
    fun `random nights balance to zero and the Bank always ends with the food money`() {
        val random = Random(SEED)
        repeat(RUNS) { run ->
            val count = random.nextInt(2, MAX_PLAYERS + 1)
            val players = (1..count).map { id ->
                BankPlayer(
                    id = id,
                    boughtIn = random.nextBoolean(),
                    paidOut = random.nextInt(4) == 0,
                    rebuyPricesCents = List(random.nextInt(3)) { 1_000L },
                    eliminatedBy = random.nextInt(1, count + 1).takeIf { it != id && random.nextBoolean() },
                )
            }
            val order = (1..count).shuffled(random).drop(1)
            val settlement = settleTournament(players, order, money, listOf(3, 2, 1), PayoutRounding.ONE_DOLLAR)
            val result = requireNotNull(settleUp(settlement, players, money)) { "run $run" }
            assertEquals(0L, result.balancesCents.values.sum(), "run $run")
            // What the Bank took in and paid out, then the settle-up: it ends holding the food money
            val bankAfter = settlement.paidInCents - settlement.paidOutCents + result.transfers.sumOf {
                when (SettleUp.BANK_ID) {
                    it.toId -> it.amountCents
                    it.fromId -> -it.amountCents
                    else -> 0L
                }
            }
            assertEquals(settlement.pool.foodCents, bankAfter, "run $run")
            assertTrue(result.transfers.size <= MinimumPayments.greedy(result.balancesCents).size, "run $run")
        }
    }

    private companion object {
        const val SEED = 20_261_009L
        const val RUNS = 500
        const val MAX_PLAYERS = 10
    }
}
