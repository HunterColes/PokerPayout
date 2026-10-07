package com.huntercoles.pokerpayout.core.domain.history

import com.huntercoles.pokerpayout.core.domain.model.BankPlayer
import com.huntercoles.pokerpayout.core.domain.model.MoneySettings
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.domain.usecase.SettleTournamentUseCase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tonight's results for History (PP-037), from the same settlement the Bank and the Payouts tab show:
 * none until there is a champion and everyone owed money is paid; then every player in finishing
 * order with what they paid in and took home.
 */
class NightResultsTest {

    private val settle = SettleTournamentUseCase(CalculatePayoutsUseCase())

    /** $40 buy-in, $5 food, $5 bounty; rebuys $40 and add-ons $10 (recorded at their own prices below). */
    private val money = MoneySettings(
        buyInCents = 4_000L,
        foodCents = 500L,
        bountyCents = 500L,
        rebuyCents = 4_000L,
        addOnCents = 1_000L,
    )
    private val names = mapOf(1 to " Dana ", 2 to "Marcus", 3 to "", 4 to "Theo")

    /**
     * Four players, two places paid (70/30). Dana rebought twice ($40, then $35), Marcus took an
     * add-on. [out] is the order they went out: Theo (by Dana), player 3 (nobody credited), Marcus
     * (by Dana), so Dana wins. [paid] are the players marked paid.
     */
    private fun results(paid: Set<Int>, out: List<Int> = listOf(4, 3, 2)): List<NightPlayer>? {
        val players = listOf(
            BankPlayer(1, boughtIn = true, paidOut = 1 in paid, rebuyPricesCents = listOf(4_000L, 3_500L)),
            BankPlayer(2, boughtIn = true, paidOut = 2 in paid, eliminatedBy = 1, addOnPricesCents = listOf(1_000L)),
            BankPlayer(3, boughtIn = true, paidOut = 3 in paid),
            BankPlayer(4, boughtIn = true, paidOut = 4 in paid, eliminatedBy = 1),
        )
        val settlement = settle(players, out, money, listOf(70, 30), PayoutRounding.ONE_DOLLAR)
        return NightResults.of(settlement, players, names)
    }

    @Test
    fun `nothing to save before there is a champion`() {
        assertNull(results(paid = setOf(1, 2, 3, 4), out = listOf(4, 3)))
    }

    @Test
    fun `nothing to save while anyone owed money is unpaid`() {
        assertNull(results(paid = emptySet()))
        assertNull(results(paid = setOf(1)))
        assertNull(results(paid = setOf(2)))
        // Players owed nothing (player 3 and Theo) needn't be marked
        assertEquals(4, results(paid = setOf(1, 2))?.size)
    }

    @Test
    fun `every player in finishing order, with what they paid in and took home`() {
        val night = requireNotNull(results(paid = setOf(1, 2)))
        assertEquals(listOf("Dana", "Marcus", "Player 3", "Theo"), night.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), night.map { it.place })
        assertEquals(listOf(5_000L, 5_000L, 5_000L, 5_000L), night.map { it.entryCents })
        assertEquals(listOf(2, 0, 0, 0), night.map { it.rebuys })
        assertEquals(listOf(7_500L, 0L, 0L, 0L), night.map { it.rebuyCents })
        assertEquals(listOf(0, 1, 0, 0), night.map { it.addOns })
        assertEquals(listOf(0L, 1_000L, 0L, 0L), night.map { it.addOnCents })
        // The $245 prize pool (4 x $40, $75 of rebuys, $10 of add-ons) goes to the two places paid
        assertEquals(24_500L, night.sumOf { it.prizeCents })
        assertTrue(night[0].prizeCents > night[1].prizeCents && night[1].prizeCents > 0L)
        assertEquals(listOf(0L, 0L), night.drop(2).map { it.prizeCents })
        // Dana knocked out Theo and Marcus, keeps her own bounty, and takes player 3's, which nobody claimed
        assertEquals(listOf(2, 0, 0, 0), night.map { it.knockouts })
        assertEquals(listOf(2_000L, 0L, 0L, 0L), night.map { it.bountyCents })
        // Everything paid out is accounted for: the prize pool and the four bounties
        assertEquals(24_500L + 2_000L, night.sumOf { it.wonCents })
    }
}
