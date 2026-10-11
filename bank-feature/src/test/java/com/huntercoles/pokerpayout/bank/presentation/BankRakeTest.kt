package com.huntercoles.pokerpayout.bank.presentation

import com.huntercoles.pokerpayout.core.domain.model.HouseRake
import com.huntercoles.pokerpayout.core.domain.settle.SettleUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The house rake in the Bank (PP-145): the pool, the payout table and what the Bank pays out all come
 * after the rake, the bounties don't, and at the settle-up the Bank keeps the rake for the host, as it
 * keeps the food money, and says so when shared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankRakeTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        kit = BankTestKit(dispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    private val BankViewModel.state get() = uiState.value

    @Test
    fun thePoolAndTheTableComeAfterTheRakeAndTheBountiesDont() = with(kit) {
        tournamentPreferences.setRake(HouseRake.of(1_000))
        val state = BankScenes.midGame(kit).state
        // $450 in for prizes (9 x $40, Marcus's $40 rebuy, five $10 add-ons): $45 to the house, $405 for the places
        assertEquals(45_000L, state.pool.prizeInCents)
        assertEquals(4_500L, state.pool.rakeCents)
        assertEquals(40_500L, state.prizePoolCents)
        assertEquals(40_500L, state.payoutTable.totalCents)
        assertEquals(4_500L, state.payoutTable.rakeCents)
        // Every $5 bounty whole; the Bank pays back out the places and the bounties, never the rake or the food
        assertEquals(4_500L, state.pool.bountyPoolCents)
        assertEquals(45_000L, state.payableCents)
        assertEquals(state.pool.totalCents, state.payableCents + state.pool.rakeCents + state.pool.foodCents)
    }

    @Test
    fun atZeroPercentTheBankIsAsItWas() = with(kit) {
        val state = BankScenes.midGame(kit).state
        assertEquals(45_000L, state.prizePoolCents)
        assertEquals(0L, state.pool.rakeCents)
        assertEquals(49_500L, state.payableCents)
    }

    @Test
    fun atTheSettleUpTheBankKeepsTheRakeWithTheFood() = with(kit) {
        // 4 players, $20 buy-in, $5 food, nobody paid in; a 10% rake on the $80 is $8
        configure(players = 4, buyIn = 20.0, food = 5.0)
        tournamentPreferences.setRake(HouseRake.of(1_000))
        val viewModel = newViewModel()
        (2..4).forEach { viewModel.knockOut(it, 1) }
        val settleUp = requireNotNull(viewModel.state.settleUp)
        assertEquals(800L, settleUp.rakeCents)
        assertEquals(HouseRake.of(1_000), settleUp.rake)
        val received = settleUp.transfers.filter { it.toId == SettleUp.BANK_ID }.sumOf { it.amountCents }
        val paid = settleUp.transfers.filter { it.fromId == SettleUp.BANK_ID }.sumOf { it.amountCents }
        assertEquals("the Bank ends with the food money and the rake", 2_000L + 800L, received - paid)

        val text = requireNotNull(SettleUpShareText.build(context.resources, viewModel.state))
        assertTrue(text, text.lines().contains("Food \$20, kept by the bank."))
        assertTrue(text, text.lines().contains("House rake (10%) \$8, kept by the bank for the host."))
    }

    @Test
    fun withoutARakeTheShareTextHasNoRakeLine() = with(kit) {
        configure(players = 4, buyIn = 20.0, food = 5.0)
        val viewModel = newViewModel()
        (2..4).forEach { viewModel.knockOut(it, 1) }
        assertEquals(0L, requireNotNull(viewModel.state.settleUp).rakeCents)
        val text = requireNotNull(SettleUpShareText.build(context.resources, viewModel.state))
        assertFalse(text, text.contains("rake"))
    }
}
