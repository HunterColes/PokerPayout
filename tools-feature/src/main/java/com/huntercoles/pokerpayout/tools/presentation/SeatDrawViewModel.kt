package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.PlayerNamesProvider
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.seats.SeatDraw
import com.huntercoles.pokerpayout.tools.seats.SeatDrawSeeds
import com.huntercoles.pokerpayout.tools.seats.SeatDrawStore
import com.huntercoles.pokerpayout.tools.seats.SeatDrawer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * The seat draw screen's state (S14, PP-036).
 *
 * @property players the names for the next draw, as typed (a blank one is drawn as "Player N").
 * @property seatNames [players] as they will be seated, blanks filled in.
 * @property fromBank whether [players] follow the Bank's names (else they were changed for the draw).
 * @property draw the last draw, saved across process death and restarts.
 * @property editingNames whether the name fields are open.
 * @property dealToAnimate non-zero right after a deal for the button: the screen plays that deal once.
 */
data class SeatDrawUiState(
    val players: List<String> = emptyList(),
    val seatNames: List<String> = players,
    val fromBank: Boolean = true,
    val seatsPerTable: Int = SeatDrawer.DEFAULT_SEATS_PER_TABLE,
    val draw: SeatDraw? = null,
    val editingNames: Boolean = false,
    val dealToAnimate: Int = 0,
) {
    /** How the next draw would split the players: balanced tables, largest first. */
    val tableSizes: List<Int> get() = SeatDrawer.tableSizes(seatNames.size, seatsPerTable)

    /** True when the draw on screen doesn't seat exactly the players listed now. */
    val drawIsStale: Boolean get() = draw != null && draw.players.sorted() != seatNames.sorted()
}

/** Everything the seat draw screen can ask for. */
sealed interface SeatDrawIntent {
    /** Plays with [count] players: names are added ("Player N") or dropped from the end. */
    data class SetPlayerCount(val count: Int) : SeatDrawIntent
    data class SetSeatsPerTable(val seats: Int) : SeatDrawIntent
    data class SetName(val index: Int, val name: String) : SeatDrawIntent
    data class EditNames(val open: Boolean) : SeatDrawIntent

    /** Back to the Bank's players and names. */
    data object UseBankNames : SeatDrawIntent

    /** Seats everyone at random; a redraw offers Undo. */
    data object DrawSeats : SeatDrawIntent

    /** Every table deals one card a seat, high card takes the button; dealing again offers Undo. */
    data object DealButton : SeatDrawIntent
}

/**
 * Seat draw (PP-036): who sits where, and who gets the button. The players start as the Bank's
 * (through [PlayerNamesProvider], so this module never imports the Bank) and can be changed for the
 * draw. Every draw is seeded from [SeatDrawSeeds] and saved in [SeatDrawStore], so it survives process
 * death; redrawing applies at once and offers Undo on the app's snackbar.
 */
@HiltViewModel
class SeatDrawViewModel @Inject constructor(
    private val store: SeatDrawStore,
    private val bankNames: PlayerNamesProvider,
    private val seeds: SeatDrawSeeds,
    private val snackbars: SnackbarController,
    private val messages: SeatDrawMessages,
) : ViewModel() {

    private val _uiState: MutableStateFlow<SeatDrawUiState>
    val uiState: StateFlow<SeatDrawUiState>

    private var deals = 0
    private var undo: Job? = null

    init {
        val saved = store.players()?.takeIf { it.size >= SeatDrawer.MIN_PLAYERS }
        val players = saved ?: startingPlayers(bankNames.currentNames())
        _uiState = MutableStateFlow(
            SeatDrawUiState(
                players = players,
                seatNames = players.seatNames(messages),
                fromBank = saved == null,
                seatsPerTable = store.seatsPerTable(),
                draw = store.draw(),
            ),
        )
        uiState = _uiState.asStateFlow()
    }

    fun acceptIntent(intent: SeatDrawIntent) {
        when (intent) {
            is SeatDrawIntent.SetPlayerCount -> setPlayerCount(intent.count)
            is SeatDrawIntent.SetSeatsPerTable -> setSeatsPerTable(intent.seats)
            is SeatDrawIntent.SetName -> setName(intent.index, intent.name)
            is SeatDrawIntent.EditNames -> _uiState.update { it.copy(editingNames = intent.open) }
            SeatDrawIntent.UseBankNames -> setPlayers(startingPlayers(bankNames.currentNames()), fromBank = true)
            SeatDrawIntent.DrawSeats -> drawSeats()
            SeatDrawIntent.DealButton -> dealButton()
        }
    }

    private fun setPlayerCount(count: Int) {
        val players = _uiState.value.players
        val wanted = count.coerceIn(SeatDrawer.MIN_PLAYERS, SeatDrawer.MAX_PLAYERS)
        if (wanted == players.size) return
        setPlayers(if (wanted < players.size) players.take(wanted) else players + List(wanted - players.size) { "" })
    }

    private fun setSeatsPerTable(seats: Int) {
        val wanted = seats.coerceIn(SeatDrawer.MIN_SEATS_PER_TABLE, SeatDrawer.MAX_SEATS_PER_TABLE)
        store.setSeatsPerTable(wanted)
        _uiState.update { it.copy(seatsPerTable = wanted) }
    }

    private fun setName(index: Int, name: String) {
        val players = _uiState.value.players
        if (index !in players.indices) return
        setPlayers(players.toMutableList().apply { this[index] = name.take(MAX_NAME_LENGTH) })
    }

    /** Saves [players] for the draw; [fromBank] means they follow the Bank's again (nothing saved). */
    private fun setPlayers(players: List<String>, fromBank: Boolean = false) {
        store.setPlayers(if (fromBank) null else players)
        _uiState.update { it.copy(players = players, seatNames = players.seatNames(messages), fromBank = fromBank) }
    }

    private fun drawSeats() {
        val state = _uiState.value
        val draw = SeatDrawer.drawSeats(state.seatNames, state.seatsPerTable, Random(seeds.next()))
        _uiState.update { it.copy(editingNames = false) }
        replaceDraw(draw, animate = false, undoMessage = messages.seatsRedrawn)
    }

    private fun dealButton() {
        val draw = _uiState.value.draw ?: return
        val dealt = SeatDrawer.dealButtons(draw, Random(seeds.next()))
        replaceDraw(dealt, animate = true, undoMessage = if (draw.buttonDealt) messages.buttonDealtAgain else null)
    }

    /**
     * Shows and saves [next]. When it replaces a draw that [undoMessage] names, the snackbar offers
     * Undo for the app's 8 s window; a newer change in the meantime (another redraw) takes over the
     * snackbar, and Undo never overwrites a draw made after this one.
     */
    private fun replaceDraw(next: SeatDraw, animate: Boolean, undoMessage: String?) {
        val previous = _uiState.value.draw
        showDraw(next, animate)
        undo?.cancel()
        if (previous == null || undoMessage == null) return
        undo = viewModelScope.launch {
            if (snackbars.showUndo(undoMessage, messages.undo) && _uiState.value.draw == next) showDraw(previous, animate = false)
        }
    }

    private fun showDraw(draw: SeatDraw, animate: Boolean) {
        store.setDraw(draw)
        _uiState.update { it.copy(draw = draw, dealToAnimate = if (animate) ++deals else 0) }
    }

    companion object {
        /** A full table's worth when the Bank has nobody. */
        const val DEFAULT_PLAYERS = 9

        /** Long enough for any real name; short enough to keep a seat on one or two lines. */
        const val MAX_NAME_LENGTH = 24
    }
}

/** The Bank's players, or nine unnamed ones ("Player 1" to "Player 9") when it has none; never fewer than two. */
private fun startingPlayers(bank: List<String>): List<String> {
    val names = bank.take(SeatDrawer.MAX_PLAYERS)
    return when {
        names.isEmpty() -> List(SeatDrawViewModel.DEFAULT_PLAYERS) { "" }
        names.size < SeatDrawer.MIN_PLAYERS -> names + List(SeatDrawer.MIN_PLAYERS - names.size) { "" }
        else -> names
    }
}

/** The names as they will be seated: a blank one is "Player N", N its place in the list. */
private fun List<String>.seatNames(messages: SeatDrawMessages): List<String> =
    mapIndexed { index, name -> name.trim().ifEmpty { messages.defaultName(index + 1) } }

/** The strings the seat draw's ViewModel needs itself: its snackbars and default names. */
class SeatDrawMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val seatsRedrawn: String get() = context.getString(R.string.seat_draw_snackbar_redrawn)
    val buttonDealtAgain: String get() = context.getString(R.string.seat_draw_snackbar_redealt)
    val undo: String get() = context.getString(R.string.seat_draw_undo)

    /** "Player 3", for a player nobody has named. */
    fun defaultName(number: Int): String = context.getString(R.string.seat_draw_default_player, number)
}
