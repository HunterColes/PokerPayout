package com.huntercoles.pokerpayout.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.preferences.OddsCalculatorPreferences
import com.huntercoles.pokerpayout.tools.poker.NextCardBreakdown
import com.huntercoles.pokerpayout.tools.poker.OddsEngine
import com.huntercoles.pokerpayout.tools.poker.OddsInputException
import com.huntercoles.pokerpayout.tools.poker.OddsRequest
import com.huntercoles.pokerpayout.tools.poker.OddsResult
import com.huntercoles.pokerpayout.tools.poker.OddsSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The odds screen's state.
 *
 * @property result the latest odds for [table], one [OddsResult.players] entry per seat. Cleared the
 *   moment the table changes, so it never describes other cards; Monte Carlo snapshots refine it.
 * @property breakdown who leads after each possible next card (flop or turn, every hand known).
 * @property error why the last calculation failed (a duplicated card from an old save, say).
 * @property runOut run it out, full screen, when not `null`.
 */
data class OddsCalculatorUiState(
    val table: OddsTable = OddsTable(),
    val keypad: KeypadState = KeypadState(),
    val result: OddsResult? = null,
    val isCalculating: Boolean = false,
    val error: String? = null,
    val breakdown: NextCardBreakdown? = null,
    val fourColourDeck: Boolean = false,
    val runOut: RunOutState? = null,
) {
    /** Run it out needs every hand known, two players in, and a board of 0, 3 or 4 cards dealt in order. */
    val canRunItOut: Boolean
        get() = error == null && table.contestants.size >= OddsTable.MIN_SEATS && table.allHandsKnown &&
            table.boardCards.size in RUN_IT_OUT_BOARDS && table.board.take(table.boardCards.size).all { it is SlotValue.Known }

    private companion object {
        val RUN_IT_OUT_BOARDS = setOf(0, 3, 4)
    }
}

/**
 * Owns the odds calculation and the table (S8, S9, S10). Odds are live: every change to the table
 * cancels the run in flight, clears its numbers at once, and starts a new run after a short pause
 * ([DEBOUNCE_MS]) so typing a hand doesn't start a calculation per key.
 */
@HiltViewModel
class OddsCalculatorViewModel @Inject constructor(
    private val prefs: OddsCalculatorPreferences,
    private val engine: OddsEngine,
    private val settings: OddsSettings,
    private val snackbars: SnackbarController,
    private val seeds: RunOutSeeds,
    private val messages: OddsMessages,
) : ViewModel() {

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<OddsCalculatorUiState> = _uiState.asStateFlow()

    private val dealer = RunItOutDealer(engine)
    private var calculation: Job? = null
    private var runOutJob: Job? = null

    /** Bumped on every table change; late snapshots from an older run are dropped. */
    private var generation = 0

    init {
        recalculate()
    }

    @Suppress("CyclomaticComplexMethod") // one branch per intent, each a one-liner
    fun acceptIntent(intent: OddsCalculatorIntent) {
        when (intent) {
            is OddsCalculatorIntent.SelectSlot -> edit { OddsKeypad.select(it, intent.slot) }
            is OddsCalculatorIntent.PickRank -> edit { OddsKeypad.pickRank(it, intent.rank) }
            is OddsCalculatorIntent.PickSuit -> edit { OddsKeypad.pickSuit(it, intent.suit) }
            is OddsCalculatorIntent.PlaceCard -> edit { OddsKeypad.place(it, intent.card) }
            OddsCalculatorIntent.Backspace -> edit(OddsKeypad::backspace)
            OddsCalculatorIntent.RandomHand -> edit(OddsKeypad::randomHand)
            OddsCalculatorIntent.CloseKeypad -> edit { it.copy(keypad = KeypadState()) }
            OddsCalculatorIntent.AddPlayer -> edit { e ->
                val t = e.table.addSeat()
                TableEdit(t, if (t == e.table) e.keypad else KeypadState(SlotRef.Hole(t.seats.lastIndex, 0)))
            }
            is OddsCalculatorIntent.RemovePlayer -> edit { e ->
                e.table.removeSeat(intent.seat).let { t ->
                    TableEdit(t, if (t == e.table) e.keypad else OddsKeypad.retarget(e.keypad, t, removedSeat = intent.seat))
                }
            }
            is OddsCalculatorIntent.Fold -> edit { e ->
                e.table.fold(intent.seat, intent.folded).let { t -> TableEdit(t, OddsKeypad.retarget(e.keypad, t)) }
            }
            is OddsCalculatorIntent.Swap -> edit { it.copy(table = it.table.swap(intent.a, intent.b)) }
            is OddsCalculatorIntent.ClearHand -> edit { it.copy(table = it.table.clearHand(intent.seat)) }
            OddsCalculatorIntent.NewHand -> replaceTable(messages.newHand) { it.newHand() }
            OddsCalculatorIntent.ClearTable -> replaceTable(messages.tableCleared) { OddsTable() }
            is OddsCalculatorIntent.SetFourColourDeck -> {
                prefs.fourColourDeck = intent.enabled
                _uiState.update { it.copy(fourColourDeck = intent.enabled) }
            }
            OddsCalculatorIntent.RunItOut -> startRunOut()
            OddsCalculatorIntent.DealNext -> runOutStep(dealer::dealNext)
            OddsCalculatorIntent.RunAgain -> runOutStep(dealer::runAgain)
            OddsCalculatorIntent.RunTwice -> runOutStep(dealer::runTwice)
            is OddsCalculatorIntent.SetRunOutLandscape ->
                _uiState.update { s -> s.copy(runOut = s.runOut?.copy(landscape = intent.landscape)) }
            OddsCalculatorIntent.ExitRunItOut -> {
                runOutJob?.cancel()
                _uiState.update { it.copy(runOut = null) }
            }
        }
    }

    // ------------------------------------------------------------------ table edits

    /** Applies a keypad or seat edit. A changed table is saved and its odds start over. */
    private fun edit(transform: (TableEdit) -> TableEdit) {
        val before = _uiState.value
        val after = transform(TableEdit(before.table, before.keypad))
        if (after.table == before.table) {
            _uiState.update { it.copy(keypad = after.keypad) }
            return
        }
        save(after.table)
        _uiState.update { it.copy(table = after.table, keypad = after.keypad) }
        recalculate()
    }

    /**
     * New hand / clear table: applies now, with Undo on the snackbar for a few seconds. The keypad
     * stays closed meanwhile: the app's snackbar sits where the suit keys would be.
     */
    private fun replaceTable(message: String, transform: (OddsTable) -> OddsTable) {
        val previous = _uiState.value.table
        val next = transform(previous)
        if (next == previous) return
        edit { TableEdit(next, KeypadState()) }
        viewModelScope.launch {
            if (snackbars.showUndo(message, messages.undo)) {
                edit { TableEdit(previous, KeypadState()) }
            }
        }
    }

    // ------------------------------------------------------------------ calculation

    /** Cancels the run in flight, clears its numbers, and (after [DEBOUNCE_MS]) works out the new table. */
    private fun recalculate() {
        generation++
        calculation?.cancel()
        val request = _uiState.value.table.toRequest()
        _uiState.update { it.copy(result = null, breakdown = null, error = null, isCalculating = request != null) }
        if (request != null) {
            val runId = generation
            calculation = viewModelScope.launch { work(request, runId) }
        }
    }

    /** Streams the odds for [request]; then, on a flop or turn with every hand known, the next-card grid. */
    private suspend fun work(request: OddsRequest, runId: Int) {
        delay(DEBOUNCE_MS)
        try {
            val final = engine.calculate(request, settings)
                .onEach { snapshot -> publish(runId) { it.copy(result = snapshot, isCalculating = !snapshot.complete) } }
                .lastOrNull()
            val contestants = request.seats.filterNot { it.folded }
            val wantsGrid = request.board.size == Street.FLOP.boardCards || request.board.size == Street.TURN.boardCards
            if (final?.exact == true && wantsGrid && contestants.all { it.cards.size == SeatState.HOLE_CARDS }) {
                val breakdown = engine.nextCardBreakdown(request)
                publish(runId) { it.copy(breakdown = breakdown) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OddsInputException) {
            publish(runId) { it.failed(e.message ?: messages.cantCalculate) }
        } catch (e: Exception) { // shown on screen, never a crash
            publish(runId) { it.failed(messages.calculationFailed(e.message ?: e::class.simpleName.orEmpty())) }
        }
    }

    /** Applies [transform] unless the table has changed since run [runId] started. */
    private fun publish(runId: Int, transform: (OddsCalculatorUiState) -> OddsCalculatorUiState) {
        if (runId == generation) _uiState.update(transform)
    }

    // ------------------------------------------------------------------ run it out

    private fun startRunOut() {
        val state = _uiState.value
        val request = state.table.toRequest()
        if (!state.canRunItOut || request == null || state.runOut != null) return
        runOutJob?.cancel()
        runOutJob = viewModelScope.launch {
            val started = dealer.start(request, seeds.next())
            _uiState.update { it.copy(runOut = started) }
        }
    }

    /** Runs one run-it-out transition; taps while one is being worked out are ignored. */
    private fun runOutStep(step: suspend (RunOutState) -> RunOutState) {
        val current = _uiState.value.runOut ?: return
        if (current.isDealing) return
        _uiState.update { it.copy(runOut = current.copy(isDealing = true)) }
        runOutJob = viewModelScope.launch {
            val next = step(current)
            // Exiting mid-deal cancels this job, so a closed run it out is never revived.
            _uiState.update { s ->
                s.copy(runOut = s.runOut?.let { now -> next.copy(isDealing = false, landscape = now.landscape) })
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    private fun load(): OddsCalculatorUiState {
        val count = prefs.getPlayerCount().coerceIn(OddsTable.MIN_SEATS, OddsTable.MAX_SEATS)
        val seats = (1..count).map { id ->
            val cards = SlotCodec.decode(prefs.getPlayerCards(id), SeatState.HOLE_CARDS, allowRandom = true)
            SeatState(cards, prefs.getPlayerFolded(id))
        }
        val board = SlotCodec.decode(prefs.getCommunityCards(), OddsTable.BOARD_SLOTS, allowRandom = false)
        val table = runCatching { OddsTable(seats, board) }.getOrElse { OddsTable(seats.map { it.copy(folded = false) }, board) }
        val nothingTyped = table.seats.all { it.isUnknown }
        return OddsCalculatorUiState(
            table = table,
            keypad = if (nothingTyped) KeypadState(target = table.nextOpen()) else KeypadState(),
            fourColourDeck = prefs.fourColourDeck,
        )
    }

    private fun save(table: OddsTable) {
        val previousCount = prefs.getPlayerCount()
        prefs.setPlayerCount(table.seats.size)
        table.seats.forEachIndexed { i, seat ->
            prefs.setPlayerCards(i + 1, SlotCodec.encode(seat.cards))
            prefs.setPlayerFolded(i + 1, seat.folded)
        }
        for (id in table.seats.size + 1..maxOf(previousCount, table.seats.size)) {
            prefs.setPlayerCards(id, "")
            prefs.setPlayerFolded(id, false)
        }
        prefs.setCommunityCards(SlotCodec.encode(table.board))
    }

    companion object {
        /** Typing pause before a calculation starts. */
        const val DEBOUNCE_MS = 150L
    }
}

/** The state after a failed calculation: no numbers, and why. */
private fun OddsCalculatorUiState.failed(message: String) =
    copy(isCalculating = false, result = null, breakdown = null, error = message)
