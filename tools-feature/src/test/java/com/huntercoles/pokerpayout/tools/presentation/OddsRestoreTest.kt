package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.core.testing.expect
import com.huntercoles.pokerpayout.core.testing.forAll
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The odds table survives process death, whatever was typed: random sessions on the keypad and the
 * seats (cards, random hands, backspace, seats added and removed, folds, swaps, New hand, Clear
 * table, the four-colour deck) on the real ViewModel over the real preferences, with the process
 * killed at random points. Each restart, rebuilt from nothing but what was saved, shows exactly the
 * table it showed before, and never fails to load. The keypad starts closed (or on the first empty
 * slot of an empty table), as designed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OddsRestoreTest {

    private val main = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    /** One tap on the odds screen, or the process dying ([kind] [DIE]). */
    private data class Step(val kind: Int, val a: Int, val b: Int)

    @Test
    fun `the odds table comes back exactly after process death`() =
        forAll(seed = 2026_1008_71L, iterations = 80, gen = sessions) { steps ->
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            var viewModel = newProcess()
            (steps + Step(DIE, 0, 0)).forEachIndexed { index, step ->
                if (step.kind == DIE) {
                    val before = viewModel.uiState.value
                    viewModel = newProcess()
                    val after = viewModel.uiState.value
                    expect(after.table == before.table) { "step $index: restarted as\n${after.table}\nnot\n${before.table}" }
                    expect(after.fourColourDeck == before.fourColourDeck) { "after step $index: the four-colour deck changed" }
                } else {
                    viewModel.acceptIntent(intentFor(step, viewModel.uiState.value.table))
                }
            }
        }

    /** A cold start: the old ViewModel cleared, the preferences read afresh, a new ViewModel. */
    private fun newProcess(): OddsCalculatorViewModel {
        stores.forEach { it.clear() }
        stores.clear()
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OddsCalculatorViewModel(
                OddsCalculatorPreferences(context),
                OddsEngine(main),
                OddsSettings(),
                SnackbarController(),
                { SEED },
                OddsMessages(context),
            ) as T
        }
        return ViewModelProvider(store, factory)[OddsCalculatorViewModel::class.java]
    }

    @Suppress("CyclomaticComplexMethod") // one branch per kind of tap
    private fun intentFor(step: Step, table: OddsTable): OddsCalculatorIntent {
        val seat = step.a.mod(table.seats.size)
        return when (step.kind) {
            0 -> OddsCalculatorIntent.SelectSlot(SlotRef.Hole(seat, step.b.mod(2)))
            1 -> OddsCalculatorIntent.SelectSlot(SlotRef.Board(step.b.mod(BOARD)))
            2, 3 -> OddsCalculatorIntent.PickRank(step.a.mod(RANKS))
            4, 5 -> OddsCalculatorIntent.PickSuit(step.b.mod(SUITS))
            6 -> OddsCalculatorIntent.PlaceCard((step.a * SUITS + step.b).mod(DECK))
            7 -> OddsCalculatorIntent.Backspace
            8 -> OddsCalculatorIntent.RandomHand
            9 -> OddsCalculatorIntent.CloseKeypad
            10 -> OddsCalculatorIntent.AddPlayer
            11 -> OddsCalculatorIntent.RemovePlayer(seat)
            12 -> OddsCalculatorIntent.Fold(seat, step.b % 2 == 0)
            13 -> OddsCalculatorIntent.Swap(seat, step.b.mod(table.seats.size))
            14 -> OddsCalculatorIntent.ClearHand(seat)
            15 -> if (step.b % 3 == 0) OddsCalculatorIntent.ClearTable else OddsCalculatorIntent.NewHand
            else -> OddsCalculatorIntent.SetFourColourDeck(step.b % 2 == 0)
        }
    }

    private companion object {
        const val PREFS = "odds_calculator_prefs"
        const val SEED = 42L
        const val DIE = 17
        const val BOARD = 5
        const val RANKS = 13
        const val SUITS = 4
        const val DECK = 52

        val sessions = Arb.list(Arb.bind(Arb.int(0..DIE), Arb.int(0..60), Arb.int(0..60), ::Step), 0..60)
    }
}
