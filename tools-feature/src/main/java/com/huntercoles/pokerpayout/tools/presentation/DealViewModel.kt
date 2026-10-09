package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.tools.table.Deal
import com.huntercoles.pokerpayout.tools.table.DealMath
import com.huntercoles.pokerpayout.tools.table.DealProblem
import com.huntercoles.pokerpayout.tools.table.Tonight
import com.huntercoles.pokerpayout.tools.table.TonightSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A player in the deal: a name (blank shows as "Player N") and their chips. */
data class DealPlayer(val name: String = "", val chips: Long? = null)

/** Where the prizes come from: tonight's Payouts tab, or typed for this deal. */
enum class PrizeSource { Payouts, Typed }

/**
 * The deal maker (S22).
 *
 * @property fromBank the players are the ones the Bank has still in, as it named them.
 * @property payouts tonight's payout table, 1st first, from the Payouts tab.
 * @property typed the prizes typed, by place, 1st first (null: not typed).
 * @property prizes what each place left pays, one per player, from [source].
 * @property problem why there's no deal yet, or null.
 * @property deal ICM and chip chop, side by side, once there is no [problem].
 */
data class DealUiState(
    val players: List<DealPlayer> = List(DEFAULT_PLAYERS) { DealPlayer() },
    val fromBank: Boolean = false,
    val source: PrizeSource = PrizeSource.Typed,
    val payouts: List<Long> = emptyList(),
    val typed: List<Long?> = emptyList(),
    val forWinnerCents: Long? = null,
) {
    val prizes: List<Long> = List(players.size) { place ->
        when (source) {
            PrizeSource.Payouts -> payouts.getOrElse(place) { 0L }
            PrizeSource.Typed -> typed.getOrNull(place) ?: 0L
        }
    }
    val problem: DealProblem? = DealMath.problem(players.map { it.chips }, prizes, forWinnerCents ?: 0L)
    val deal: Deal? = if (problem == null) DealMath.deal(players.mapNotNull { it.chips }, prizes, forWinnerCents ?: 0L) else null

    /** Tonight's Payouts tab has a prize pool to take the prizes from. */
    val hasPayouts: Boolean get() = payouts.any { it > 0L }

    val canAdd: Boolean get() = players.size < DealMath.MAX_PLAYERS
    val canRemove: Boolean get() = players.size > DealMath.MIN_PLAYERS

    /** The most that can be kept back for the winner: what 1st pays over 2nd. */
    val maxForWinnerCents: Long get() = DealMath.maxForWinner(prizes)

    companion object {
        /** Three left: where most home games talk about a deal. */
        const val DEFAULT_PLAYERS = 3
    }
}

sealed interface DealIntent {
    data class SetName(val index: Int, val name: String) : DealIntent

    /** Null when the field is empty. */
    data class SetChips(val index: Int, val chips: Long?) : DealIntent

    data object AddPlayer : DealIntent

    data class RemovePlayer(val index: Int) : DealIntent

    data class SetSource(val source: PrizeSource) : DealIntent

    /** The prize for [place] (1 is 1st); null when the field is empty. */
    data class SetPrize(val place: Int, val cents: Long?) : DealIntent

    /** Kept back and played for; null or 0 for none. */
    data class SetForWinner(val cents: Long?) : DealIntent

    /** Tonight's players and payouts again, nothing typed; with Undo. */
    data object StartOver : DealIntent
}

/**
 * The deal maker: the players still in and the prizes left start from tonight's Bank and Payouts
 * ([TonightSource]), and can be typed instead. Every change works out ICM and the chip chop again
 * ([DealMath]). What was typed stays in [TableToolsMemory] while the app runs; tonight's payout
 * table is read again each time the screen opens.
 */
@HiltViewModel
class DealViewModel @Inject constructor(
    private val tonight: TonightSource,
    private val memory: TableToolsMemory,
    private val snackbars: SnackbarController,
    private val messages: TableToolMessages,
) : ViewModel() {

    private val _uiState: MutableStateFlow<DealUiState>
    val uiState: StateFlow<DealUiState>

    private var undo: Job? = null

    init {
        val now = tonight.tonight()
        _uiState = MutableStateFlow(memory.deal?.copy(payouts = now.prizes) ?: fresh(now))
        uiState = _uiState.asStateFlow()
    }

    fun acceptIntent(intent: DealIntent) {
        val state = _uiState.value
        when (intent) {
            is DealIntent.SetName -> {
                changePlayers(state.players.updated(intent.index) { it.copy(name = intent.name.take(MAX_PLAYER_NAME)) })
            }
            is DealIntent.SetChips -> {
                val chips = intent.chips?.coerceAtLeast(0L)
                show(state.copy(players = state.players.updated(intent.index) { it.copy(chips = chips) }))
            }
            DealIntent.AddPlayer -> if (state.canAdd) changePlayers(state.players + DealPlayer())
            is DealIntent.RemovePlayer -> if (state.canRemove && intent.index in state.players.indices) {
                changePlayers(state.players.filterIndexed { i, _ -> i != intent.index })
            }
            is DealIntent.SetSource -> setSource(intent.source)
            is DealIntent.SetPrize -> setPrize(intent.place, intent.cents)
            is DealIntent.SetForWinner -> show(state.copy(forWinnerCents = intent.cents?.takeIf { it > 0L }))
            DealIntent.StartOver -> startOver()
        }
    }

    /** New names or a player more or less: no longer just the Bank's. */
    private fun changePlayers(players: List<DealPlayer>) {
        show(_uiState.value.copy(players = players, fromBank = false))
    }

    /** Typing starts from the prizes on screen, so the host changes only what differs. */
    private fun setSource(source: PrizeSource) {
        val state = _uiState.value
        val typed = if (source == PrizeSource.Typed && state.typed.none { it != null }) state.prizes else state.typed
        show(state.copy(source = source, typed = typed))
    }

    private fun setPrize(place: Int, cents: Long?) {
        if (place !in 1..DealMath.MAX_PLAYERS) return
        val state = _uiState.value
        val typed = state.typed + List((place - state.typed.size).coerceAtLeast(0)) { null }
        show(state.copy(typed = typed.updated(place - 1) { cents }))
    }

    private fun startOver() {
        val before = _uiState.value
        val fresh = fresh(tonight.tonight())
        if (fresh == before) return
        show(fresh)
        undo?.cancel()
        undo = viewModelScope.launch {
            if (snackbars.showUndo(messages.startedOver, messages.undo) && _uiState.value == fresh) show(before)
        }
    }

    private fun show(state: DealUiState) {
        if (state == _uiState.value) return
        memory.deal = state
        _uiState.value = state
    }

    /**
     * The Bank's players still in when there are 2 to 10 of them (else three to name), and the
     * prizes from tonight's Payouts when it has a prize pool (else typed).
     */
    private fun fresh(now: Tonight): DealUiState {
        val bank = now.playersLeft.takeIf { it.size in DealMath.MIN_PLAYERS..DealMath.MAX_PLAYERS }
        return DealUiState(
            players = bank?.map { DealPlayer(name = it) } ?: List(DealUiState.DEFAULT_PLAYERS) { DealPlayer() },
            fromBank = bank != null,
            source = if (now.prizePoolCents > 0L) PrizeSource.Payouts else PrizeSource.Typed,
            payouts = now.prizes,
        )
    }
}
