package com.huntercoles.pokerpayout.tools.presentation.composable

import com.huntercoles.pokerpayout.tools.poker.Cards
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import com.huntercoles.pokerpayout.tools.presentation.KeypadState
import com.huntercoles.pokerpayout.tools.presentation.OddsCalculatorUiState
import com.huntercoles.pokerpayout.tools.presentation.OddsTable
import com.huntercoles.pokerpayout.tools.presentation.RunItOutDealer
import com.huntercoles.pokerpayout.tools.presentation.RunOutState
import com.huntercoles.pokerpayout.tools.presentation.SeatState
import com.huntercoles.pokerpayout.tools.presentation.SlotRef
import com.huntercoles.pokerpayout.tools.presentation.SlotValue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * The screen states the odds goldens and layout checks render: the mockups' hand, A♠K♠ against
 * Q♥Q♦, from typing it in to sweating the river. Every number comes from the real engine (exact,
 * or Monte Carlo with a fixed seed), worked out once per test JVM.
 */
internal object OddsFixtures {
    private val engine = OddsEngine()

    /** Monte Carlo with a fixed seed: the same snapshots on every run and thread count. */
    private val seeded = OddsSettings(maxSamples = 100_000, exactBudget = 0, seed = 11)

    private fun slots(text: String): List<SlotValue> = text.split(' ').filter { it.isNotBlank() }.map {
        when (it) {
            "?" -> SlotValue.Random
            "-" -> SlotValue.Empty
            else -> SlotValue.Known(Cards.parse(it))
        }
    }

    fun table(vararg seats: String, board: String = "", folded: Set<Int> = emptySet()): OddsTable = OddsTable(
        seats = seats.mapIndexed { i, s -> SeatState(slots(s), folded = i in folded) },
        board = (slots(board) + List(OddsTable.BOARD_SLOTS) { SlotValue.Empty }).take(OddsTable.BOARD_SLOTS),
    )

    /** The table with its finished odds (and the next-card grid where the screen would have it). */
    private fun worked(
        table: OddsTable,
        settings: OddsSettings = OddsSettings(),
        keypad: KeypadState = KeypadState(),
    ) = runBlocking {
        val request = table.toRequest()!!
        val result = engine.finalResult(request, settings)
        val grid = result.exact && table.allHandsKnown && table.boardCards.size in 3..4
        OddsCalculatorUiState(
            table = table,
            keypad = keypad,
            result = result,
            breakdown = if (grid) engine.nextCardBreakdown(request) else null,
        )
    }

    /** S8, empty: nothing typed, the first slot waiting. */
    val empty: OddsCalculatorUiState by lazy { OddsCalculatorUiState(keypad = KeypadState(target = SlotRef.Hole(0, 0))) }

    /** S8, typing: A♠K♠ against Q♥ and a slot still to fill; Q picked on the keypad, so ♥ is struck. */
    val typing: OddsCalculatorUiState by lazy {
        worked(
            table("As Ks", "Qh -"),
            seeded,
            KeypadState(target = SlotRef.Hole(1, 1), rank = Cards.parse("Qc") / Cards.SUITS),
        )
    }

    /** S8, estimate: the same hand with the keypad put away, a Monte Carlo snapshot still refining. */
    val estimate: OddsCalculatorUiState by lazy {
        runBlocking {
            val table = table("As Ks", "Qh -")
            val snapshot = engine.calculate(table.toRequest()!!, seeded).toList()[2]
            OddsCalculatorUiState(table = table, result = snapshot, isCalculating = true)
        }
    }

    /** S9, flop exact: 56.06 / 43.94 over all 990 runouts, with the next-card grid. */
    val flopExact: OddsCalculatorUiState by lazy { worked(table("As Ks", "Qh Qd", board = "Js Ts 2c")) }

    /** S9, preflop against a random hand: an estimate. */
    val preflopRandom: OddsCalculatorUiState by lazy { worked(table("As Ks", "? ?"), seeded) }

    /** S9, four players with one folded (its cards dead). */
    val fourPlayersFold: OddsCalculatorUiState by lazy {
        worked(table("As Ks", "Qh Qd", "9c 8c", "Ad Kd", board = "Js Ts 2c", folded = setOf(2)))
    }

    private val runRequest by lazy { table("As Ks", "Qh Qd", board = "Js Ts 2c").toRequest()!! }

    /** Run it out started on the flop, with [turn] and [river] dealt face down. */
    private fun runOut(turn: String, river: String, deals: Int): RunOutState = runBlocking {
        val dealer = RunItOutDealer(engine)
        var state = dealer.start(runRequest, seed = 42).copy(runout = Cards.parseAll("$turn $river"))
        repeat(deals) { state = dealer.dealNext(state) }
        state
    }

    /** S10, the turn: the 7♥ leaves A♠K♠ 16 rivers of 44 (36.4%). */
    val runOutTurn: RunOutState by lazy { runOut("7h", "3d", deals = 1) }

    /** S10, river: the 9♠ makes A♠K♠'s flush. */
    val runOutRiverP1: RunOutState by lazy { runOut("7h", "9s", deals = 2) }

    /** S10, river: the 3♦ is a blank, Q♥Q♦ holds. */
    val runOutRiverP2: RunOutState by lazy { runOut("7h", "3d", deals = 2) }

    /** Run it twice from the flop (layout checks only). */
    val runOutTwice: RunOutState by lazy { runBlocking { RunItOutDealer(engine).runTwice(runOut("7h", "3d", deals = 0)) } }
}
