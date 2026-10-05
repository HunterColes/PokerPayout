package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * The odds ViewModel owns the calculation: it runs in viewModelScope, streams engine
 * snapshots into the state, clears them on any input change and surfaces engine errors.
 * Main and the engine share one test dispatcher, so every run is deterministic.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OddsCalculatorViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(main)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        vararg hands: String,
        board: String = "",
        settings: OddsSettings = OddsSettings(),
    ) = OddsCalculatorViewModel(fakePrefs(hands.toList(), board), OddsEngine(main), settings)

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    @Test
    fun `calculate streams exact odds into the state`() = runVmTest {
        val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
        assertTrue(vm.uiState.value.canCalculate)

        vm.acceptIntent(OddsCalculatorIntent.Calculate)
        assertTrue(vm.uiState.value.isSimulating)
        assertNull(vm.uiState.value.result)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isSimulating)
        assertNull(state.error)
        val result = state.result
        assertNotNull(result)
        assertTrue(result!!.exact)
        assertEquals(990L, result.deals)
        assertEquals(56.06, result.players[0].winPct, 0.005)
        assertEquals(43.94, result.players[1].winPct, 0.005)
    }

    @Test
    fun `adding a board card clears the old results immediately (B11)`() = runVmTest {
        val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
        vm.acceptIntent(OddsCalculatorIntent.Calculate)
        testScheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.result)

        vm.acceptIntent(OddsCalculatorIntent.ShowCardPickerForCommunity)
        vm.acceptIntent(OddsCalculatorIntent.CardSelected("9c"))

        assertEquals(4, vm.uiState.value.communityCards.size)
        assertNull(vm.uiState.value.result)
        assertFalse(vm.uiState.value.isSimulating)
    }

    @Test
    fun `every other input change clears results too`() = runVmTest {
        val changes = listOf<Pair<String, OddsCalculatorIntent>>(
            "remove a hole card" to OddsCalculatorIntent.PlayerCardRemoved(playerId = 1, cardIndex = 0),
            "remove a board card" to OddsCalculatorIntent.CommunityCardRemoved(cardIndex = 2),
            "add a player" to OddsCalculatorIntent.PlayerCountChanged(3),
            "reset" to OddsCalculatorIntent.ConfirmReset,
        )
        for ((label, change) in changes) {
            val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
            vm.acceptIntent(OddsCalculatorIntent.Calculate)
            testScheduler.advanceUntilIdle()
            assertNotNull(vm.uiState.value.result, label)

            vm.acceptIntent(change)
            assertNull(vm.uiState.value.result, "$label should clear the results")
        }
    }

    @Test
    fun `dragging the slider over the same player count keeps the results`() = runVmTest {
        val vm = viewModel("As Ks", "Qh Qd", board = "Js Ts 2c")
        vm.acceptIntent(OddsCalculatorIntent.Calculate)
        testScheduler.advanceUntilIdle()
        vm.acceptIntent(OddsCalculatorIntent.PlayerCountChanged(2))
        assertNotNull(vm.uiState.value.result)
    }

    @Test
    fun `Monte Carlo snapshots stream in until the run completes`() = runVmTest {
        val vm = viewModel("As Ks", "Qh Qd", settings = OddsSettings(maxSamples = 50_000, exactBudget = 0, seed = 7))
        val seen = mutableListOf<OddsCalculatorUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { seen += it } }

        vm.acceptIntent(OddsCalculatorIntent.Calculate)
        testScheduler.advanceUntilIdle()

        val snapshots = seen.mapNotNull { s -> s.result?.let { it.deals to s.isSimulating } }.distinct()
        assertEquals(
            listOf(2_048L to true, 6_144L to true, 14_336L to true, 30_720L to true, 50_000L to false),
            snapshots,
        )
        val final = vm.uiState.value.result!!
        assertFalse(final.exact)
        assertTrue(final.complete)
        assertEquals(46.21, final.players[0].equityPct, 4 * final.players[0].equityStdErr) // exact: 46.2145
    }

    @Test
    fun `an input change cancels the run in flight and its numbers never come back`() = runVmTest {
        // Unbounded Monte Carlo: if cancellation failed, advanceUntilIdle would never return.
        val vm = viewModel("As Ks", "Qh Qd", settings = OddsSettings(maxSamples = Int.MAX_VALUE, exactBudget = 0, seed = 1))
        val afterChange = mutableListOf<OddsCalculatorUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.first { it.result != null }
            vm.acceptIntent(OddsCalculatorIntent.PlayerCountChanged(3))
            vm.uiState.collect { afterChange += it }
        }

        vm.acceptIntent(OddsCalculatorIntent.Calculate)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.playerCount)
        assertNull(state.result)
        assertFalse(state.isSimulating)
        assertTrue(afterChange.isNotEmpty() && afterChange.all { it.result == null })
    }

    @Test
    fun `engine errors are shown instead of swallowed`() = runVmTest {
        val vm = viewModel("As Ks", "As Qd") // the ace of spades twice, e.g. from stale saved state
        vm.acceptIntent(OddsCalculatorIntent.Calculate)
        testScheduler.advanceUntilIdle()

        assertEquals("As is used twice.", vm.uiState.value.error)
        assertNull(vm.uiState.value.result)
        assertFalse(vm.uiState.value.isSimulating)

        vm.acceptIntent(OddsCalculatorIntent.PlayerCardRemoved(playerId = 2, cardIndex = 0))
        assertNull(vm.uiState.value.error, "fixing the input clears the error")
    }

    @Test
    fun `calculate needs two cards per player and a full street on the board`() = runVmTest {
        for (vm in listOf(viewModel("As Ks", "Qh"), viewModel("As Ks", "Qh Qd", board = "Js Ts"))) {
            assertFalse(vm.uiState.value.canCalculate)
            vm.acceptIntent(OddsCalculatorIntent.Calculate)
            testScheduler.advanceUntilIdle()
            assertFalse(vm.uiState.value.isSimulating)
            assertNull(vm.uiState.value.result)
        }
    }

    /** In-memory stand-in for the SharedPreferences-backed store. Hands use "As Ks" notation. */
    private fun fakePrefs(hands: List<String>, board: String): OddsCalculatorPreferences {
        val cards = mutableMapOf<Int, String>()
        hands.forEachIndexed { i, h -> cards[i + 1] = h.split(' ').filter { it.isNotBlank() }.joinToString(",") }
        var count = hands.size
        var community = board.split(' ').filter { it.isNotBlank() }.joinToString(",")
        return mockk {
            every { getPlayerCount() } answers { count }
            every { setPlayerCount(any()) } answers { count = firstArg() }
            every { getPlayerCards(any()) } answers { cards[firstArg()] ?: "" }
            every { setPlayerCards(any(), any()) } answers { cards[firstArg()] = secondArg() }
            every { getCommunityCards() } answers { community }
            every { setCommunityCards(any()) } answers { community = firstArg() }
            every { resetAllData() } answers { cards.clear(); count = 2; community = "" }
            every { isInDefaultState() } answers { count == 2 && cards.values.all { it.isEmpty() } && community.isEmpty() }
        }
    }
}
