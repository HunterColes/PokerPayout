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
import com.huntercoles.pokerpayout.tools.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * @property standings the season's points, top first ([Season]).
 * @property openNight the night shown in full, if one is open.
 */
data class HistoryUiState(
    val nights: List<SavedNight> = emptyList(),
    val year: Int? = null,
    val years: List<Int> = emptyList(),
    val standings: List<Standing> = emptyList(),
    val openNight: SavedNight? = null,
) {
    /** The player of the year (of all time when [year] is null), or several when level on points. */
    val leaders: List<Standing> get() = Season.leaders(standings)

    companion object {
        /** The year picker shows all time and up to this many years. */
        const val MAX_YEARS = 4

        /** The screen for [nights]: [year]'s season (all time when null or when no night was played then). */
        fun of(nights: List<SavedNight>, year: Int? = null, openId: Long? = null): HistoryUiState {
            val years = Season.years(nights).take(MAX_YEARS)
            val season = year?.takeIf { it in years }
            return HistoryUiState(
                nights = nights,
                year = season,
                years = years,
                standings = Season.standings(nights, season),
                openNight = nights.firstOrNull { it.id == openId },
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
}

/**
 * History (PP-037): the nights saved from the Payouts tab ([NightStore]), read-only. A night opens in
 * full; deleting one applies at once and offers Undo on the app's snackbar. Every night can be saved
 * as a CSV file ([NightCsv], UTF-8) where the player picks.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val store: NightStore,
    private val snackbars: SnackbarController,
    private val messages: HistoryMessages,
    private val files: DocumentFiles,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState.of(store.nights.value))
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.nights.collect { nights -> _uiState.update { HistoryUiState.of(nights, it.year, it.openNight?.id) } }
        }
    }

    fun acceptIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.SelectYear -> _uiState.update { HistoryUiState.of(it.nights, intent.year, it.openNight?.id) }
            is HistoryIntent.Open -> _uiState.update { HistoryUiState.of(it.nights, it.year, intent.id) }
            HistoryIntent.Close -> _uiState.update { it.copy(openNight = null) }
            is HistoryIntent.Delete -> delete(intent.id)
            is HistoryIntent.SaveCsv -> saveCsv(intent.uri)
        }
    }

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
            snackbars.hostState.currentSnackbarData?.dismiss()
            snackbars.showMessage(message)
        }
    }

    private fun delete(id: Long) {
        val night = store.get(id) ?: return
        _uiState.update { it.copy(openNight = null) }
        store.delete(id)
        // The newest action gets the snackbar at once: one still up from before goes, its Undo with it
        snackbars.hostState.currentSnackbarData?.dismiss()
        viewModelScope.launch {
            if (snackbars.showUndo(messages.deleted, messages.undo)) store.put(night)
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
}
