package com.huntercoles.pokerpayout.tournament.presentation.payouts

import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsGame.Companion.BEN
import com.huntercoles.pokerpayout.tournament.presentation.payouts.PayoutsGame.Companion.RITA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Payouts tab's bounties card and share text with progressive and mystery bounties (PP-035), on
 * the mockups' night: each claim is what the knockouts paid, and with the champion's share they add
 * up to the bounty pool.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PayoutsBountyModesTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var game: PayoutsGame

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        game = PayoutsGame()
    }

    @After
    fun tearDown() {
        game.clear()
        Dispatchers.resetMain()
    }

    private fun PayoutsViewModel.state(): PayoutsUiState {
        dispatcher.scheduler.advanceUntilIdle()
        return uiState.value
    }

    /**
     * Progressive, $5 to start. Dana knocks out Ben, Alex, Sam, Jo and Theo ($2.50 each, her bounty
     * growing to $17.50), Marcus knocks out Rita ($2.50; his bounty $7.50), Priya goes out unclaimed,
     * and Dana takes Marcus's $7.50 ($3.75 now). Dana's claims: $16.25; she ends on $21.25 and takes
     * Priya's $5 too.
     */
    @Test
    fun progressiveClaimsAreTheCashHalvesAndTheChampionTakesHerGrownBounty() {
        game.tournament.setBountyMode(BountyMode.PROGRESSIVE)
        val bounties = game.finished().viewModel().state().bounties
        assertEquals(BountyMode.PROGRESSIVE, bounties.mode)
        assertEquals(
            listOf(
                BountyClaim("Dana", listOf("Ben", "Alex", "Sam", "Jo", "Theo", "Marcus"), 1_625L),
                BountyClaim("Marcus", listOf("Rita"), 250L)
            ),
            bounties.claims
        )
        assertEquals("Dana", bounties.championName)
        assertEquals(2_125L + 500L, bounties.championCents)
        assertEquals(0L, bounties.stillOutCents)
        assertEquals(4_500L, bounties.claims.sumOf { it.cents } + bounties.championCents)

        val text = PayoutsShareText.build(game.context, game.viewModel().state())
        assertTrue(
            text,
            text.contains(
                "Progressive bounties, $5 to start: Dana $16.25 (Ben, Alex, Sam, Jo, Theo, Marcus) · " +
                    "Marcus $2.50 (Rita) · Dana $26.25 (own bounty and unclaimed)"
            )
        )
    }

    @Test
    fun mysteryClaimsAreTheEnvelopesDrawn() {
        game.tournament.setBountyMode(BountyMode.MYSTERY)
        game.midGame()
        game.bank.savePlayerBountyDraw(BEN, 1_500L)
        game.bank.savePlayerBountyDraw(RITA, 300L)
        val bounties = game.viewModel().state().bounties
        assertEquals(BountyMode.MYSTERY, bounties.mode)
        assertEquals(9, bounties.envelopes)
        assertEquals(
            listOf(BountyClaim("Dana", listOf("Ben"), 1_500L), BountyClaim("Marcus", listOf("Rita"), 300L)),
            bounties.claims
        )
        assertEquals(4_500L - 1_800L, bounties.stillOutCents)

        val text = PayoutsShareText.build(game.context, game.viewModel().state())
        assertTrue(text, text.contains("Mystery bounties, 9 envelopes: Dana $15 (Ben) · Marcus $3 (Rita)"))
    }

    @Test
    fun aStandardNightReadsAsBefore() {
        val text = PayoutsShareText.build(game.context, game.midGame().viewModel().state())
        assertTrue(text, text.contains("Bounties, $5 a head: Dana $5 (Ben) · Marcus $5 (Rita)"))
    }
}
