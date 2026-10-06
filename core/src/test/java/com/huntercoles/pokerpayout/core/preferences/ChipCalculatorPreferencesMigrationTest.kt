package com.huntercoles.pokerpayout.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOptimizer
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOutcome
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.core.utils.PlanStacksUseCase
import com.huntercoles.pokerpayout.core.utils.StackPlan
import com.huntercoles.pokerpayout.core.utils.StackPlanRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chip calculator's saved settings become the chip set's (PP-033): existing users keep their
 * setup. Written as v1.3.0 wrote them, into Robolectric's real SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChipCalculatorPreferencesMigrationTest {

    private lateinit var context: Context
    private lateinit var chips: SharedPreferences
    private lateinit var tournament: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        chips = context.getSharedPreferences("chip_calculator_prefs", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        tournament = context.getSharedPreferences("tournament_prefs", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    }

    private fun open() = ChipCalculatorPreferences(context, TournamentPreferences(context))

    @Test
    fun `a calculator never used starts from the 500-chip home set, asking you to check it`() {
        val settings = open().current()
        assertEquals(ChipSetSettings(), settings)
        assertEquals(ChipInventory.HOME_SET, settings.inventory)
        assertFalse(settings.inventoryReviewed)
    }

    @Test
    fun `the last generated stack becomes the set, and the stack settings carry over`() {
        tournament.edit().putInt("player_count", 9).commit()
        chips.edit()
            .putString("chip_breakdown", "50,9;100,8;250,5;500,3;1000,1")
            .putFloat("fit_score", 0.896f)
            .putInt("total_physical_chips", 26) // v1.1.x
            .putString("selected_curve", "Bell Curve (Balanced)")
            .putInt("denomination_count", 6)
            .putInt("custom_total_chips", 7_500)
            .commit()

        val settings = open().current()

        // 9 players twice over, in rolls of 25: 9 × 18 = 162 -> 175, 8 × 18 = 144 -> 150, ...
        assertEquals(
            listOf(
                InventoryChip(ChipColour.Orange, 50, 175),
                InventoryChip(ChipColour.Black, 100, 150),
                InventoryChip(ChipColour.Pink, 250, 100),
                InventoryChip(ChipColour.Purple, 500, 75),
                InventoryChip(ChipColour.Yellow, 1_000, 25),
            ),
            settings.inventory.chips
        )
        assertFalse("a set the app filled in asks to be checked", settings.inventoryReviewed)
        assertEquals(ChipDistributionCurve.BellCurve, settings.shape)
        assertEquals(6, settings.maxColours)
        assertEquals(7_500, settings.stackOverride)
        assertEquals(0, settings.reserveStacks)

        // The result and the fragile curve name are gone; the curve is saved by id now
        listOf("chip_breakdown", "fit_score", "total_physical_chips", "selected_curve").forEach {
            assertFalse("$it should be removed", chips.contains(it))
        }
        assertEquals("bell", chips.getString("stack_shape", null))
        assertEquals(7_500, chips.getInt("custom_total_chips", 0))
        assertEquals(6, chips.getInt("denomination_count", 0))
    }

    @Test
    fun `settings without a generated stack are run once more for their colours`() {
        tournament.edit()
            .putInt("player_count", 6)
            .putInt("smallest_chip", 30) // an old free-entry value: the calculator would use 10
            .putInt("starting_chips", 4_000)
            .commit()
        chips.edit().putInt("denomination_count", 4).putString("selected_curve", "Linear Moderate").commit()

        val expected = ChipDistributionOptimizer.optimize(4_000, 10, 4, ChipDistributionCurve.LinearModerate)
            as ChipDistributionOutcome.Success
        val stack = expected.distribution.denominations.zip(expected.distribution.quantities)

        val settings = open().current()
        assertEquals(ChipInventory.fromLastStack(stack, players = 6), settings.inventory)
        assertEquals(stack.map { it.first }, settings.inventory.chips.map { it.value })
        assertEquals(ChipDistributionCurve.LinearModerate, settings.shape)
        assertEquals(4, settings.maxColours)
        assertNull(settings.stackOverride)
    }

    @Test
    fun `an existing user's plan is the stack the calculator showed them`() {
        // v1.3.0 defaults, Generate pressed: 50×9, 100×8, 250×5, 500×3, 1,000×1 for 5 players
        chips.edit().putString("chip_breakdown", "50,9;100,8;250,5;500,3;1000,1").putFloat("fit_score", 0.896f).commit()
        val settings = open().current()

        val plan = PlanStacksUseCase()(
            StackPlanRequest(settings.inventory, 5_000, players = 5, smallBlind = 50, maxColours = settings.maxColours)
        )
        assertTrue("$plan", plan is StackPlan.Ready)
        plan as StackPlan.Ready
        assertEquals(listOf(50 to 9, 100 to 8, 250 to 5, 500 to 3, 1_000 to 1), plan.stack.chips.map { it.value to it.count })
        assertTrue("a reserve as big as the table", plan.reserve.extraStacks >= 5)
    }

    @Test
    fun `the migration runs once and never overwrites your set`() {
        chips.edit().putString("chip_breakdown", "25,10;100,10").commit()
        val prefs = open()
        val mine = ChipInventory.of(listOf(InventoryChip(ChipColour.White, 25, 300), InventoryChip(ChipColour.Red, 100, 200)))
        prefs.setInventory(mine)
        prefs.setReserveStacks(4)

        val reopened = open().current()
        assertEquals(mine, reopened.inventory)
        assertTrue(reopened.inventoryReviewed)
        assertEquals(4, reopened.reserveStacks)
    }

    @Test
    fun `an unknown curve name and odd counts fall back to the defaults`() {
        chips.edit()
            .putString("selected_curve", "Renamed Curve")
            .putInt("denomination_count", 42)
            .putInt("custom_total_chips", 0)
            .commit()
        val settings = open().current()
        assertEquals(ChipDistributionCurve.LinearSteep, settings.shape)
        assertEquals(ChipSetSettings.MAX_COLOURS_RANGE.last, settings.maxColours)
        assertNull(settings.stackOverride)
    }

    @Test
    fun `reset goes back to the home set, and restore puts everything back`() {
        val prefs = open()
        prefs.setInventory(ChipInventory.HOME_SET.withCount(ChipColour.Green, 40))
        prefs.setShape(ChipDistributionCurve.PositiveLinear)
        prefs.setMaxColours(3)
        prefs.setReserveStacks(2)
        prefs.setStackOverride(3_000)
        val before = prefs.current()

        prefs.resetAllData()
        assertEquals(ChipSetSettings(), prefs.current())

        prefs.restore(before)
        assertEquals(before, prefs.current())
        assertEquals(before, open().current())
    }
}
