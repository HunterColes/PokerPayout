package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.PlayerNamesProvider
import com.huntercoles.pokerpayout.tools.seats.SeatDraw
import com.huntercoles.pokerpayout.tools.seats.SeatDrawSeeds
import com.huntercoles.pokerpayout.tools.seats.SeatDrawStore
import com.huntercoles.pokerpayout.tools.seats.SeatDrawer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The seat draw ViewModel (PP-036) on a real store over in-memory preferences, a fake Bank, fixed
 * seeds and virtual time: players start as the Bank's, can be changed for the draw, every draw is
 * saved (a new ViewModel on the same store is a process death), and a redraw offers Undo.
 */
class SeatDrawViewModelTest {

    private val main = StandardTestDispatcher()
    private val stores = mutableMapOf<String, SharedPreferences>()
    private val snackbars = SnackbarController()
    private lateinit var context: Context
    private var bank = listOf("Dana", "Marcus", "Priya", "Theo", "Jo", "Sam", "Alex", "Rita", "Ben", "Kim")
    private var nextSeed = 100L
    private val seeds = SeatDrawSeeds { nextSeed++ }
    private val messages = mockk<SeatDrawMessages> {
        every { seatsRedrawn } returns "Seats redrawn"
        every { buttonDealtAgain } returns "Button dealt again"
        every { undo } returns "Undo"
        every { defaultName(any()) } answers { "Player ${firstArg<Int>()}" }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        context = mockk {
            every { getSharedPreferences(any(), any()) } answers { stores.getOrPut(firstArg()) { InMemoryPreferences() } }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        SeatDrawViewModel(SeatDrawStore(context), PlayerNamesProvider { bank }, seeds, snackbars, messages)

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    private val SeatDrawViewModel.state get() = uiState.value

    private fun SeatDrawViewModel.draw(): SeatDraw = checkNotNull(state.draw) { "nothing drawn" }

    @Test
    fun `it opens on the Bank's players, nine a table, with nothing drawn`() {
        val vm = viewModel()
        assertEquals(bank, vm.state.players)
        assertEquals(bank, vm.state.seatNames)
        assertTrue(vm.state.fromBank)
        assertEquals(9, vm.state.seatsPerTable)
        assertEquals(listOf(5, 5), vm.state.tableSizes)
        assertNull(vm.state.draw)
    }

    @Test
    fun `with nobody in the Bank it starts from Player 1 to Player 9`() {
        bank = emptyList()
        val vm = viewModel()
        assertEquals((1..9).map { "Player $it" }, vm.state.seatNames)
        assertEquals(listOf(9), vm.state.tableSizes)
    }

    @Test
    fun `drawing seats everyone once at balanced tables, and the draw outlives the process`() {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        val draw = vm.draw()
        assertEquals(bank.sorted(), draw.players.sorted())
        assertEquals(listOf(5, 5), draw.tables.map { it.seats.size })
        assertFalse(vm.state.drawIsStale)

        val reborn = viewModel()
        assertEquals(draw, reborn.state.draw, "saved, so process death keeps it")
        assertEquals(bank, reborn.state.players)
    }

    @Test
    fun `the same seed gives the same draw`() {
        nextSeed = 7
        val first = viewModel().apply { acceptIntent(SeatDrawIntent.DrawSeats) }.draw()
        nextSeed = 7
        val second = viewModel().apply { acceptIntent(SeatDrawIntent.DrawSeats) }.draw()
        assertEquals(first, second)
    }

    @Test
    fun `a count changes the players for this draw, new ones as Player N, and is kept`() {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.SetPlayerCount(12))
        assertEquals(bank + listOf("", ""), vm.state.players)
        assertEquals(bank + listOf("Player 11", "Player 12"), vm.state.seatNames)
        assertFalse(vm.state.fromBank)
        assertEquals(listOf(6, 6), vm.state.tableSizes)

        vm.acceptIntent(SeatDrawIntent.SetPlayerCount(3))
        assertEquals(bank.take(3), vm.state.players)
        vm.acceptIntent(SeatDrawIntent.SetPlayerCount(1))
        assertEquals(2, vm.state.players.size, "two at least")
        vm.acceptIntent(SeatDrawIntent.SetPlayerCount(500))
        assertEquals(SeatDrawer.MAX_PLAYERS, vm.state.players.size)

        val reborn = viewModel()
        assertEquals(SeatDrawer.MAX_PLAYERS, reborn.state.players.size)
        assertFalse(reborn.state.fromBank, "the changed list is saved")
    }

    @Test
    fun `names can be changed for the draw, a blank one is drawn as Player N, and the Bank's come back`() {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.EditNames(open = true))
        assertTrue(vm.state.editingNames)
        vm.acceptIntent(SeatDrawIntent.SetName(0, "Danielle"))
        vm.acceptIntent(SeatDrawIntent.SetName(1, "   "))
        vm.acceptIntent(SeatDrawIntent.SetName(99, "nobody"))
        assertEquals("Danielle", vm.state.seatNames[0])
        assertEquals("Player 2", vm.state.seatNames[1])
        assertEquals(bank.size, vm.state.players.size)
        assertEquals("Danielle", viewModel().state.players[0], "saved")

        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        assertFalse(vm.state.editingNames, "drawing puts the names away")
        assertTrue("Player 2" in vm.draw().players)

        vm.acceptIntent(SeatDrawIntent.UseBankNames)
        assertEquals(bank, vm.state.players)
        assertTrue(vm.state.fromBank)
        assertTrue(viewModel().state.fromBank, "following the Bank again is saved too")
        assertTrue(vm.state.drawIsStale, "the draw on screen seated other names")
    }

    @Test
    fun `seats per table stay between 3 and 10, are saved, and change the tables`() {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.SetSeatsPerTable(10))
        assertEquals(listOf(10), vm.state.tableSizes)
        vm.acceptIntent(SeatDrawIntent.SetSeatsPerTable(1))
        assertEquals(3, vm.state.seatsPerTable)
        vm.acceptIntent(SeatDrawIntent.SetSeatsPerTable(14))
        assertEquals(10, vm.state.seatsPerTable)
        vm.acceptIntent(SeatDrawIntent.SetSeatsPerTable(4))
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        assertEquals(listOf(4, 3, 3), vm.draw().tables.map { it.seats.size })
        assertEquals(4, viewModel().state.seatsPerTable)
    }

    @Test
    fun `the button is dealt only once seats are drawn, at every table, and is saved`() {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DealButton)
        assertNull(vm.state.draw, "no seats, no button")

        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        val seated = vm.draw()
        vm.acceptIntent(SeatDrawIntent.DealButton)
        val dealt = vm.draw()
        assertEquals(seated.players, dealt.players, "the seats stay")
        assertTrue(dealt.buttonDealt)
        dealt.tables.forEach { table ->
            val button = checkNotNull(table.button) { "table ${table.number} has no button" }
            assertEquals(table.seats.size, table.buttonCards!!.size)
            assertEquals(button.buttonSeat % table.seats.size + 1, button.smallBlindSeat)
        }
        assertTrue(vm.state.dealToAnimate > 0, "a fresh deal plays its animation")

        val reborn = viewModel()
        assertEquals(dealt, reborn.state.draw)
        assertEquals(0, reborn.state.dealToAnimate, "a saved deal doesn't play again")
    }

    @Test
    fun `a redraw applies at once, and Undo brings the last draw back`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        testScheduler.runCurrent()
        assertNull(snackbars.hostState.currentSnackbarData, "the first draw replaces nothing")
        vm.acceptIntent(SeatDrawIntent.DealButton)
        testScheduler.runCurrent()
        assertNull(snackbars.hostState.currentSnackbarData, "the first deal only adds to the draw")
        val before = vm.draw()

        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        testScheduler.runCurrent()
        val redrawn = vm.draw()
        assertNotEquals(before, redrawn)
        assertFalse(redrawn.buttonDealt, "new seats need a new deal")
        val snackbar = checkNotNull(snackbars.hostState.currentSnackbarData) { "no Undo offered" }
        assertEquals("Seats redrawn", snackbar.visuals.message)
        snackbar.performAction()
        testScheduler.advanceUntilIdle()

        assertEquals(before, vm.state.draw)
        assertEquals(before, viewModel().state.draw, "the undone draw is the saved one")
        assertEquals(0, vm.state.dealToAnimate, "Undo doesn't replay the deal")
    }

    @Test
    fun `dealing again offers Undo too`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        vm.acceptIntent(SeatDrawIntent.DealButton)
        val first = vm.draw()
        vm.acceptIntent(SeatDrawIntent.DealButton)
        testScheduler.runCurrent()
        assertNotEquals(first, vm.state.draw)
        val snackbar = snackbars.hostState.currentSnackbarData!!
        assertEquals("Button dealt again", snackbar.visuals.message)
        snackbar.performAction()
        testScheduler.advanceUntilIdle()
        assertEquals(first, vm.state.draw)
    }

    @Test
    fun `without Undo the redraw stays once the 8 second window is over`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        val redrawn = vm.draw()
        testScheduler.advanceUntilIdle() // virtual time runs past the window
        assertNull(snackbars.hostState.currentSnackbarData)
        assertEquals(redrawn, vm.state.draw)
        assertEquals(redrawn, viewModel().state.draw)
    }

    @Test
    fun `Undo goes back one draw, and a newer redraw takes the snackbar over`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        testScheduler.runCurrent()
        val second = vm.draw()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        testScheduler.runCurrent()
        val third = vm.draw()
        assertNotEquals(second, third)

        snackbars.hostState.currentSnackbarData!!.performAction()
        testScheduler.advanceUntilIdle()
        assertEquals(second, vm.state.draw, "back to the draw before the last")
    }

    @Test
    fun `changing the players after a draw marks it out of date until the next draw`() {
        val vm = viewModel()
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        assertFalse(vm.state.drawIsStale)
        vm.acceptIntent(SeatDrawIntent.SetPlayerCount(11))
        assertTrue(vm.state.drawIsStale)
        vm.acceptIntent(SeatDrawIntent.DrawSeats)
        assertFalse(vm.state.drawIsStale)
        assertEquals(11, vm.draw().players.size)
    }
}
