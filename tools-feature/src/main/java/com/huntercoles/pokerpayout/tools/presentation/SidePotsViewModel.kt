package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.tools.table.Contribution
import com.huntercoles.pokerpayout.tools.table.PotSplit
import com.huntercoles.pokerpayout.tools.table.SidePots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A player in the hand: a name (blank shows as "Player N"), the chips they put in, and whether they folded. */
data class PotPlayer(val name: String = "", val chips: Long? = null, val folded: Boolean = false)

/**
 * Side pots (S21): who put what in, and the pots that makes.
 *
 * @property split the main pot and side pots for [players], worked out once per change.
 */
data class SidePotsUiState(val players: List<PotPlayer> = List(DEFAULT_PLAYERS) { PotPlayer() }) {
    val split: PotSplit = SidePots.split(players.map { Contribution(it.chips ?: 0L, it.folded) })

    val canAdd: Boolean get() = players.size < SidePots.MAX_PLAYERS
    val canRemove: Boolean get() = players.size > SidePots.MIN_PLAYERS

    /** Everything put in, folded or not. */
    val totalChips: Long get() = players.sumOf { it.chips ?: 0L }

    companion object {
        /** An all-in and two callers: the smallest hand with a side pot. */
        const val DEFAULT_PLAYERS = 3
    }
}

sealed interface SidePotsIntent {
    data class SetName(val index: Int, val name: String) : SidePotsIntent

    /** Null when the field is empty. */
    data class SetChips(val index: Int, val chips: Long?) : SidePotsIntent

    data class SetFolded(val index: Int, val folded: Boolean) : SidePotsIntent

    data object AddPlayer : SidePotsIntent

    data class RemovePlayer(val index: Int) : SidePotsIntent

    /** Clears the chips and folds and keeps the players, with Undo. */
    data object NewHand : SidePotsIntent
}

/**
 * Side pots: every change works the pots out again, in exact whole chips ([SidePots]). What was
 * typed stays in [TableToolsMemory] while the app runs.
 */
@HiltViewModel
class SidePotsViewModel @Inject constructor(
    private val memory: TableToolsMemory,
    private val snackbars: SnackbarController,
    private val messages: TableToolMessages,
) : ViewModel() {

    private val _uiState = MutableStateFlow(memory.sidePots ?: SidePotsUiState())
    val uiState: StateFlow<SidePotsUiState> = _uiState.asStateFlow()

    private var undo: Job? = null

    fun acceptIntent(intent: SidePotsIntent) {
        when (intent) {
            is SidePotsIntent.SetName -> player(intent.index) { it.copy(name = intent.name.take(MAX_PLAYER_NAME)) }
            is SidePotsIntent.SetChips -> player(intent.index) { it.copy(chips = intent.chips?.coerceAtLeast(0L)) }
            is SidePotsIntent.SetFolded -> player(intent.index) { it.copy(folded = intent.folded) }
            SidePotsIntent.AddPlayer -> if (_uiState.value.canAdd) change(_uiState.value.players + PotPlayer())
            is SidePotsIntent.RemovePlayer -> removePlayer(intent.index)
            SidePotsIntent.NewHand -> newHand()
        }
    }

    private fun player(index: Int, transform: (PotPlayer) -> PotPlayer) {
        change(_uiState.value.players.updated(index, transform))
    }

    private fun removePlayer(index: Int) {
        val players = _uiState.value.players
        if (_uiState.value.canRemove && index in players.indices) change(players.filterIndexed { i, _ -> i != index })
    }

    private fun change(players: List<PotPlayer>) {
        if (players != _uiState.value.players) show(SidePotsUiState(players))
    }

    private fun show(state: SidePotsUiState) {
        memory.sidePots = state
        _uiState.value = state
    }

    /** Same players, nothing in; Undo brings the hand back unless something changed since. */
    private fun newHand() {
        val before = _uiState.value
        if (before.players.none { it.chips != null || it.folded }) return
        val cleared = SidePotsUiState(before.players.map { it.copy(chips = null, folded = false) })
        show(cleared)
        undo?.cancel()
        undo = viewModelScope.launch {
            if (snackbars.showUndo(messages.newHand, messages.undo) && _uiState.value == cleared) show(before)
        }
    }
}
