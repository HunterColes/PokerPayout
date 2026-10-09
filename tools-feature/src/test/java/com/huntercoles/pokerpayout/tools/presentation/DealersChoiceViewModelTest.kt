package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.tools.dealers.BuiltInGame
import com.huntercoles.pokerpayout.tools.dealers.DealersChoiceStore
import com.huntercoles.pokerpayout.tools.dealers.GameChoice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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
 * Dealer's choice on a real store over in-memory preferences and fixed seeds: the starting wheel,
 * games on and off, house games, and spins that never repeat the last game. A new ViewModel on the
 * same preferences is a process death.
 */
class DealersChoiceViewModelTest {

    private val stores = mutableMapOf<String, SharedPreferences>()
    private lateinit var context: Context
    private var nextSeed = 40L
    private val seeds = WheelSeeds { nextSeed++ }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = mockk {
            every { getSharedPreferences(any(), any()) } answers { stores.getOrPut(firstArg()) { InMemoryPreferences() } }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = DealersChoiceViewModel(DealersChoiceStore(context), seeds)

    private val DealersChoiceViewModel.state get() = uiState.value

    @Test
    fun `the first wheel has the nine classics, and nothing picked yet`() {
        val state = viewModel().state
        assertEquals(
            listOf(
                BuiltInGame.HoldEm, BuiltInGame.Omaha, BuiltInGame.BigO, BuiltInGame.Stud, BuiltInGame.Razz,
                BuiltInGame.TripleDraw, BuiltInGame.Badugi, BuiltInGame.Pineapple, BuiltInGame.CrazyPineapple,
            ),
            state.wheel.map { it.game },
        )
        assertEquals(BuiltInGame.entries.size, state.games.size)
        assertNull(state.pick)
        assertTrue(state.canSpin)
    }

    @Test
    fun `a spin picks a game on the wheel, saves it, and the next spin never repeats it`() {
        val vm = viewModel()
        var last: String? = null
        repeat(30) { spin ->
            vm.acceptIntent(DealersChoiceIntent.Spin)
            val pick = checkNotNull(vm.state.pick)
            assertTrue(pick.onWheel)
            assertNotEquals(last, pick.id)
            assertEquals(spin + 1, vm.state.spins)
            assertEquals(pick.id, vm.state.wheel[vm.state.pickIndex].id)
            last = pick.id
        }
        assertEquals(last, viewModel().state.pickId, "the last pick is saved")
    }

    @Test
    fun `the same seeds spin the same games`() {
        val first = viewModel().apply { repeat(5) { acceptIntent(DealersChoiceIntent.Spin) } }.state
        stores.clear()
        nextSeed = 40L
        val second = viewModel().apply { repeat(5) { acceptIntent(DealersChoiceIntent.Spin) } }.state
        assertEquals(first.pickId, second.pickId)
        assertEquals(first.landing, second.landing)
    }

    @Test
    fun `games go on and off the wheel, and the wheel is saved`() {
        val vm = viewModel()
        vm.acceptIntent(DealersChoiceIntent.SetOnWheel(BuiltInGame.Irish.id, true))
        vm.acceptIntent(DealersChoiceIntent.SetOnWheel(BuiltInGame.Razz.id, false))
        assertTrue(vm.state.wheel.any { it.game == BuiltInGame.Irish })
        assertFalse(vm.state.wheel.any { it.game == BuiltInGame.Razz })
        assertEquals(vm.state.wheel, viewModel().state.wheel)
    }

    @Test
    fun `with fewer than two games on the wheel it won't spin`() {
        val vm = viewModel()
        vm.state.wheel.drop(1).forEach { vm.acceptIntent(DealersChoiceIntent.SetOnWheel(it.id, false)) }
        assertEquals(1, vm.state.wheel.size)
        assertFalse(vm.state.canSpin)
        vm.acceptIntent(DealersChoiceIntent.Spin)
        assertEquals(0, vm.state.spins)
        assertNull(vm.state.pick)
    }

    @Test
    fun `a house game goes on the wheel at once, once, and is saved`() {
        val vm = viewModel()
        vm.acceptIntent(DealersChoiceIntent.AddHouseGame("  Kings and Little Ones "))
        vm.acceptIntent(DealersChoiceIntent.AddHouseGame("kings and little ones"))
        vm.acceptIntent(DealersChoiceIntent.AddHouseGame("   "))
        val house = vm.state.houseGames
        assertEquals(listOf("Kings and Little Ones"), house.map { it.houseName })
        assertTrue(house.single().onWheel)
        assertEquals(10, vm.state.wheel.size)
        assertEquals(vm.state.games, viewModel().state.games)
    }

    @Test
    fun `a house game's name is cut to fit, and the wheel holds eight of them`() {
        val vm = viewModel()
        vm.acceptIntent(DealersChoiceIntent.AddHouseGame("A".repeat(40)))
        assertEquals(DealersChoiceViewModel.MAX_NAME_LENGTH, vm.state.houseGames.single().houseName?.length)
        (2..10).forEach { vm.acceptIntent(DealersChoiceIntent.AddHouseGame("Game $it")) }
        assertEquals(DealersChoiceViewModel.MAX_HOUSE_GAMES, vm.state.houseGames.size)
        assertFalse(vm.state.canAddHouseGame)
    }

    @Test
    fun `removing the house game just picked clears the pick, and only house games can go`() {
        val vm = viewModel()
        vm.state.wheel.forEach { vm.acceptIntent(DealersChoiceIntent.SetOnWheel(it.id, false)) }
        vm.acceptIntent(DealersChoiceIntent.AddHouseGame("Anaconda"))
        vm.acceptIntent(DealersChoiceIntent.AddHouseGame("Guts"))
        vm.acceptIntent(DealersChoiceIntent.Spin)
        val picked = checkNotNull(vm.state.pick)
        assertEquals(null, picked.game)

        vm.acceptIntent(DealersChoiceIntent.RemoveHouseGame(BuiltInGame.HoldEm.id))
        assertEquals(BuiltInGame.entries.size + 2, vm.state.games.size, "the app's games can't be removed")

        vm.acceptIntent(DealersChoiceIntent.RemoveHouseGame(picked.id))
        assertNull(vm.state.pick)
        assertNull(viewModel().state.pick)
        assertEquals(1, viewModel().state.houseGames.size)
    }

    @Test
    fun `a pick taken off the wheel stays the last game, off the wheel`() {
        val vm = viewModel()
        vm.acceptIntent(DealersChoiceIntent.Spin)
        val picked = checkNotNull(vm.state.pick)
        vm.acceptIntent(DealersChoiceIntent.SetOnWheel(picked.id, false))
        assertEquals(picked.id, vm.state.pickId)
        assertFalse(checkNotNull(vm.state.pick).onWheel)
        assertEquals(-1, vm.state.pickIndex)
    }

    @Test
    fun `the store reads back what it saved, odd characters and all`() {
        val store = DealersChoiceStore(context)
        val names = listOf("Kings, Queens & Jacks", "100% Wild", "Zoë's game")
        store.setHouseGames(names)
        store.setOnWheel(setOf(BuiltInGame.Badugi.id, GameChoice.houseId(names[0])))
        assertEquals(names, store.houseGames())
        assertEquals(setOf(BuiltInGame.Badugi.id, GameChoice.houseId(names[0])), store.onWheel())
        store.setHouseGames(emptyList())
        assertTrue(store.houseGames().isEmpty())
    }
}
