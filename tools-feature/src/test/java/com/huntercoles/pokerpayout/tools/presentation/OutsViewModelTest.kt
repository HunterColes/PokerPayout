package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.tools.table.Chance
import com.huntercoles.pokerpayout.tools.table.Street
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Outs and pot odds (S20): a flush draw on the flop to start, outs kept in range, pot odds once both are typed. */
class OutsViewModelTest {

    private val memory = TableToolsMemory()

    private fun viewModel() = OutsViewModel(memory)

    @Test
    fun `it opens on a flush draw on the flop`() {
        val state = viewModel().uiState.value
        assertEquals(Street.Flop, state.street)
        assertEquals(9, state.outs)
        assertEquals(Chance(378, 1_081), state.byRiver)
        assertEquals(Chance(9, 47), state.nextCard)
        assertNull(state.equityNeeded)
    }

    @Test
    fun `outs stay between 1 and 25, and the turn has one card to come`() {
        val vm = viewModel()
        vm.acceptIntent(OutsIntent.SetOuts(0))
        assertEquals(1, vm.uiState.value.outs)
        vm.acceptIntent(OutsIntent.SetOuts(40))
        assertEquals(25, vm.uiState.value.outs)
        vm.acceptIntent(OutsIntent.SetOuts(8))
        vm.acceptIntent(OutsIntent.SetStreet(Street.Turn))
        assertEquals(Chance(8, 46), vm.uiState.value.byRiver)
    }

    @Test
    fun `the equity a call needs comes once the pot and the call are typed, and is kept`() {
        val vm = viewModel()
        vm.acceptIntent(OutsIntent.SetPot(300))
        assertNull(vm.uiState.value.equityNeeded)
        vm.acceptIntent(OutsIntent.SetCall(100))
        assertEquals(25.0, vm.uiState.value.equityNeeded!!, 1e-12)
        assertEquals(vm.uiState.value, viewModel().uiState.value)
    }
}
