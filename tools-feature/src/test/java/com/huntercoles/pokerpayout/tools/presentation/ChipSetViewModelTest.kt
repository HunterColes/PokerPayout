package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.utils.BlindSchedule
import com.huntercoles.pokerpayout.core.utils.BlindScheduleProvider
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.ChipShortfall
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.core.utils.KeptBackEstimate
import com.huntercoles.pokerpayout.core.utils.ScheduledBreak
import com.huntercoles.pokerpayout.core.utils.StackPlan
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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

/**
 * The chip set ViewModel (S11, PP-033) on real preferences over in-memory storage, a fake clock
 * schedule and virtual time: the plan is live, every change is saved, the Tournament setup is the
 * source of players and stack, and reset and removing a colour offer Undo.
 */
class ChipSetViewModelTest {

    private val main = StandardTestDispatcher()
    private val stores = mutableMapOf<String, SharedPreferences>()
    private val snackbars = SnackbarController()
    private lateinit var context: Context
    private lateinit var tournament: TournamentPreferences
    private var schedule: BlindSchedule? = null
    private val provider = object : BlindScheduleProvider {
        override fun currentSchedule(): BlindSchedule? = schedule
    }
    private val messages = mockk<ChipSetMessages> {
        every { reset } returns "Reset"
        every { undo } returns "Undo"
        every { removed(any()) } answers { "${firstArg<InventoryChip>().colour} removed" }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(main)
        context = mockk {
            every { getSharedPreferences(any(), any()) } answers { stores.getOrPut(firstArg()) { InMemoryPreferences() } }
        }
        tournament = TournamentPreferences(context)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun chips() = ChipCalculatorPreferences(context, tournament)

    private fun viewModel(compute: CoroutineDispatcher = main) =
        ChipSetViewModel(chips(), tournament, provider, snackbars, messages, compute)

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(main) { block() }

    private val ChipSetViewModel.state get() = uiState.value

    private fun ChipSetViewModel.ready(): StackPlan.Ready {
        val plan = state.plan
        assertTrue(plan is StackPlan.Ready, "expected a plan, got $plan")
        return plan as StackPlan.Ready
    }

    /** The clock's default ladder (5,000 from 50s) with breaks after levels 4 and 8. */
    private val defaultSchedule = BlindSchedule(
        levels = listOf(50, 100, 150, 300, 500, 800, 1_500, 3_000, 5_000, 10_000, 20_000, 40_000)
            .mapIndexed { i, sb -> BlindLevel(i + 1, sb, 2 * sb, 0, i * 20) },
        regularLevelCount = 9,
        breaks = listOf(ScheduledBreak(1, 4), ScheduledBreak(2, 8)),
        smallestChip = 50,
        startingChips = 5_000
    )

    @Test
    fun `it opens on the home set's plan for the Tournament's players and stack`() = runVmTest {
        val vm = viewModel()
        assertNull(vm.state.plan, "nothing planned before the first run")
        testScheduler.advanceUntilIdle()

        assertEquals(ChipInventory.HOME_SET, vm.state.inventory)
        assertFalse(vm.state.settings.inventoryReviewed)
        assertEquals(5, vm.state.players)
        assertEquals(5_000, vm.state.startingStack)
        assertTrue(vm.state.stackFromTournament)
        val plan = vm.ready()
        assertEquals(5_000L, plan.stack.totalValue)
        assertEquals(25, plan.stack.chips.first().value, "25s pay the 50 small blind")
        assertNull(plan.colorUp)
        assertFalse(vm.state.hasSchedule)
    }

    @Test
    fun `a count change is saved and planned at once, with no Generate`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.acceptIntent(ChipSetIntent.SetCount(ChipColour.Green, 20))
        testScheduler.advanceUntilIdle()

        assertEquals(20, vm.state.inventory[ChipColour.Green]!!.count)
        assertTrue(vm.state.settings.inventoryReviewed, "a set you changed is yours")
        assertEquals(20, chips().current().inventory[ChipColour.Green]!!.count, "saved")
        val plan = vm.ready()
        assertTrue(5 * plan.stack.countOf(25) <= 20, "the stack respects the 20 greens: ${plan.stack.chips}")
    }

    @Test
    fun `a set that can't cover the table names the colour and how many more`() = runVmTest {
        tournament.setPlayerCount(9)
        tournament.setSmallestChip(25)
        tournament.setStartingChips(1_000)
        chips().setInventory(
            ChipInventory.of(listOf(InventoryChip(ChipColour.Green, 25, 90), InventoryChip(ChipColour.Black, 100, 45)))
        )
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        val plan = vm.state.plan
        assertTrue(plan is StackPlan.Short, "$plan")
        plan as StackPlan.Short
        assertEquals(ChipShortfall(ChipColour.Black, 100, more = 27, stacks = 9), plan.shortfall)
        assertEquals(6, plan.stacksYouCanMake)

        // Adding the blacks makes it
        vm.acceptIntent(ChipSetIntent.SetCount(ChipColour.Black, 72))
        testScheduler.advanceUntilIdle()
        assertEquals(1_000L, vm.ready().stack.totalValue)
    }

    @Test
    fun `the color-up plan follows the clock's schedule`() = runVmTest {
        schedule = defaultSchedule
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.hasSchedule)
        val colorUp = assertNotNull(vm.ready().colorUp).let { vm.ready().colorUp!! }
        assertEquals(listOf(1, 2), colorUp.steps.map { it.breakNumber })
        assertEquals(5, colorUp.stacksInPlay)
    }

    @Test
    fun `players follow the Tournament setup while the screen is open`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        val extraForFive = vm.ready().reserve.extraStacks

        tournament.setPlayerCount(9)
        testScheduler.advanceUntilIdle()
        assertEquals(9, vm.state.players)
        assertTrue(vm.ready().reserve.extraStacks < extraForFive)
    }

    @Test
    fun `adding, editing and removing a colour, and Undo brings it back`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.acceptIntent(ChipSetIntent.AddColour)
        assertEquals(ColourEditor(editing = null), vm.state.editor)
        vm.acceptIntent(ChipSetIntent.SaveColour(InventoryChip(ChipColour.Pink, 250, 50), replacing = null))
        assertNull(vm.state.editor)
        assertEquals(listOf(25, 100, 250, 500, 1_000), vm.state.inventory.chips.map { it.value })

        vm.acceptIntent(ChipSetIntent.EditColour(ChipColour.Pink))
        assertEquals(ColourEditor(ChipColour.Pink), vm.state.editor)
        vm.acceptIntent(ChipSetIntent.SaveColour(InventoryChip(ChipColour.White, 250, 60), replacing = ChipColour.Pink))
        assertEquals(InventoryChip(ChipColour.White, 250, 60), vm.state.inventory[ChipColour.White])
        assertNull(vm.state.inventory[ChipColour.Pink])

        vm.acceptIntent(ChipSetIntent.RemoveColour(ChipColour.White))
        assertNull(vm.state.inventory[ChipColour.White])
        testScheduler.runCurrent()
        val snackbar = snackbars.hostState.currentSnackbarData!!
        assertEquals("White removed", snackbar.visuals.message)
        snackbar.performAction()
        testScheduler.advanceUntilIdle()
        assertEquals(InventoryChip(ChipColour.White, 250, 60), vm.state.inventory[ChipColour.White])
    }

    @Test
    fun `a colour can't take a value another colour has`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.acceptIntent(ChipSetIntent.AddColour)
        vm.acceptIntent(ChipSetIntent.SaveColour(InventoryChip(ChipColour.Pink, 100, 50), replacing = null))

        assertEquals(ChipInventory.HOME_SET, vm.state.inventory)
        assertEquals(ColourEditor(editing = null), vm.state.editor, "the sheet stays open")
        vm.acceptIntent(ChipSetIntent.CloseEditor)
        assertNull(vm.state.editor)
    }

    @Test
    fun `the stack settings are saved and re-planned`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.acceptIntent(ChipSetIntent.SetReserve(3))
        vm.acceptIntent(ChipSetIntent.SetMaxColours(3))
        vm.acceptIntent(ChipSetIntent.SetShape(ChipDistributionCurve.BellCurve))
        testScheduler.advanceUntilIdle()

        val saved = chips().current()
        assertEquals(3, saved.reserveOverride)
        assertEquals(3, saved.maxColours)
        assertEquals(ChipDistributionCurve.BellCurve, saved.shape)
        val plan = vm.ready()
        assertEquals(3, plan.reserve.requested)
        assertTrue(plan.stack.chips.size <= 3 || plan.stack.moreColoursThanAsked)
    }

    /** PP-091 #3: the mockups' night keeps 5 back for rebuys (until level 4) and 9 for add-ons. */
    private fun mockupNight() {
        tournament.setPlayerCount(9)
        tournament.setRebuyAmount(40.0)
        tournament.setAddOnAmount(10.0)
        tournament.setRebuyUntilLevel(4)
    }

    @Test
    fun `stacks kept back follow the Tournament's rebuys and add-ons until you set them`() = runVmTest {
        mockupNight()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(KeptBackEstimate(rebuyStacks = 5, addOnStacks = 9, rebuyCutoff = true), vm.state.reserveEstimate)
        assertEquals(14, vm.state.reserveStacks)
        assertTrue(vm.state.reserveFromTournament)
        assertEquals(14, vm.ready().reserve.requested, "the plan keeps them back")
        assertNull(chips().current().reserveOverride, "an estimate isn't saved")

        // Rebuys open all game: one each
        tournament.setRebuyUntilLevel(0)
        testScheduler.advanceUntilIdle()
        assertEquals(18, vm.state.reserveStacks)
        assertEquals(18, vm.ready().reserve.requested)

        // Your own number stays, whatever the Tournament does next
        vm.acceptIntent(ChipSetIntent.SetReserve(3))
        tournament.setAddOnAmount(0.0)
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.state.reserveStacks)
        assertFalse(vm.state.reserveFromTournament)
        assertEquals(9, vm.state.reserveEstimate.stacks, "the estimate moves on underneath")
        assertEquals(3, chips().current().reserveOverride)
        assertEquals(3, vm.ready().reserve.requested)

        // ... until you go back to the estimate
        vm.acceptIntent(ChipSetIntent.SetReserve(null))
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.reserveFromTournament)
        assertEquals(9, vm.state.reserveStacks)
        assertNull(chips().current().reserveOverride)
    }

    @Test
    fun `a kept-back number saved before the estimate existed is kept`() = runVmTest {
        mockupNight()
        chips().setReserveOverride(2)
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.state.reserveStacks)
        assertFalse(vm.state.reserveFromTournament)
        assertEquals(14, vm.state.reserveEstimate.stacks)
        assertEquals(2, vm.ready().reserve.requested)
    }

    @Test
    fun `a reset goes back to the Tournament's estimate`() = runVmTest {
        mockupNight()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.acceptIntent(ChipSetIntent.SetReserve(1))
        vm.acceptIntent(ChipSetIntent.Reset)
        testScheduler.advanceUntilIdle() // past the Undo window
        assertTrue(vm.state.reserveFromTournament)
        assertEquals(14, vm.state.reserveStacks)
    }

    @Test
    fun `a stack of your own, then back to the Tournament's`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.acceptIntent(ChipSetIntent.SetStackOverride(3_000))
        testScheduler.advanceUntilIdle()
        assertEquals(3_000, vm.state.startingStack)
        assertFalse(vm.state.stackFromTournament)
        assertEquals(3_000L, vm.ready().stack.totalValue)

        vm.acceptIntent(ChipSetIntent.SetStackOverride(5_000)) // the Tournament's own: follow it again
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.stackFromTournament)
        assertNull(chips().current().stackOverride)
    }

    @Test
    fun `reset applies at once, and Undo puts everything back`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.acceptIntent(ChipSetIntent.SetCount(ChipColour.Black, 300))
        vm.acceptIntent(ChipSetIntent.SetReserve(2))
        testScheduler.advanceUntilIdle()
        val before = vm.state.settings

        vm.acceptIntent(ChipSetIntent.Reset)
        testScheduler.runCurrent()
        assertEquals(ChipSetSettings(), vm.state.settings)
        val snackbar = snackbars.hostState.currentSnackbarData!!
        assertEquals("Reset", snackbar.visuals.message)
        snackbar.performAction()
        testScheduler.advanceUntilIdle()
        assertEquals(before, vm.state.settings)
    }

    @Test
    fun `without Undo the reset stays once the 8 second window is over`() = runVmTest {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.acceptIntent(ChipSetIntent.SetReserve(2))
        vm.acceptIntent(ChipSetIntent.Reset)
        testScheduler.advanceUntilIdle() // virtual time runs past the window
        assertNull(snackbars.hostState.currentSnackbarData)
        assertEquals(ChipSetSettings(), chips().current())
    }

    @Test
    fun `the plan is worked out on the compute dispatcher, not on Main`() = runVmTest {
        val compute = StandardTestDispatcher(TestCoroutineScheduler())
        val vm = viewModel(compute)
        testScheduler.advanceUntilIdle()
        assertNull(vm.state.plan, "Main alone can't plan")

        repeat(3) {
            compute.scheduler.advanceUntilIdle()
            testScheduler.advanceUntilIdle()
        }
        assertEquals(5_000L, vm.ready().stack.totalValue)
    }
}
