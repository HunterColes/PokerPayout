package com.huntercoles.pokerpayout.tools.presentation

import com.huntercoles.pokerpayout.tools.poker.NextCardBreakdown
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import com.huntercoles.pokerpayout.tools.poker.RunOuts

/**
 * The run-it-out state machine's transitions (S10), each worked out exactly with the engine.
 * Every hand is known in run it out, so even preflop is an exact enumeration.
 */
class RunItOutDealer(private val engine: OddsEngine) {

    /**
     * Starts on [request] (every contestant's hand known; a board of 0, 3 or 4 cards). The runout
     * is dealt face down from [seed], and the history holds every street up to the starting board.
     */
    suspend fun start(request: OddsRequest, seed: Long): RunOutState {
        val history = Street.entries.filter { it.boardCards <= request.board.size }.map { street ->
            StreetEquity(street, equity(request.copy(board = request.board.take(street.boardCards))))
        }
        return RunOutState(
            request = request,
            seed = seed,
            run = 0,
            runout = RunOuts.deal(request, seed, run = 0).single(),
            history = history,
            outs = outs(request, request.board),
        )
    }

    /** Turns the next street face up and works out the new equity; unchanged once the river is out. */
    suspend fun dealNext(state: RunOutState): RunOutState {
        val next = state.nextStreet ?: return state
        val dealt = next.boardCards - state.request.board.size
        val board = state.request.board + state.runout.take(dealt)
        return state.copy(
            dealt = dealt,
            history = state.history + StreetEquity(next, equity(state.request.copy(board = board))),
            outs = outs(state.request, board),
        )
    }

    /** A fresh runout (the next run of the seed) from the starting board. */
    suspend fun runAgain(state: RunOutState): RunOutState {
        val run = state.run + if (state.twice != null) 2 else 1
        return state.copy(
            run = run,
            runout = RunOuts.deal(state.request, state.seed, run).single(),
            dealt = 0,
            history = state.history.take(startStreets(state)),
            twice = null,
            outs = outs(state.request, state.request.board),
        )
    }

    /** The rest of the board twice from one deck (the next run of the seed); each board is half the pot. */
    suspend fun runTwice(state: RunOutState): RunOutState {
        val run = state.run + if (state.twice != null) 2 else 1
        val boards = RunOuts.deal(state.request, state.seed, run, count = 2).map { state.request.board + it }
        return state.copy(
            run = run,
            runout = boards.first().drop(state.request.board.size),
            dealt = 0,
            history = state.history.take(startStreets(state)),
            twice = boards.map { TwiceBoard(it, equity(state.request.copy(board = it))) },
            outs = null,
        )
    }

    private fun startStreets(state: RunOutState): Int = Street.entries.count { it.boardCards <= state.request.board.size }

    private suspend fun equity(request: OddsRequest): List<Double> =
        engine.finalResult(request, EXACT).players.map { it.equityPct }

    private suspend fun outs(request: OddsRequest, board: List<Int>): NextCardBreakdown? =
        if (board.size == Street.FLOP.boardCards || board.size == Street.TURN.boardCards) {
            engine.nextCardBreakdown(request.copy(board = board))
        } else {
            null
        }

    private companion object {
        /** Every hand is known, so the default budget always enumerates (heads-up preflop: 3.4M evaluations). */
        val EXACT = OddsSettings()
    }
}
