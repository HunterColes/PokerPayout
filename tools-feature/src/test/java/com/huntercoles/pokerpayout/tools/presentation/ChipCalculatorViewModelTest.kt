package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.content.SharedPreferences
import com.huntercoles.pokerpayout.core.preferences.ChipCalculatorPreferences
import com.huntercoles.pokerpayout.core.preferences.TournamentPreferences
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOptimizer
import com.huntercoles.pokerpayout.core.utils.ChipDistributionOutcome
import com.huntercoles.pokerpayout.core.utils.ChipDistributionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The chip calculator ViewModel against real preferences on in-memory storage.
 *
 * Main is an [UnconfinedTestDispatcher] on purpose: on the device, Dispatchers.Main.immediate
 * resumes a StateFlow collector inline when the value changes on the main thread. That inline
 * resume is what made v1.1.x show "Total Chips 0" and a stale fit score after Generate.
 */
class ChipCalculatorViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val stores = mutableMapOf<String, SharedPreferences>()
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        context = mockk {
            every { getSharedPreferences(any(), any()) } answers {
                stores.getOrPut(firstArg()) { FakeSharedPreferences() }
            }
        }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(compute: CoroutineDispatcher = UnconfinedTestDispatcher(scheduler)) =
        ChipCalculatorViewModel(
            ChipCalculatorPreferences(context, TournamentPreferences(context)),
            TournamentPreferences(context),
            compute
        )

    private fun optimum(t: Int, s: Int, k: Int, curve: ChipDistributionCurve): ChipDistributionResult =
        (ChipDistributionOptimizer.optimize(t, s, k, curve) as ChipDistributionOutcome.Success).distribution

    private fun ChipCalculatorUiState.pairs() = chipBreakdown.map { it.value to it.count }

    @Test
    fun `generate on the default config shows the breakdown's chip total and fit score`() {
        // Tournament defaults: 5000 starting chips, smallest chip 50; calculator default 5 denominations, Linear Steep.
        val vm = viewModel()
        vm.calculateChipBreakdown()

        val state = vm.uiState.value
        assertEquals(listOf(50 to 9, 100 to 8, 250 to 5, 500 to 3, 1000 to 1), state.pairs())
        assertEquals(26, state.totalPhysicalChips, "Total Chips stat")
        assertEquals(optimum(5000, 50, 5, ChipDistributionCurve.LinearSteep).fitScore, state.fitScore)
        assertFalse(state.isCalculating)
        assertNull(state.message)
    }

    @Test
    fun `fit score and total follow the latest generate, not the previous one`() {
        val vm = viewModel()
        vm.calculateChipBreakdown()
        vm.updateCurveSelection(ChipDistributionCurve.BellCurve)
        vm.calculateChipBreakdown()

        val expected = optimum(5000, 50, 5, ChipDistributionCurve.BellCurve)
        val state = vm.uiState.value
        assertEquals(expected.denominations.zip(expected.quantities), state.pairs())
        assertEquals(expected.fitScore, state.fitScore)
        assertEquals(expected.totalChips, state.totalPhysicalChips)
    }

    @Test
    fun `unreachable stack shows one error line and clears the old breakdown`() {
        val vm = viewModel()
        vm.calculateChipBreakdown()
        vm.updateTotalChips(5010)
        vm.updateSmallestChip(25)
        vm.calculateChipBreakdown()

        val state = vm.uiState.value
        assertEquals("5010 can't be made from chips of 25 and up. Try 5000 or 5025.", state.message)
        assertTrue(state.chipBreakdown.isEmpty())
        assertNull(state.fitScore)
        assertEquals(0, state.totalPhysicalChips)
        assertFalse(state.isCalculating)
    }

    @Test
    fun `non-standard smallest chip gives a breakdown and a note about the snapped chip`() {
        val vm = viewModel()
        vm.updateSmallestChip(15)
        vm.calculateChipBreakdown()

        val expected = optimum(5000, 15, 5, ChipDistributionCurve.LinearSteep)
        val state = vm.uiState.value
        assertEquals("15 isn't a standard chip, so the smallest chip used is 10.", state.message)
        assertEquals(10, state.chipBreakdown.first().value)
        assertEquals(expected.denominations.zip(expected.quantities), state.pairs())
        assertEquals(expected.totalChips, state.totalPhysicalChips)
    }

    @Test
    fun `generate shows a loading state until the compute dispatcher has run`() {
        // The optimizer runs on the injected compute dispatcher (Dispatchers.Default in the app),
        // not on Main: nothing changes until that dispatcher gets to run.
        val compute = StandardTestDispatcher(scheduler)
        val vm = viewModel(compute)
        vm.calculateChipBreakdown()

        assertTrue(vm.uiState.value.isCalculating)
        assertTrue(vm.uiState.value.chipBreakdown.isEmpty())

        scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isCalculating)
        assertEquals(26, vm.uiState.value.totalPhysicalChips)
    }

    @Test
    fun `reset clears the message and the breakdown`() {
        val vm = viewModel()
        vm.updateTotalChips(5010)
        vm.updateSmallestChip(25)
        vm.calculateChipBreakdown()
        vm.confirmReset()

        val state = vm.uiState.value
        assertNull(state.message)
        assertTrue(state.chipBreakdown.isEmpty())
        assertEquals(5000, state.totalChips)
        assertEquals(50, state.smallestChip)
    }
}

/** In-memory SharedPreferences for JVM tests. */
private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? =
        if (values.containsKey(key)) values[key] as String? else defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (values.containsKey(key)) values[key] as MutableSet<String>? else defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.toMutableSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}
