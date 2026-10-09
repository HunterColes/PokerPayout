package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.tools.table.Pot
import com.huntercoles.pokerpayout.tools.table.PotSplit
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The side pots ViewModel (S21): every change works the pots out again, players come and go
 * between 2 and 10, New hand clears the chips with Undo, and what was typed outlives the screen
 * (a new ViewModel on the same memory is the screen coming back after a tab switch).
 */
class SidePotsViewModelTest {

    private val main = StandardTestDispatcher()
    private val memory = TableToolsMemory()
    private val snackbars = SnackbarController()
    private val messages = mockk<TableToolMessages> {
        every { newHand } returns "New hand: chips cleared"
        every { undo } returns "Undo"
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(main)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SidePotsViewModel(memory, snackbars, messages)

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    private val SidePotsViewModel.state get() = uiState.value

    @Test
    fun `it opens on three players with nothing in`() {
        val state = viewModel().state
        assertEquals(List(3) { PotPlayer() }, state.players)
        assertEquals(PotSplit.Empty, state.split)
    }

    @Test
    fun `chips and folds make the pots as they are typed`() {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.SetChips(0, 100))
        vm.acceptIntent(SidePotsIntent.SetChips(1, 300))
        vm.acceptIntent(SidePotsIntent.SetChips(2, 300))
        assertEquals(listOf(300L, 400L), (vm.state.split as PotSplit.Pots).pots.map { it.chips })
        vm.acceptIntent(SidePotsIntent.SetFolded(2, true))
        val pots = (vm.state.split as PotSplit.Pots).pots
        assertEquals(listOf(Pot(300, 0, 100, listOf(0, 1, 2), listOf(0, 1)), Pot(400, 100, 300, listOf(1, 2), listOf(1))), pots)
        assertEquals(700L, vm.state.totalChips)
    }

    @Test
    fun `names are kept short, and an empty field is no chips`() {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.SetName(1, "Dana".repeat(10)))
        vm.acceptIntent(SidePotsIntent.SetChips(1, 50))
        vm.acceptIntent(SidePotsIntent.SetChips(1, null))
        assertEquals(PotPlayer(name = "Dana".repeat(10).take(24)), vm.state.players[1])
    }

    @Test
    fun `players come and go between 2 and 10`() {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.RemovePlayer(0))
        assertEquals(2, vm.state.players.size)
        assertFalse(vm.state.canRemove)
        vm.acceptIntent(SidePotsIntent.RemovePlayer(0))
        assertEquals(2, vm.state.players.size, "never fewer than two")
        repeat(12) { vm.acceptIntent(SidePotsIntent.AddPlayer) }
        assertEquals(10, vm.state.players.size, "never more than ten")
        assertFalse(vm.state.canAdd)
    }

    @Test
    fun `removing a player keeps the others as they were`() {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.SetName(0, "Dana"))
        vm.acceptIntent(SidePotsIntent.SetName(1, "Sam"))
        vm.acceptIntent(SidePotsIntent.SetChips(2, 75))
        vm.acceptIntent(SidePotsIntent.RemovePlayer(1))
        assertEquals(listOf(PotPlayer("Dana"), PotPlayer(chips = 75)), vm.state.players)
    }

    @Test
    fun `what was typed is there when the screen comes back`() {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.SetChips(0, 400))
        vm.acceptIntent(SidePotsIntent.SetFolded(1, true))
        assertEquals(vm.state, viewModel().state)
    }

    @Test
    fun `New hand clears the chips and folds, keeps the names, and Undo brings the hand back`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.SetName(0, "Dana"))
        vm.acceptIntent(SidePotsIntent.SetChips(0, 400))
        vm.acceptIntent(SidePotsIntent.SetFolded(1, true))
        val hand = vm.state
        vm.acceptIntent(SidePotsIntent.NewHand)
        testScheduler.runCurrent()
        assertEquals(listOf(PotPlayer("Dana"), PotPlayer(), PotPlayer()), vm.state.players)
        val snackbar = checkNotNull(snackbars.hostState.currentSnackbarData) { "no Undo offered" }
        assertEquals("New hand: chips cleared", snackbar.visuals.message)
        snackbar.performAction()
        testScheduler.advanceUntilIdle()
        assertEquals(hand, vm.state)
    }

    @Test
    fun `New hand with nothing in does nothing, and the cleared hand stays without Undo`() = runVmTest {
        val vm = viewModel()
        vm.acceptIntent(SidePotsIntent.NewHand)
        testScheduler.runCurrent()
        assertNull(snackbars.hostState.currentSnackbarData)
        vm.acceptIntent(SidePotsIntent.SetChips(0, 400))
        vm.acceptIntent(SidePotsIntent.NewHand)
        testScheduler.advanceUntilIdle() // the window closes
        assertTrue(vm.state.players.all { it.chips == null })
        assertEquals(vm.state, viewModel().state)
    }
}
