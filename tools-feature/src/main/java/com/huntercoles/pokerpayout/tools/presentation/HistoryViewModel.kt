package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupProblem
import com.huntercoles.pokerpayout.core.backup.DocumentFiles
import com.huntercoles.pokerpayout.core.coroutines.IoDispatcher
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.domain.history.NightCsv
import com.huntercoles.pokerpayout.core.domain.history.NightStore
import com.huntercoles.pokerpayout.core.domain.history.SavedNight
import com.huntercoles.pokerpayout.core.domain.history.Season
import com.huntercoles.pokerpayout.core.domain.history.Standing
import com.huntercoles.pokerpayout.core.domain.players.KnownPlayer
import com.huntercoles.pokerpayout.core.domain.players.PlayerMerges
import com.huntercoles.pokerpayout.core.domain.players.RegularsStore
import com.huntercoles.pokerpayout.core.domain.players.Roster
import com.huntercoles.pokerpayout.tools.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * History (S16, PP-037): the saved nights, the latest first, and the season's standings.
 *
 * @property year the season shown: a year, or null for all time.
 * @property years the years to pick from, the latest first (at most [MAX_YEARS]; older nights still
 *   count in all time).
 * @property standings the season's points, top first ([Season]), with the names merged as one ([merges]).
 * @property openNight the night shown in full, if one is open.
 * @property merges the names merged as one person (PP-110).
 * @property known the names the Bank has used (PP-110): someone a player could be, without a saved night.
 * @property player the player opened from the standings, to merge or separate their names (S26b).
 */
data class HistoryUiState(
    val nights: List<SavedNight> = emptyList(),
    val year: Int? = null,
    val years: List<Int> = emptyList(),
    val standings: List<Standing> = emptyList(),
    val openNight: SavedNight? = null,
    val merges: PlayerMerges = PlayerMerges.NONE,
    val known: List<KnownPlayer> = emptyList(),
    val player: PlayerPanel? = null,
) {
    /** The player of the year (of all time when [year] is null), or several when level on points. */
    val leaders: List<Standing> get() = Season.leaders(standings)

    /** [name]'s panel open (null closes it), with [picked] picked as the same person. */
    fun withPlayer(name: String?, picked: String? = null): HistoryUiState =
        copy(player = name?.let { PlayerPanel.of(it, nights, known, merges, picked) })

    companion object {
        /** The year picker shows all time and up to this many years. */
        const val MAX_YEARS = 4

        /** The screen for [nights]: [year]'s season (all time when null or when no night was played then). */
        fun of(
            nights: List<SavedNight>,
            year: Int? = null,
            openId: Long? = null,
            merges: PlayerMerges = PlayerMerges.NONE,
            known: List<KnownPlayer> = emptyList(),
        ): HistoryUiState {
            val years = Season.years(nights).take(MAX_YEARS)
            val season = year?.takeIf { it in years }
            return HistoryUiState(
                nights = nights,
                year = season,
                years = years,
                standings = Season.standings(nights, season, merges),
                openNight = nights.firstOrNull { it.id == openId },
                merges = merges,
                known = known,
            )
        }
    }
}

/** Everything the History screen can ask for. */
sealed interface HistoryIntent {
    /** Shows [year]'s season, or all time when null. */
    data class SelectYear(val year: Int?) : HistoryIntent

    data class Open(val id: Long) : HistoryIntent

    data object Close : HistoryIntent

    /** Deletes the night at once; the snackbar offers Undo. */
    data class Delete(val id: Long) : HistoryIntent

    /** The file picker made [uri] for the CSV: write every night to it. */
    data class SaveCsv(val uri: Uri) : HistoryIntent

    // One person under two names (S26b, PP-110) --------------------------------------------------

    /** A player tapped in the standings: their names, and who they could be the same person as. */
    data class OpenPlayer(val name: String) : HistoryIntent

    data object ClosePlayer : HistoryIntent

    /** [name] is the same person as the player open (null: back to the list): which name to keep? */
    data class PickSame(val name: String?) : HistoryIntent

    /** [from] counts as [into] from now on; at once, with Undo on the snackbar. */
    data class Merge(val from: String, val into: String) : HistoryIntent

    /** [spelling] counts on its own again; at once, with Undo on the snackbar. */
    data class Separate(val spelling: String) : HistoryIntent
}

/**
 * History (PP-037): the nights saved from the Payouts tab ([NightStore]), read-only. A night opens in
 * full; deleting one applies at once and offers Undo on the app's snackbar. Every night can be saved
 * as a CSV file ([NightCsv], UTF-8) where the player picks.
 *
 * Two names of one person (PP-110) are merged here, so their season adds up: a player opened from
 * the standings is merged with someone who never played a night with them, keeping either name, or
 * one of their names is separated again; both at once, with Undo. Nights stay as they were saved:
 * a merge only says which name counts as which ([RegularsStore]).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val store: NightStore,
    private val regulars: RegularsStore,
    private val snackbars: SnackbarController,
    private val messages: HistoryMessages,
    private val files: DocumentFiles,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HistoryUiState.of(store.nights.value, merges = regulars.merges.value, known = regulars.players.value),
    )
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(store.nights, regulars.merges, regulars.players) { nights, merges, known -> Triple(nights, merges, known) }
                .collect { (nights, merges, known) ->
                    _uiState.update { state ->
                        HistoryUiState.of(nights, state.year, state.openNight?.id, merges, known)
                            .withPlayer(state.player?.name, state.player?.picked?.name)
                    }
                }
        }
    }

    fun acceptIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.SelectYear -> _uiState.update { rebuilt(it, year = intent.year) }
            is HistoryIntent.Open -> _uiState.update { rebuilt(it, openId = intent.id) }
            HistoryIntent.Close -> _uiState.update { it.copy(openNight = null) }
            is HistoryIntent.Delete -> delete(intent.id)
            is HistoryIntent.SaveCsv -> saveCsv(intent.uri)
            is HistoryIntent.OpenPlayer -> _uiState.update { it.withPlayer(intent.name) }
            HistoryIntent.ClosePlayer -> _uiState.update { it.withPlayer(null) }
            is HistoryIntent.PickSame -> _uiState.update { it.withPlayer(it.player?.name, intent.name) }
            is HistoryIntent.Merge -> merge(intent.from, intent.into)
            is HistoryIntent.Separate -> separate(intent.spelling)
        }
    }

    /** The screen again for another season or night, the player open (if any) kept. */
    private fun rebuilt(state: HistoryUiState, year: Int? = state.year, openId: Long? = state.openNight?.id) =
        HistoryUiState.of(state.nights, year, openId, state.merges, state.known).withPlayer(state.player?.name)

    private fun saveCsv(uri: Uri) {
        val csv = NightCsv.of(store.nights.value)
        viewModelScope.launch {
            val message = try {
                val name = withContext(io) {
                    files.write(uri, csv)
                    files.name(uri)
                }
                messages.csvSaved(name)
            } catch (expected: BackupException) {
                messages.problem(expected.problem)
            }
            snackbars.showMessage(message)
        }
    }

    private fun delete(id: Long) {
        val night = store.get(id) ?: return
        _uiState.update { it.copy(openNight = null) }
        store.delete(id)
        // The newest action gets the snackbar at once: one still up from before goes, its Undo with it
        viewModelScope.launch {
            if (snackbars.showUndo(messages.deleted, messages.undo)) store.put(night)
        }
    }

    /**
     * [from] counts as [into]: never two who played the same night, and nothing changes if they are one
     * person already. The sheet closes, so the snackbar's Undo is in reach.
     */
    private fun merge(from: String, into: String) {
        val before = regulars.merges.value
        val after = before.merge(from, into)
        if (after == before || Roster.sharedNight(store.nights.value, from, into, before) != null) return
        change(before, after, messages.merged(from.trim(), into.trim()))
    }

    /** One of a player's other names counts on its own again. */
    private fun separate(spelling: String) {
        val before = regulars.merges.value
        val after = before.separate(spelling)
        if (after == before) return
        change(before, after, messages.separated(spelling.trim()))
    }

    /** Saves [after] and closes the player; Undo puts [before] back, unless the merges have changed since. */
    private fun change(before: PlayerMerges, after: PlayerMerges, message: String) {
        regulars.setMerges(after)
        _uiState.update { it.withPlayer(null) }
        viewModelScope.launch {
            if (snackbars.showUndo(message, messages.undo) && regulars.merges.value == after) regulars.setMerges(before)
        }
    }
}

/** The strings History's ViewModel shows itself: its snackbars. */
class HistoryMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val deleted: String get() = context.getString(R.string.history_deleted)
    val undo: String get() = context.getString(R.string.history_undo)

    fun csvSaved(name: String?): String =
        name?.let { context.getString(R.string.history_csv_saved_named, it) } ?: context.getString(R.string.history_csv_saved)

    fun problem(problem: BackupProblem): String = context.getString(problem.message)

    /** "Mike R. now counts as Mike". */
    fun merged(from: String, into: String): String = context.getString(R.string.history_merged, from, into)

    /** "Mike R. counts on their own again". */
    fun separated(spelling: String): String = context.getString(R.string.history_separated, spelling)
}
