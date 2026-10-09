package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import com.huntercoles.pokerpayout.tools.table.Chance
import com.huntercoles.pokerpayout.tools.table.OutsMath
import com.huntercoles.pokerpayout.tools.table.PotOdds
import com.huntercoles.pokerpayout.tools.table.Street
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Outs and pot odds (S20).
 *
 * @property outs the cards that make the hand, [OutsMath.MIN_OUTS] to [OutsMath.MAX_OUTS].
 * @property pot what is in the middle, with the bet you face (null: not typed).
 * @property call what it costs to call (null: not typed).
 */
data class OutsUiState(
    val street: Street = Street.Flop,
    val outs: Int = DEFAULT_OUTS,
    val pot: Long? = null,
    val call: Long? = null,
) {
    /** By the river: both cards from the flop, the river from the turn. */
    val byRiver: Chance get() = OutsMath.byRiver(outs, street)

    /** The next card alone (on the turn, the same as [byRiver]). */
    val nextCard: Chance get() = OutsMath.nextCard(outs, street)

    /** The equity a call needs, in percent, once the pot and the call are typed. */
    val equityNeeded: Double? get() = PotOdds.equityNeeded(pot ?: 0L, call ?: 0L)

    companion object {
        /** A flush draw, the draw everyone knows. */
        const val DEFAULT_OUTS = 9
    }
}

sealed interface OutsIntent {
    data class SetStreet(val street: Street) : OutsIntent

    data class SetOuts(val outs: Int) : OutsIntent

    /** Null when the field is empty. */
    data class SetPot(val chips: Long?) : OutsIntent

    data class SetCall(val chips: Long?) : OutsIntent
}

/** Outs and pot odds: plain inputs, kept in [TableToolsMemory] while the app runs; the maths is [OutsMath]. */
@HiltViewModel
class OutsViewModel @Inject constructor(private val memory: TableToolsMemory) : ViewModel() {
    private val _uiState = MutableStateFlow(memory.outs ?: OutsUiState())
    val uiState: StateFlow<OutsUiState> = _uiState.asStateFlow()

    fun acceptIntent(intent: OutsIntent) {
        val state = _uiState.value
        val next = when (intent) {
            is OutsIntent.SetStreet -> state.copy(street = intent.street)
            is OutsIntent.SetOuts -> state.copy(outs = intent.outs.coerceIn(OutsMath.MIN_OUTS, OutsMath.MAX_OUTS))
            is OutsIntent.SetPot -> state.copy(pot = intent.chips?.coerceAtLeast(0L))
            is OutsIntent.SetCall -> state.copy(call = intent.chips?.coerceAtLeast(0L))
        }
        memory.outs = next
        _uiState.value = next
    }
}
