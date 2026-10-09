package com.huntercoles.pokerpayout.bank.presentation

import android.content.Context
import com.huntercoles.pokerpayout.core.domain.model.BountyMode
import com.huntercoles.pokerpayout.core.domain.model.MysteryBounty
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * Progressive (PKO) and mystery bounties in the Bank (PP-035), through the real ViewModel on real
 * preferences: what each knockout pays and shows, Undo, a restart, the seeded envelope draw, a game
 * saved before bounty modes, and money conservation over random knockout orders in every mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BankBountyModesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var kit: BankTestKit

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        kit = BankTestKit(testDispatcher)
    }

    @After
    fun tearDown() {
        kit.clear()
        Dispatchers.resetMain()
    }

    /** [players] players, $20 buy-in, $5 bounty, paid out winner-takes-all, bounties in [mode]. */
    private fun BankTestKit.night(mode: BountyMode, players: Int = 5): BankViewModel {
        configure(players = players, buyIn = 20.0, bounty = 5.0, weights = listOf(1))
        tournamentPreferences.setBountyMode(mode)
        return newViewModel()
    }

    private fun BankViewModel.bountyOf(id: Int): Long? = uiState.value.rows.first { it.playerId == id }.bountyCents

    // ---- Progressive ----------------------------------------------------------------------------

    @Test
    fun aProgressiveKnockoutPaysHalfAndPutsHalfOnTheEliminatorsBounty() = with(kit) {
        val viewModel = night(BountyMode.PROGRESSIVE)
        assertEquals(listOf(500L, 500L, 500L, 500L, 500L), (1..5).map { viewModel.bountyOf(it) })

        viewModel.act(BankIntent.KnockOut(5, 1))
        assertEquals("Player 5 is out in 5th · Player 1 takes $2.50, bounty now $7.50", snackbarMessage())
        settle()

        assertEquals(750L, viewModel.bountyOf(1))
        assertNull("an out row shows no bounty", viewModel.bountyOf(5))
        val owed = viewModel.payOutSheet(1).owed
        assertEquals(250L, owed.knockoutBountyCents)
        assertEquals(750L, owed.headBountyCents)
    }

    @Test
    fun theKnockoutSheetShowsTheBountiesAsTheyStandNow() = with(kit) {
        val viewModel = night(BountyMode.PROGRESSIVE)
        viewModel.knockOut(5, 1)

        // Player 1 is worth $7.50 to whoever knocks them out now
        viewModel.send(BankIntent.OpenKnockout(1))
        val sheet = viewModel.uiState.value.sheet as BankSheet.Knockout
        assertEquals(BountyMode.PROGRESSIVE, sheet.mode)
        assertEquals(750L, sheet.bountyCents)
        assertEquals(
            mapOf(2 to 500L, 3 to 500L, 4 to 500L, 5 to 500L),
            sheet.candidates.associate { it.playerId to it.bountyCents }
        )

        // And Player 1, as a candidate for someone else's knockout, carries $7.50
        viewModel.send(BankIntent.DismissSheet, BankIntent.OpenKnockout(2))
        val other = viewModel.uiState.value.sheet as BankSheet.Knockout
        assertEquals(500L, other.bountyCents)
        assertEquals(750L, other.candidates.first { it.playerId == 1 }.bountyCents)
    }

    @Test
    fun anEliminatorAlreadyOutTakesTheWholeBounty() = with(kit) {
        val viewModel = night(BountyMode.PROGRESSIVE)
        viewModel.knockOut(5, 1)
        viewModel.act(BankIntent.KnockOut(4, 5))
        assertEquals("Player 4 is out in 4th · Player 5 takes all $5", snackbarMessage())
        settle()
        assertEquals(500L, viewModel.payOutSheet(5).owed.knockoutBountyCents)
    }

    @Test
    fun theChampionTakesTheirGrownBountyAndTheUnclaimedOnes() = with(kit) {
        val viewModel = night(BountyMode.PROGRESSIVE)
        viewModel.knockOut(5, 1) // 1: $2.50 cash, bounty $7.50
        viewModel.knockOut(4, null) // $5 for the champion
        viewModel.knockOut(1, 2) // 2: $3.75 cash, bounty $8.75
        viewModel.knockOut(3, 2) // 2: $2.50 cash, bounty $11.25; the champion

        val state = viewModel.uiState.value
        assertEquals(2, state.championId)
        assertEquals(1_125L, viewModel.bountyOf(2))
        val champion = viewModel.payOutSheet(2).owed
        assertEquals(625L, champion.knockoutBountyCents)
        assertEquals(1_125L, champion.kingsBountyCents)
        assertEquals(500L, champion.unclaimedBountyCents)
        assertEquals(state.payableCents, (1..5).sumOf { viewModel.payOutSheet(it).owed.winningsCents })
    }

    @Test
    fun undoFullyReversesAProgressiveKnockout() = with(kit) {
        val viewModel = night(BountyMode.PROGRESSIVE)
        viewModel.knockOut(5, 1)
        val before = viewModel.recorded()
        val rowsBefore = viewModel.uiState.value.rows
        val owedBefore = (1..5).map { viewModel.payOutSheet(it).owed }

        viewModel.knockOut(1, 2) // half of player 1's $7.50 onto player 2's $5
        assertEquals(875L, viewModel.bountyOf(2))
        viewModel.send(BankIntent.Undo)

        assertEquals(before, viewModel.recorded())
        assertEquals(rowsBefore, viewModel.uiState.value.rows)
        assertEquals(owedBefore, (1..5).map { viewModel.payOutSheet(it).owed })
        // And it is saved that way: a restart shows the same bounties
        clear()
        val restarted = newViewModel()
        assertEquals(rowsBefore, restarted.uiState.value.rows)
    }

    @Test
    fun progressiveBountiesComeBackTheSameAfterARestart() = with(kit) {
        val viewModel = night(BountyMode.PROGRESSIVE, players = 7)
        viewModel.knockOut(7, 1) // player 1: bounty $7.50
        viewModel.knockOut(6, 1) // player 1: bounty $10
        viewModel.knockOut(1, 2) // player 2: $5 cash, bounty $10
        val rows = viewModel.uiState.value.rows
        val owed = (1..7).map { viewModel.payOutSheet(it).owed }

        clear() // process death
        val restored = newViewModel()
        assertEquals(rows, restored.uiState.value.rows)
        assertEquals(owed, (1..7).map { restored.payOutSheet(it).owed })
        assertEquals(1_000L, restored.bountyOf(2))
        assertEquals(500L, restored.payOutSheet(2).owed.knockoutBountyCents)
    }

    // ---- Mystery --------------------------------------------------------------------------------

    @Test
    fun aMysteryKnockoutDrawsAnEnvelopeAndShowsIt() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        val deal = MysteryBounty.envelopes(9, 500)
        assertEquals(deal, viewModel.uiState.value.envelopesLeft)

        viewModel.act(BankIntent.KnockOut(9, 1))
        val reveal = viewModel.uiState.value.sheet as BankSheet.Envelope
        assertTrue("${reveal.cents} is one of $deal", reveal.cents in deal)
        assertEquals("Player 1", reveal.eliminatorName)
        assertEquals("Player 9", reveal.victimName)
        assertEquals(8, reveal.envelopesLeft)
        assertNull(reveal.championName)
        assertEquals("Player 9 is out in 9th · Player 1 draws ${money(reveal.cents)}", snackbarMessage())
        settle()

        assertEquals(reveal.cents, viewModel.player(9).bountyDrawCents)
        assertEquals(reveal.cents, bankPreferences.getPlayerBountyDraw(9))
        assertEquals(MysteryBounty.remaining(deal, listOf(reveal.cents)), viewModel.uiState.value.envelopesLeft)
        assertEquals(reveal.cents, viewModel.payOutSheet(1).owed.knockoutBountyCents)
        viewModel.send(BankIntent.DismissSheet)
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun undoPutsTheEnvelopeBackInThePool() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        val deal = viewModel.uiState.value.envelopesLeft
        val before = viewModel.recorded()

        viewModel.knockOut(9, 1)
        assertTrue(viewModel.uiState.value.sheet is BankSheet.Envelope)
        viewModel.send(BankIntent.Undo)

        assertEquals(before, viewModel.recorded())
        assertEquals(deal, viewModel.uiState.value.envelopesLeft)
        assertNull("the reveal goes with it", viewModel.uiState.value.sheet)
        assertNull(bankPreferences.getPlayerBountyDraw(9))
        assertEquals(0L, viewModel.payOutSheet(1).owed.knockoutBountyCents)
    }

    @Test
    fun undoFromTheSnackbarPutsTheEnvelopeBackToo() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        val deal = viewModel.uiState.value.envelopesLeft
        viewModel.act(BankIntent.KnockOut(9, 1))
        pressSnackbarUndo()
        assertFalse(viewModel.player(9).out)
        assertEquals(deal, viewModel.uiState.value.envelopesLeft)
        assertNull(viewModel.uiState.value.sheet)
    }

    @Test
    fun bringingAPlayerBackPutsTheirEnvelopeBack() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        val deal = viewModel.uiState.value.envelopesLeft
        viewModel.knockOut(9, 1)
        viewModel.send(BankIntent.DismissSheet, BankIntent.BringBack(9))
        assertNull(viewModel.player(9).bountyDrawCents)
        assertNull(bankPreferences.getPlayerBountyDraw(9))
        assertEquals(deal, viewModel.uiState.value.envelopesLeft)
    }

    @Test
    fun aMysteryKnockoutCreditedToNobodyDrawsNothing() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        viewModel.act(BankIntent.KnockOut(9, null))
        assertEquals("Player 9 is out in 9th · bounty to the champion", snackbarMessage())
        settle()
        assertNull(viewModel.uiState.value.sheet)
        assertNull(viewModel.player(9).bountyDrawCents)
        assertEquals(9, viewModel.uiState.value.envelopesLeft.size)
    }

    @Test
    fun mysteryDrawsComeBackTheSameAfterARestart() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        viewModel.knockOut(9, 1)
        viewModel.knockOut(8, 2)
        val draws = listOf(viewModel.player(9).bountyDrawCents, viewModel.player(8).bountyDrawCents)
        val left = viewModel.uiState.value.envelopesLeft

        clear() // process death
        val restored = newViewModel()
        assertEquals(draws, listOf(restored.player(9).bountyDrawCents, restored.player(8).bountyDrawCents))
        assertEquals(left, restored.uiState.value.envelopesLeft)
        assertNull("no reveal after a restart", restored.uiState.value.sheet)
    }

    @Test
    fun theSameSeedDrawsTheSameEnvelopes() = with(kit) {
        fun drawsFor(seed: Int): List<Long?> {
            clear()
            bankPreferences.resetAllBankData()
            draws = Random(seed)
            val viewModel = night(BountyMode.MYSTERY, players = 9)
            (9 downTo 2).forEach { viewModel.knockOut(it, 1) }
            return (2..9).map { viewModel.player(it).bountyDrawCents }
        }
        val first = drawsFor(seed = 7)
        assertEquals(first, drawsFor(seed = 7))
        // Eight knockouts drew eight different envelopes out of the nine
        assertEquals(MysteryBounty.envelopes(9, 500).sum(), first.sumOf { it ?: 0L } + kitChampionTake())
    }

    /** What the champion of the night just played takes: the envelopes left. */
    private fun kitChampionTake(): Long = with(kit) {
        val viewModel = newViewModel()
        val champion = requireNotNull(viewModel.uiState.value.championId)
        viewModel.payOutSheet(champion).owed.kingsBountyCents
    }

    /**
     * Late registration in a mystery game (found by core's SettlementPropertiesTest): nine players at
     * $5 deal 1 x $15, 2 x $6, 6 x $3; the first knockout draws the $15; then a tenth player sits
     * down. Ten players deal 1 x $17, 2 x $6, 7 x $3, which has no $15, so the drawn envelope used to
     * come out of nothing: ten envelopes worth the whole $50 were left on top of the $15 paid, and the
     * night paid out up to $9 more in bounties than went in. Now the $35 left is dealt again.
     */
    @Test
    fun aPlayerJoiningAfterADrawLeavesOnlyTheMoneyNotYetDrawn() = with(kit) {
        // A seed whose first draw from the nine envelopes is the first one, the $15
        draws = Random((0..1_000).first { Random(it).nextInt(9) == 0 })
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        viewModel.knockOut(9, 1)
        assertEquals(1_500L, viewModel.player(9).bountyDrawCents)
        viewModel.send(BankIntent.DismissSheet)

        tournamentPreferences.setPlayerCount(10)
        settle()
        assertEquals(10 * 500L - 1_500L, viewModel.uiState.value.envelopesLeft.sum())
        assertEquals(9, viewModel.uiState.value.envelopesLeft.size)

        (listOf(10) + (8 downTo 2)).forEach { id ->
            viewModel.knockOut(id, 1)
            viewModel.send(BankIntent.DismissSheet)
        }
        val owed = (1..10).map { viewModel.payOutSheet(it).owed }
        assertEquals(10 * 500L, owed.sumOf { it.knockoutBountyCents + it.kingsBountyCents + it.unclaimedBountyCents })
    }

    @Test
    fun envelopesNeverRunOutAndTheChampionTakesTheLast() = with(kit) {
        val viewModel = night(BountyMode.MYSTERY, players = 9)
        (9 downTo 2).forEach { id ->
            viewModel.knockOut(id, 1)
            val state = viewModel.uiState.value
            assertEquals("after player $id", id - 1, state.envelopesLeft.size)
            if (id == 2) {
                // The last knockout's reveal says what the champion takes
                val reveal = state.sheet as BankSheet.Envelope
                assertEquals("Player 1", reveal.championName)
                assertEquals(state.envelopesLeft.sum(), reveal.championCents)
            }
            viewModel.send(BankIntent.DismissSheet)
        }
        val state = viewModel.uiState.value
        val champion = viewModel.payOutSheet(1)
        assertEquals(1, champion.envelopesLeft)
        assertEquals(state.envelopesLeft.single(), champion.owed.kingsBountyCents)
        assertEquals(state.pool.bountyCents, champion.owed.knockoutBountyCents + champion.owed.kingsBountyCents)
    }

    // ---- Saved data -------------------------------------------------------------------------------

    /**
     * A game saved before PP-035 (v1.3.7): a bounty, knockouts with their credits, and no bounty mode
     * or envelope stored. It loads as Standard and settles exactly as it did: a whole bounty a
     * knockout, nothing written to change that.
     */
    @Test
    fun aGameSavedBeforeBountyModesLoadsAsStandard() = with(kit) {
        configure(players = 5, buyIn = 20.0, bounty = 5.0, weights = listOf(1))
        listOf(5 to 1, 4 to 1).forEach { (victim, by) ->
            bankPreferences.savePlayerOutStatus(victim, true)
            bankPreferences.savePlayerEliminatedBy(victim, by)
        }
        bankPreferences.saveEliminationOrder(listOf(5, 4))
        val raw = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE)
        assertFalse("the saved game has no bounty mode", raw.contains("bounty_mode"))

        val reloaded = TournamentPreferences(context)
        assertEquals(BountyMode.STANDARD, reloaded.getBountyMode())
        assertEquals(BountyMode.STANDARD, reloaded.getMoneySettings().bountyMode)
        val viewModel = newViewModel()
        assertEquals(BountyMode.STANDARD, viewModel.uiState.value.bountyMode)
        assertEquals(1_000L, viewModel.payOutSheet(1).owed.knockoutBountyCents)
        assertTrue("standard rows show no bounty", viewModel.uiState.value.rows.all { it.bountyCents == null })
        assertTrue(viewModel.uiState.value.envelopesLeft.isEmpty())
        assertFalse("loading writes no mode", raw.contains("bounty_mode"))
    }

    // ---- Conservation through the Bank, property-style ------------------------------------------

    /**
     * Random nights in every mode through the ViewModel: knockouts in a random order, credited to
     * someone still in, someone already out or nobody; bring-backs; undos. After every action the
     * Bank owes no more than the prize and bounty pools; once there is a champion the bounties add
     * up to the bounty pool to the cent. Seeded, envelopes included.
     */
    @Test
    fun bountiesAddUpOverRandomKnockoutOrdersInEveryMode() = with(kit) {
        val random = Random(35_10_2026)
        BountyMode.entries.forEach { mode ->
            repeat(SESSIONS) { session ->
                clear()
                bankPreferences.resetAllBankData()
                draws = Random(random.nextInt())
                val players = random.nextInt(2, 11)
                val bounty = listOf(1.0, 2.5, 5.0, 7.75).random(random)
                configure(players = players, buyIn = 20.0, bounty = bounty, weights = listOf(1))
                tournamentPreferences.setBountyMode(mode)
                val viewModel = newViewModel()
                val context = { "$mode session $session: ${viewModel.uiState.value.eliminationOrder}" }

                repeat(random.nextInt(3, ACTIONS)) {
                    randomAction(viewModel, random)
                    val state = viewModel.uiState.value
                    val owed = (1..players).sumOf { id -> viewModel.payOutSheet(id).owed.winningsCents }
                    assertTrue(context(), owed <= state.payableCents)
                }
                while (viewModel.uiState.value.activePlayers > 1) {
                    val stillIn = viewModel.uiState.value.players.filterNot { it.out }.map { it.id }
                    val target = stillIn.random(random)
                    viewModel.knockOut(target, (stillIn - target).randomOrNull(random)?.takeIf { random.nextBoolean() })
                }
                val sheets = (1..players).map { viewModel.payOutSheet(it).owed }
                val state = viewModel.uiState.value
                val bounties = sheets.sumOf { it.knockoutBountyCents + it.kingsBountyCents + it.unclaimedBountyCents }
                assertEquals(context(), state.pool.bountyCents, bounties)
                assertEquals(context(), state.payableCents, sheets.sumOf { it.winningsCents })
            }
        }
    }

    private fun BankTestKit.randomAction(viewModel: BankViewModel, random: Random) {
        val state = viewModel.uiState.value
        val stillIn = state.players.filterNot { it.out }.map { it.id }
        val out = state.players.filter { it.out }.map { it.id }
        when (random.nextInt(6)) {
            0 -> out.randomOrNull(random)?.let { viewModel.send(BankIntent.BringBack(it)) }
            1 -> viewModel.send(BankIntent.Undo)
            else -> if (stillIn.size > 1) {
                val target = stillIn.random(random)
                val credit = when (random.nextInt(4)) {
                    0 -> null
                    1 -> out.randomOrNull(random)
                    else -> (stillIn - target).random(random)
                }
                viewModel.knockOut(target, credit)
            }
        }
    }

    private fun money(cents: Long) = FormatUtils.formatMoney(cents)

    private companion object {
        const val SESSIONS = 12
        const val ACTIONS = 16
    }
}
