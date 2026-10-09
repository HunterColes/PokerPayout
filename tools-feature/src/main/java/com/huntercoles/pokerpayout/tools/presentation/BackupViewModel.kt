package com.huntercoles.pokerpayout.tools.presentation

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.backup.AppRestarter
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupLine
import com.huntercoles.pokerpayout.core.backup.Backups
import com.huntercoles.pokerpayout.core.backup.DocumentFiles
import com.huntercoles.pokerpayout.core.backup.OpenedBackup
import com.huntercoles.pokerpayout.core.coroutines.IoDispatcher
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The Backup screen (Tools): save everything to a file, or open one and restore it.
 *
 * @property busy a file is being written or read; the buttons wait.
 * @property preview the file just opened, before anything is restored.
 * @property problem why the last file couldn't be saved or read, in plain words.
 */
data class BackupUiState(
    val busy: Boolean = false,
    val preview: BackupPreview? = null,
    @StringRes val problem: Int? = null,
)

/**
 * What an opened backup holds: when and by which version it was saved, a line per kind of data, and
 * whether it has anything to add (presets, nights) as well as to replace.
 *
 * @property partial parts of it are from a later version of the app and will be left out.
 */
data class BackupPreview(
    val fileName: String?,
    val saved: LocalDate?,
    val appVersion: String?,
    val lines: List<BackupLine>,
    val canMerge: Boolean,
    val partial: Boolean = false,
)

sealed interface BackupIntent {
    /** The file picker made [uri] for a new backup: write it. */
    data class Save(val uri: Uri) : BackupIntent

    /** The file picker chose [uri]: read it and show what it holds. */
    data class Open(val uri: Uri) : BackupIntent

    /** Add the backup's presets and nights to what's here. */
    data object Merge : BackupIntent

    /** Put the backup in place of everything here; the app starts again. */
    data object Replace : BackupIntent

    data object ClosePreview : BackupIntent
}

/**
 * Backups ([Backups]) through the system's file picker. Nothing changes until the player picks Add or
 * Replace in the preview. Adding offers Undo; replacing starts the app again, so every screen reads
 * the restored settings.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: Backups,
    private val files: DocumentFiles,
    private val restarter: AppRestarter,
    private val snackbars: SnackbarController,
    private val messages: BackupMessages,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** The file behind the preview. */
    private var opened: OpenedBackup? = null

    fun acceptIntent(intent: BackupIntent) {
        when (intent) {
            is BackupIntent.Save -> save(intent.uri)
            is BackupIntent.Open -> open(intent.uri)
            BackupIntent.Merge -> merge()
            BackupIntent.Replace -> replace()
            BackupIntent.ClosePreview -> closePreview()
        }
    }

    private fun save(uri: Uri) = work {
        val name = withContext(io) {
            files.write(uri, backups.export())
            files.name(uri)
        }
        _uiState.update { it.copy(busy = false) }
        show(messages.saved(name))
    }

    private fun open(uri: Uri) = work {
        val (backup, name) = withContext(io) { backups.open(files.read(uri)) to files.name(uri) }
        opened = backup
        _uiState.update { it.copy(busy = false, preview = previewOf(backup, name)) }
    }

    private fun merge() {
        val backup = opened ?: return
        work {
            val result = withContext(io) { backups.merge(backup) }
            closePreview()
            val added = backup.linesFor(result)
            if (added.isEmpty()) {
                show(messages.nothingNew)
            } else {
                snackbars.hostState.currentSnackbarData?.dismiss()
                if (snackbars.showUndo(messages.added(added), messages.undo)) withContext(io) { result.undo() }
            }
        }
    }

    private fun replace() {
        val backup = opened ?: return
        work {
            val restart = withContext(io) { backups.replace(backup) }
            closePreview()
            if (restart) restarter.restart() else show(messages.restored)
        }
    }

    /** The newest message gets the snackbar at once; one still up goes. */
    private suspend fun show(message: String) {
        snackbars.hostState.currentSnackbarData?.dismiss()
        snackbars.showMessage(message)
    }

    private fun closePreview() {
        opened = null
        _uiState.update { it.copy(busy = false, preview = null) }
    }

    /** Runs [block] with the buttons waiting; a file that can't be used says why. */
    private fun work(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (expected: BackupException) {
                opened = null
                _uiState.update { it.copy(busy = false, preview = null, problem = expected.problem.message) }
            }
        }
    }

    private fun previewOf(backup: OpenedBackup, name: String?) = BackupPreview(
        fileName = name,
        saved = backup.meta.created?.atZone(ZoneId.systemDefault())?.toLocalDate(),
        appVersion = backup.meta.appVersion,
        lines = backup.lines,
        canMerge = backup.canMerge,
        partial = backup.partial,
    )
}

/** The words the Backup screen's ViewModel shows itself: its snackbars. */
class BackupMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val undo: String get() = context.getString(R.string.backup_undo)
    val nothingNew: String get() = context.getString(R.string.backup_nothing_new)
    val restored: String get() = context.getString(R.string.backup_restored)

    fun saved(name: String?): String =
        name?.let { context.getString(R.string.backup_saved_named, it) } ?: context.getString(R.string.backup_saved)

    /** "Added 2 presets and 3 nights in History". */
    fun added(lines: List<BackupLine>): String = context.getString(R.string.backup_added, list(lines.map(::text)))

    fun text(line: BackupLine): String = when (line) {
        is BackupLine.Counted -> context.resources.getQuantityString(line.plural, line.count, line.count)
        is BackupLine.Named -> context.getString(line.text)
    }

    /** "a", "a and b", "a, b and c". */
    private fun list(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.first()
        else -> context.getString(
            R.string.backup_list_and,
            items.dropLast(1).joinToString(context.getString(R.string.backup_list_separator)),
            items.last(),
        )
    }
}
