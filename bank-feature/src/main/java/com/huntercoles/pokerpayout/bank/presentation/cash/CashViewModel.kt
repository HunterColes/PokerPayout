package com.huntercoles.pokerpayout.bank.presentation.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.domain.cash.BankMode
import com.huntercoles.pokerpayout.core.domain.cash.CashGame
import com.huntercoles.pokerpayout.core.domain.cash.CashPlayer
import com.huntercoles.pokerpayout.core.domain.cash.CashSettlement
import com.huntercoles.pokerpayout.core.domain.cash.CashTransfer
import com.huntercoles.pokerpayout.core.domain.cash.ChipCheck
import com.huntercoles.pokerpayout.core.domain.cash.SettleCashUseCase
import com.huntercoles.pokerpayout.core.preferences.BankPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The cash game in the Bank (S13, PP-029, D4): who bought in for what, what they cashed out, the
 * chip check, and who pays whom.
 *
 * - **Separate.** It reads and writes only the cash game's own keys in [BankPreferences], so the
 *   tournament's resets never reach it and its own clear never reaches the tournament. The mode
 *   switch only changes which one the Bank shows.
 * - **Undo.** Every change to the money applies at once and can be taken back from the snackbar
 *   while it shows, or from the top bar's Undo, newest first, up to [MAX_UNDO] changes. Undo restores
 *   the whole game as it was (a snapshot), keeping names typed since.
 * - **Settle-up.** Recomputed from the ledger after every change. Ticks for payments a new settle-up
 *   no longer lists go, and so does a split chosen for a difference the chip check no longer shows.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // one small function per action
class CashViewModel @Inject constructor(
    private val bankPreferences: BankPreferences,
    private val settleCash: SettleCashUseCase,
    private val feedback: CashFeedback,
) : ViewModel() {

    private val undoStack = ArrayDeque<UndoEntry>()
    private var snackbar: Job? = null

    private val _uiState = MutableStateFlow(
        CashUiState(mode = bankPreferences.getBankMode()).withGame(normalized(bankPreferences.getCashGame()))
    )
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()

    private val state: CashUiState get() = _uiState.value
    private val game: CashGame get() = state.game

    @Suppress("CyclomaticComplexMethod") // One branch per intent; each delegates.
    fun acceptIntent(intent: CashIntent) {
        when (intent) {
            is CashIntent.SwitchMode -> switchMode(intent.mode)
            CashIntent.ShowAddPlayer -> if (state.canAddPlayer) showSheet(CashSheet.AddPlayer)
            is CashIntent.AddPlayer -> addPlayer(intent.name, intent.buyInCents)
            is CashIntent.OpenPlayer -> openPlayer(intent.playerId)
            is CashIntent.Rename -> rename(intent.playerId, intent.name)
            is CashIntent.TopUp -> topUp(intent.playerId, intent.amountCents)
            is CashIntent.RemoveBuyIn -> removeBuyIn(intent.playerId, intent.index)
            is CashIntent.SetCashOut -> setCashOut(intent.playerId, intent.amountCents)
            is CashIntent.RemovePlayer -> removePlayer(intent.playerId)
            CashIntent.SplitDifference -> splitDifference()
            CashIntent.Recount -> recount()
            is CashIntent.SetPaid -> setPaid(intent.transfer, intent.paid)
            CashIntent.ClearGame -> clearGame()
            CashIntent.DismissSheet -> showSheet(null)
            CashIntent.Undo -> undo(expected = null)
        }
    }

    // Actions ------------------------------------------------------------------------------------

    private fun switchMode(mode: BankMode) {
        if (mode == state.mode) return
        bankPreferences.saveBankMode(mode)
        _uiState.update { it.copy(mode = mode, sheet = null) }
    }

    private fun openPlayer(playerId: Int) {
        if (game.ledger.player(playerId) != null) showSheet(CashSheet.Player(playerId))
    }

    private fun addPlayer(name: String, buyInCents: Long) {
        showSheet(null)
        if (buyInCents <= 0L || !state.canAddPlayer) return
        val player = CashPlayer(
            id = game.nextPlayerId,
            name = name.trim().ifEmpty { feedback.defaultName(game.ledger.players.size + 1) },
            buyInsCents = listOf(buyInCents),
        )
        record(feedback.boughtIn(player.name, buyInCents)) { it.withPlayers(it.ledger.players + player) }
    }

    /** Not a change to the money, so not undone; Undo keeps the names typed since. */
    private fun rename(playerId: Int, name: String) {
        val player = game.ledger.player(playerId) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == player.name) return
        save(game.replace(player.copy(name = trimmed)))
    }

    private fun topUp(playerId: Int, amountCents: Long) {
        val player = game.ledger.player(playerId) ?: return
        if (amountCents <= 0L || player.buyInsCents.size >= MAX_CASH_BUY_INS) return
        record(feedback.toppedUp(player.name, amountCents)) {
            it.replace(player.copy(buyInsCents = player.buyInsCents + amountCents))
        }
    }

    private fun removeBuyIn(playerId: Int, index: Int) {
        val player = game.ledger.player(playerId) ?: return
        if (player.buyInsCents.size <= 1 || index !in player.buyInsCents.indices) return
        record(feedback.buyInRemoved(player.name, player.buyInsCents[index])) {
            it.replace(player.copy(buyInsCents = player.buyInsCents.filterIndexed { i, _ -> i != index }))
        }
    }

    private fun setCashOut(playerId: Int, amountCents: Long?) {
        val player = game.ledger.player(playerId) ?: return
        val cents = amountCents?.coerceAtLeast(0L)
        if (cents == player.cashOutCents) return
        record(feedback.cashedOut(player.name, cents)) { it.replace(player.copy(cashOutCents = cents)) }
    }

    private fun removePlayer(playerId: Int) {
        val player = game.ledger.player(playerId) ?: return
        showSheet(null)
        record(feedback.playerRemoved(player.name)) { it.withPlayers(it.ledger.players - player) }
    }

    private fun splitDifference() {
        val check = game.ledger.chipCheck as? ChipCheck.Off ?: return
        val settlement = settleCash(game.ledger, splitDifference = true)
        if (settlement !is CashSettlement.Settled) return
        record(feedback.split(check.differenceCents)) { it.copy(splitCents = check.differenceCents) }
    }

    private fun recount() {
        if (game.splitCents == null) return
        record(feedback.split(null)) { it.copy(splitCents = null) }
    }

    private fun setPaid(transfer: CashTransfer, paid: Boolean) {
        if (transfer !in state.transfers || state.isPaid(transfer) == paid) return
        val message = feedback.paid(state.nameOf(transfer.fromId), state.nameOf(transfer.toId), transfer.amountCents, paid)
        record(message) { if (paid) it.copy(paid = it.paid + transfer) else it.copy(paid = it.paid - transfer) }
    }

    private fun clearGame() {
        if (game.isEmpty) return
        showSheet(null)
        record(feedback.cleared(game.ledger.players.size)) { CashGame() }
    }

    private fun showSheet(sheet: CashSheet?) {
        _uiState.update { it.copy(sheet = sheet) }
    }

    // Recording, with Undo -----------------------------------------------------------------------

    private class UndoEntry(val before: CashGame, val message: String)

    /** Applies [change], saves the result and offers Undo with [message]; nothing if it changed nothing. */
    private fun record(message: String, change: (CashGame) -> CashGame) {
        val before = game
        val after = normalized(change(before))
        if (after == before) return
        val entry = UndoEntry(before, message)
        undoStack.addLast(entry)
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        save(after)
        offerUndo(entry)
    }

    private fun save(next: CashGame) {
        bankPreferences.saveCashGame(next)
        _uiState.update { current ->
            val sheet = current.sheet.takeUnless { it is CashSheet.Player && next.ledger.player(it.playerId) == null }
            current.withGame(next).copy(sheet = sheet)
        }
    }

    /** One snackbar at a time: a new change replaces the last one's (it stays in the history). */
    private fun offerUndo(entry: UndoEntry) {
        snackbar?.cancel()
        snackbar = viewModelScope.launch {
            if (feedback.showUndo(entry.message)) undo(expected = entry)
        }
    }

    /**
     * Takes back the newest change. From the snackbar ([expected] set), only if that change is still
     * the newest: the top bar's Undo may have taken it back already.
     */
    private fun undo(expected: UndoEntry?) {
        val entry = undoStack.lastOrNull()?.takeIf { expected == null || it === expected } ?: return
        undoStack.removeLast()
        if (expected == null) {
            snackbar?.cancel()
            snackbar = null
        }
        // Names may have changed since; keep today's.
        val names = game.ledger.players.associate { it.id to it.name }
        val restored = entry.before.withPlayers(entry.before.ledger.players.map { it.copy(name = names[it.id] ?: it.name) })
        save(restored)
    }

    // The settle-up --------------------------------------------------------------------------------

    /**
     * Drops a split chosen for another difference, and ticks for payments a new settle-up no longer
     * lists. While the settle-up waits (a count being corrected) the ticks stay: the payments they
     * belong to may come back unchanged.
     */
    private fun normalized(game: CashGame): CashGame {
        val split = game.copy(splitCents = game.splitCents.takeIf { game.splitChosen })
        val paid = when (val settlement = settleCash(split.ledger, split.splitChosen)) {
            is CashSettlement.Settled -> split.paid.intersect(settlement.transfers.toSet())
            CashSettlement.Empty -> emptySet()
            else -> split.paid
        }
        return split.copy(paid = paid)
    }

    private fun CashUiState.withGame(game: CashGame) = copy(
        game = game,
        settlement = settleCash(game.ledger, game.splitChosen),
        undoLabel = undoStack.lastOrNull()?.message,
    )

    private companion object {
        /** How many changes Undo can take back. */
        const val MAX_UNDO = 20
    }
}

private fun CashGame.withPlayers(players: List<CashPlayer>): CashGame = copy(ledger = ledger.copy(players = players))

private fun CashGame.replace(player: CashPlayer): CashGame =
    withPlayers(ledger.players.map { if (it.id == player.id) player else it })
