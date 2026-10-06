package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * The odds ViewModel: the keypad, seats, live odds, New hand with Undo, and the run-it-out state
 * machine. Main and the engine share one test dispatcher and virtual time, so every run is
 * deterministic and the debounce, the snackbar's 8 s window and Monte Carlo runs need no real time.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class OddsCalculatorViewModelTest {

    private val main = StandardTestDispatcher()
    private lateinit var prefs: FakePrefs
    private val snackbars = SnackbarController()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        prefs = FakePrefs()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        vararg hands: String,
        board: String = "",
        settings: OddsSettings = OddsSettings(),
        seed: Long = 42,
    ): OddsCalculatorViewModel {
        if (hands.isNotEmpty()) prefs.seed(hands.toList(), board)
        return OddsCalculatorViewModel(prefs.mock, OddsEngine(main), settings, snackbars, { seed }, messages)
    }

    private val messages = mockk<OddsMessages> {
        every { newHand } returns "New hand"
        every { tableCleared } returns "Table cleared"
        every { undo } returns "Undo"
        every { cantCalculate } returns "Can't"
        every { calculationFailed(any()) } answers { "Failed: ${firstArg<String>()}" }
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    private fun OddsCalculatorViewModel.send(vararg intents: OddsCalculatorIntent) = intents.forEach(::acceptIntent)

    /** Types cards on the keypad, rank then suit: "As Kd 10h". */
    private fun OddsCalculatorViewModel.type(cards: String) = Cards.parseAll(cards).forEach { card ->
        send(OddsCalculatorIntent.PickRank(card / 4), OddsCalculatorIntent.PickSuit(card % 4))
    }

    private val OddsCalculatorViewModel.state get() = uiState.value

    private fun card(text: String) = Cards.parse(text)

    // ------------------------------------------------------------------ live odds

    @Nested
    inner class LiveOdds {
        @Test
        fun `the mockup hand is worked out exactly - 56_06 against 43_94`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            assertTrue(vm.state.isCalculating)
            testScheduler.advanceUntilIdle()

            val result = vm.state.result!!
            assertFalse(vm.state.isCalculating)
            assertNull(vm.state.error)
            assertTrue(result.exact)
            assertEquals(990L, result.deals)
            assertEquals(56.06, result.players[0].winPct, 0.005)
            assertEquals(43.94, result.players[1].winPct, 0.005)
            assertEquals(0.0, result.players[0].tiePct)

            val breakdown = vm.state.breakdown!!
            assertEquals(16, breakdown.leadCount(0), "16 of 45 turn cards put Player 1 ahead")
            assertEquals(45, breakdown.cards.size)
        }

        @Test
        fun `adding a card clears the stale odds at once, then works out the new ones (B11)`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            testScheduler.advanceUntilIdle()
            assertNotNull(vm.state.result)

            vm.send(OddsCalculatorIntent.SelectSlot(SlotRef.Board(3)), OddsCalculatorIntent.PlaceCard(card("7h")))
            assertNull(vm.state.result, "the flop's numbers must not survive the turn")
            assertNull(vm.state.breakdown)
            assertTrue(vm.state.isCalculating)

            testScheduler.advanceUntilIdle()
            assertEquals(36.36, vm.state.result!!.players[0].equityPct, 0.005) // 16 of 44
            assertEquals(44L, vm.state.result!!.deals)
        }

        @Test
        fun `every change to the table clears the results`() = runVmTest {
            val changes = listOf(
                "add a player" to listOf(OddsCalculatorIntent.AddPlayer),
                "remove a card" to listOf(OddsCalculatorIntent.SelectSlot(SlotRef.Hole(1, 0)), OddsCalculatorIntent.Backspace),
                "swap" to listOf(OddsCalculatorIntent.Swap(0, 1)),
                "new hand" to listOf(OddsCalculatorIntent.NewHand),
                "clear a hand" to listOf(OddsCalculatorIntent.ClearHand(1)),
            )
            for ((label, intents) in changes) {
                val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
                testScheduler.advanceUntilIdle()
                assertNotNull(vm.state.result, label)
                vm.send(*intents.toTypedArray())
                assertNull(vm.state.result, "$label should clear the results")
                vm.send(OddsCalculatorIntent.CloseKeypad)
            }
        }

        @Test
        fun `keypad moves that change no card keep the results`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            testScheduler.advanceUntilIdle()
            vm.send(OddsCalculatorIntent.SelectSlot(SlotRef.Board(3)), OddsCalculatorIntent.PickRank(5))
            vm.send(OddsCalculatorIntent.CloseKeypad)
            assertNotNull(vm.state.result)
        }

        @Test
        fun `a missing card is dealt at random, so the odds are an estimate`() = runVmTest {
            val vm = viewModel("As Ks", "Qh", settings = OddsSettings(maxSamples = 20_000, seed = 3))
            testScheduler.advanceUntilIdle()
            val result = vm.state.result!!
            assertFalse(result.exact)
            assertTrue(result.complete)
            assertNull(vm.state.breakdown, "no next-card grid while a hand is unknown")
        }

        @Test
        fun `an empty table works nothing out`() = runVmTest {
            val vm = viewModel()
            testScheduler.advanceUntilIdle()
            assertNull(vm.state.result)
            assertFalse(vm.state.isCalculating)
        }

        @Test
        fun `an input change cancels the run in flight and its numbers never come back`() = runVmTest {
            // Unbounded Monte Carlo: if cancellation failed, advanceUntilIdle would never return.
            // (A new hand leaves nothing to work out, so no new run starts.)
            val vm = viewModel("As Ks", "Qh Qd", settings = OddsSettings(maxSamples = Int.MAX_VALUE, exactBudget = 0, seed = 1))
            val afterChange = mutableListOf<OddsCalculatorUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.first { it.result != null }
                vm.acceptIntent(OddsCalculatorIntent.NewHand)
                vm.uiState.collect { afterChange += it }
            }
            testScheduler.advanceUntilIdle()

            assertTrue(vm.state.table.isEmpty)
            assertNull(vm.state.result)
            assertFalse(vm.state.isCalculating)
            assertTrue(afterChange.isNotEmpty() && afterChange.all { it.result == null })
        }

        @Test
        fun `engine errors are shown, and fixing the cards clears them`() = runVmTest {
            val vm = viewModel("As Ks", "As Qd") // the ace of spades twice, from an old save
            testScheduler.advanceUntilIdle()
            assertEquals("As is used twice.", vm.state.error)
            assertNull(vm.state.result)
            assertFalse(vm.state.canRunItOut)

            vm.send(OddsCalculatorIntent.SelectSlot(SlotRef.Hole(1, 0)), OddsCalculatorIntent.Backspace)
            assertNull(vm.state.error)
        }
    }

    // ------------------------------------------------------------------ keypad

    @Nested
    inner class Keypad {
        @Test
        fun `a fresh table opens the keypad on Player 1's first card`() = runVmTest {
            val vm = viewModel()
            assertEquals(SlotRef.Hole(0, 0), vm.state.keypad.target)
        }

        @Test
        fun `typing fills the seats in order, then the flop, turn and river, then closes`() = runVmTest {
            val vm = viewModel()
            vm.type("As")
            assertEquals(SlotRef.Hole(0, 1), vm.state.keypad.target)
            assertNull(vm.state.keypad.rank, "the rank is used up by the card")
            vm.type("Ks")
            assertEquals(SlotRef.Hole(1, 0), vm.state.keypad.target)
            vm.type("Qh Qd")
            assertEquals(SlotRef.Board(0), vm.state.keypad.target)
            vm.type("Js Ts 2c")
            assertEquals(SlotRef.Board(3), vm.state.keypad.target)
            vm.type("7h")
            assertEquals(SlotRef.Board(4), vm.state.keypad.target)
            vm.type("3d")
            assertNull(vm.state.keypad.target, "every slot is full: the keypad closes")

            val table = vm.state.table
            assertEquals(Cards.parseAll("As Ks"), table.seats[0].known)
            assertEquals(Cards.parseAll("Qh Qd"), table.seats[1].known)
            assertEquals(Cards.parseAll("Js Ts 2c 7h 3d"), table.boardCards)
            assertEquals(Street.RIVER, table.street)
        }

        @Test
        fun `a card on the table can't be picked again`() = runVmTest {
            val vm = viewModel()
            vm.type("As")
            vm.send(OddsCalculatorIntent.PickRank(Cards.ACE), OddsCalculatorIntent.PickSuit(3)) // A♠ again
            assertEquals(SlotRef.Hole(0, 1), vm.state.keypad.target, "nothing placed, the keypad waits")
            assertEquals(listOf(card("As")), vm.state.table.seats[0].known)

            // With all four aces out, the ace key does nothing.
            vm.type("Ah Ad Ac")
            vm.send(OddsCalculatorIntent.PickRank(Cards.ACE))
            assertNull(vm.state.keypad.rank)
        }

        @Test
        fun `tapping a card re-types it, and the turn slot aims at the flop first`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            vm.send(OddsCalculatorIntent.SelectSlot(SlotRef.Hole(0, 1)))
            vm.type("Kh")
            assertEquals(Cards.parseAll("As Kh"), vm.state.table.seats[0].known)

            val empty = viewModel("As Ks", "Qh Qd")
            empty.send(OddsCalculatorIntent.SelectSlot(SlotRef.Board(3)))
            assertEquals(SlotRef.Board(0), empty.state.keypad.target)
        }

        @Test
        fun `backspace clears the slot, or steps back to the card before`() = runVmTest {
            val vm = viewModel()
            vm.type("As Ks Qh")
            assertEquals(SlotRef.Hole(1, 1), vm.state.keypad.target)
            vm.send(OddsCalculatorIntent.Backspace) // the slot is empty: clear Q♥ before it
            assertEquals(SlotRef.Hole(1, 0), vm.state.keypad.target)
            assertTrue(vm.state.table.seats[1].isUnknown)
            vm.send(OddsCalculatorIntent.SelectSlot(SlotRef.Hole(0, 0)), OddsCalculatorIntent.Backspace)
            assertEquals(listOf(card("Ks")), vm.state.table.seats[0].known)
            assertEquals(SlotRef.Hole(0, 0), vm.state.keypad.target)
        }

        @Test
        fun `random fills the seat's missing cards and moves to the next seat`() = runVmTest {
            val vm = viewModel()
            vm.type("As Ks")
            vm.send(OddsCalculatorIntent.RandomHand)
            assertEquals(listOf(SlotValue.Random, SlotValue.Random), vm.state.table.seats[1].cards)
            assertEquals(SlotRef.Board(0), vm.state.keypad.target, "the random hand's slots are skipped")
            assertFalse(vm.state.canRunItOut, "a random hand can't be run out")

            // The board can't be random.
            vm.send(OddsCalculatorIntent.RandomHand)
            assertEquals(SlotValue.Empty, vm.state.table.board[0])
        }

        @Test
        fun `done closes the keypad`() = runVmTest {
            val vm = viewModel()
            vm.send(OddsCalculatorIntent.CloseKeypad)
            assertFalse(vm.state.keypad.isOpen)
            vm.send(OddsCalculatorIntent.PickRank(3))
            assertNull(vm.state.keypad.rank, "a closed keypad takes no keys")
        }
    }

    // ------------------------------------------------------------------ seats

    @Nested
    inner class Seats {
        @Test
        fun `add player up to ten, aiming the keypad at the new seat`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd")
            vm.send(OddsCalculatorIntent.AddPlayer)
            assertEquals(3, vm.state.table.seats.size)
            assertEquals(SlotRef.Hole(2, 0), vm.state.keypad.target)
            repeat(10) { vm.send(OddsCalculatorIntent.AddPlayer) }
            assertEquals(OddsTable.MAX_SEATS, vm.state.table.seats.size)
        }

        @Test
        fun `remove player down to two`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", "8c 9c")
            vm.send(OddsCalculatorIntent.RemovePlayer(1))
            assertEquals(listOf(Cards.parseAll("As Ks"), Cards.parseAll("8c 9c")), vm.state.table.seats.map { it.known })
            vm.send(OddsCalculatorIntent.RemovePlayer(0))
            assertEquals(2, vm.state.table.seats.size, "two players is the minimum")
        }

        @Test
        fun `fold makes a seat's cards dead and the keypad skips it`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", "Jd Jc", board = "Js Ts 2c")
            vm.send(OddsCalculatorIntent.Fold(2))
            testScheduler.advanceUntilIdle()
            val result = vm.state.result!!
            assertTrue(result.players[2].folded)
            assertEquals(0.0, result.players[2].equityPct)
            assertEquals(100.0, result.players[0].equityPct + result.players[1].equityPct, 1e-9)
            assertEquals(903L, result.deals, "the folded jacks are dead: 43 cards left, C(43, 2) runouts")
            assertTrue(vm.state.table.order().none { it is SlotRef.Hole && it.seat == 2 })

            vm.send(OddsCalculatorIntent.Fold(0))
            assertFalse(vm.state.table.seats[0].folded, "two players must stay in the hand")
            vm.send(OddsCalculatorIntent.Fold(2, folded = false))
            assertFalse(vm.state.table.seats[2].folded)
        }

        @Test
        fun `swap exchanges two seats' hands`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            vm.send(OddsCalculatorIntent.Swap())
            testScheduler.advanceUntilIdle()
            assertEquals(Cards.parseAll("Qh Qd"), vm.state.table.seats[0].known)
            assertEquals(43.94, vm.state.result!!.players[0].winPct, 0.005)
        }

        @Test
        fun `new hand clears every card but keeps the seats, and Undo brings the hand back`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", "8c 9c", board = "Js Ts 2c")
            vm.send(OddsCalculatorIntent.Fold(2))
            val before = vm.state.table
            vm.send(OddsCalculatorIntent.NewHand)
            assertEquals(3, vm.state.table.seats.size)
            assertTrue(vm.state.table.seats.all { it.isUnknown && !it.folded })
            assertTrue(vm.state.table.boardCards.isEmpty())
            assertEquals(SlotRef.Hole(0, 0), vm.state.keypad.target)

            testScheduler.runCurrent()
            val snackbar = snackbars.hostState.currentSnackbarData!!
            assertEquals("New hand", snackbar.visuals.message)
            assertEquals("Undo", snackbar.visuals.actionLabel)
            snackbar.performAction()
            testScheduler.advanceUntilIdle()

            assertEquals(before, vm.state.table)
            assertTrue(vm.state.table.seats[2].folded)
            assertTrue(vm.state.result!!.exact, "and its odds are worked out again")
        }

        @Test
        fun `without Undo the new hand stays once the 8 second window is over`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            vm.send(OddsCalculatorIntent.NewHand)
            testScheduler.advanceUntilIdle() // virtual time runs past the window
            assertNull(snackbars.hostState.currentSnackbarData)
            assertTrue(vm.state.table.isEmpty)
        }

        @Test
        fun `clear table goes back to two empty seats, with Undo`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd", "8c 9c")
            vm.send(OddsCalculatorIntent.ClearTable)
            assertEquals(OddsTable(), vm.state.table)
            testScheduler.runCurrent()
            snackbars.hostState.currentSnackbarData!!.performAction()
            testScheduler.advanceUntilIdle()
            assertEquals(3, vm.state.table.seats.size)
        }

        @Test
        fun `the table and the four-colour deck are saved and come back`() = runVmTest {
            val vm = viewModel()
            vm.type("As Ks")
            vm.send(OddsCalculatorIntent.RandomHand, OddsCalculatorIntent.AddPlayer)
            vm.type("8c 9c")
            vm.send(OddsCalculatorIntent.Fold(2), OddsCalculatorIntent.SetFourColourDeck(true))
            vm.type("Js Ts 2c")

            val again = viewModel()
            assertEquals(vm.state.table, again.state.table)
            assertTrue(again.state.fourColourDeck)
            assertNull(again.state.keypad.target, "a saved hand opens with the keypad closed")
        }
    }

    // ------------------------------------------------------------------ run it out

    @Nested
    inner class RunItOut {
        private fun TestScope.started(seed: Long = 42): OddsCalculatorViewModel {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c", seed = seed)
            testScheduler.advanceUntilIdle()
            assertTrue(vm.state.canRunItOut)
            vm.send(OddsCalculatorIntent.RunItOut)
            testScheduler.advanceUntilIdle()
            return vm
        }

        @Test
        fun `starting shows the flop with the preflop and flop equity`() = runVmTest {
            val run = started().state.runOut!!
            assertEquals(Street.FLOP, run.street)
            assertEquals(listOf(Street.PREFLOP, Street.FLOP), run.history.map { it.street })
            assertEquals(46.21, run.history[0].equityPct[0], 0.005) // A♠K♠ v Q♥Q♦ preflop, exact
            assertEquals(56.06, run.history[1].equityPct[0], 0.005)
            assertEquals(Cards.parseAll("7c 4d"), run.runout, "seed 42, run 0 (pinned in OddsInsightsTest)")
            assertEquals(16, run.outs!!.leadCount(0))
            assertEquals(Street.TURN, run.nextStreet)
        }

        @Test
        fun `deal next turns up the turn, then the river, then nothing`() = runVmTest {
            val vm = started()
            vm.send(OddsCalculatorIntent.DealNext)
            assertTrue(vm.state.runOut!!.isDealing)
            testScheduler.advanceUntilIdle()
            var run = vm.state.runOut!!
            assertEquals(Cards.parseAll("Js Ts 2c 7c"), run.board)
            assertEquals(Street.TURN, run.street)
            assertEquals(36.36, run.equity[0], 0.005, "the 7♣ is a blank: 16 of 44")
            assertEquals(-19.70, run.delta[0], 0.005)
            assertEquals(19.70, run.delta[1], 0.005)
            assertEquals(16, run.outs!!.leadCount(0), "Player 1 needs one of 16 rivers")

            vm.send(OddsCalculatorIntent.DealNext)
            testScheduler.advanceUntilIdle()
            run = vm.state.runOut!!
            assertEquals(Cards.parseAll("Js Ts 2c 7c 4d"), run.board)
            assertTrue(run.isComplete)
            assertEquals(listOf(0.0, 100.0), run.equity)
            assertEquals(listOf(1), run.winners)
            assertEquals(1, run.leaderBeforeRiver, "Player 2 holds")
            assertNull(run.outs)

            vm.send(OddsCalculatorIntent.DealNext)
            testScheduler.advanceUntilIdle()
            assertEquals(run, vm.state.runOut, "nothing left to deal")
        }

        @Test
        fun `taps while a street is being worked out are ignored`() = runVmTest {
            val vm = started()
            vm.send(OddsCalculatorIntent.DealNext, OddsCalculatorIntent.DealNext)
            testScheduler.advanceUntilIdle()
            assertEquals(Street.TURN, vm.state.runOut!!.street)
        }

        @Test
        fun `run again deals a fresh runout from the flop`() = runVmTest {
            val vm = started()
            vm.send(OddsCalculatorIntent.DealNext)
            testScheduler.advanceUntilIdle()
            vm.send(OddsCalculatorIntent.RunAgain)
            testScheduler.advanceUntilIdle()
            val run = vm.state.runOut!!
            assertEquals(1, run.run)
            assertEquals(0, run.dealt)
            assertEquals(Street.FLOP, run.street)
            assertEquals(2, run.history.size, "back to preflop and flop")
            assertNotEquals(Cards.parseAll("7c 4d"), run.runout)
        }

        @Test
        fun `run it twice deals two boards from one deck`() = runVmTest {
            val vm = started()
            vm.send(OddsCalculatorIntent.RunTwice)
            testScheduler.advanceUntilIdle()
            val twice = vm.state.runOut!!.twice!!
            assertEquals(2, twice.size)
            twice.forEach { board ->
                assertEquals(5, board.board.size)
                assertEquals(Cards.parseAll("Js Ts 2c"), board.board.take(3))
                assertEquals(100.0, board.equityPct.sum(), 1e-9)
            }
            assertTrue(twice[0].board.drop(3).intersect(twice[1].board.drop(3).toSet()).isEmpty())
            assertTrue(vm.state.runOut!!.isComplete)

            vm.send(OddsCalculatorIntent.RunAgain)
            testScheduler.advanceUntilIdle()
            assertNull(vm.state.runOut!!.twice)
            assertEquals(3, vm.state.runOut!!.run, "run it twice used runs 1 and 2")
        }

        @Test
        fun `the same seed replays the same cards`() = runVmTest {
            fun TestScope.river(seed: Long): List<Int> {
                val vm = started(seed)
                vm.send(OddsCalculatorIntent.DealNext)
                testScheduler.advanceUntilIdle()
                vm.send(OddsCalculatorIntent.DealNext)
                testScheduler.advanceUntilIdle()
                return vm.state.runOut!!.board
            }
            assertEquals(river(7), river(7))
            assertNotEquals(river(7), river(8))
        }

        @Test
        fun `run it out needs every hand known and leaves the table untouched`() = runVmTest {
            val partial = viewModel("As Ks", "Qh", board = "Js Ts 2c")
            testScheduler.advanceUntilIdle()
            assertFalse(partial.state.canRunItOut)
            partial.send(OddsCalculatorIntent.RunItOut)
            testScheduler.advanceUntilIdle()
            assertNull(partial.state.runOut)

            val vm = started()
            val table = vm.state.table
            vm.send(OddsCalculatorIntent.DealNext, OddsCalculatorIntent.SetRunOutLandscape(true))
            testScheduler.advanceUntilIdle()
            assertTrue(vm.state.runOut!!.landscape)
            vm.send(OddsCalculatorIntent.ExitRunItOut)
            assertNull(vm.state.runOut)
            assertEquals(table, vm.state.table)
            assertNotNull(vm.state.result, "the odds are still there")
        }

        @Test
        fun `run it out from preflop deals the flop in one go`() = runVmTest {
            val vm = viewModel("As Ks", "Qh Qd")
            testScheduler.advanceUntilIdle()
            vm.send(OddsCalculatorIntent.CloseKeypad, OddsCalculatorIntent.RunItOut)
            testScheduler.advanceUntilIdle()
            assertEquals(Street.FLOP, vm.state.runOut!!.nextStreet)
            vm.send(OddsCalculatorIntent.DealNext)
            testScheduler.advanceUntilIdle()
            assertEquals(3, vm.state.runOut!!.board.size)
            assertEquals(listOf(Street.PREFLOP, Street.FLOP), vm.state.runOut!!.history.map { it.street })
        }
    }

    /** In-memory stand-in for the SharedPreferences store. */
    private class FakePrefs {
        val cards = mutableMapOf<Int, String>()
        val folded = mutableMapOf<Int, Boolean>()
        var count = 2
        var community = ""
        var fourColour = false

        fun seed(hands: List<String>, board: String) {
            cards.clear()
            hands.forEachIndexed { i, h -> cards[i + 1] = h.split(' ').filter { it.isNotBlank() }.joinToString(",") }
            count = hands.size
            community = board.split(' ').filter { it.isNotBlank() }.joinToString(",")
        }

        val mock: OddsCalculatorPreferences = mockk {
            every { getPlayerCount() } answers { count }
            every { setPlayerCount(any()) } answers { count = firstArg() }
            every { getPlayerCards(any()) } answers { cards[firstArg()] ?: "" }
            every { setPlayerCards(any(), any()) } answers { cards[firstArg()] = secondArg() }
            every { getPlayerFolded(any()) } answers { folded[firstArg()] ?: false }
            every { setPlayerFolded(any(), any()) } answers { folded[firstArg()] = secondArg() }
            every { getCommunityCards() } answers { community }
            every { setCommunityCards(any()) } answers { community = firstArg() }
            every { fourColourDeck } answers { fourColour }
            every { fourColourDeck = any() } answers { fourColour = firstArg() }
        }
    }
}
