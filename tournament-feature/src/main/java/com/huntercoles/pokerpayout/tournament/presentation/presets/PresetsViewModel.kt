package com.huntercoles.pokerpayout.tournament.presentation.presets

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.backup.BackupException
import com.huntercoles.pokerpayout.core.backup.BackupProblem
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.presets.CurrentSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetFiles
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetStore
import com.huntercoles.pokerpayout.tournament.domain.presets.Starter
import com.huntercoles.pokerpayout.tournament.domain.presets.TournamentPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Saved setups (PP-032): save the Tournament setup under a name, load one back, rename or delete one.
 * Every change applies at once, closes the sheet and offers Undo on the snackbar. Loading asks first
 * when it would replace what the host set, and is refused once the clock has started ([CurrentSetup]).
 * A preset also goes to a friend as a small file ([PresetFiles]), and a file of presets opens here.
 * The starter nights (PP-113) are listed too, fitted to tonight: they load like a preset, and a copy
 * of one can be saved among the host's own.
 */
@Suppress("TooManyFunctions") // one function per thing the presets sheet does
@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val store: PresetStore,
    private val setup: CurrentSetup,
    private val snackbars: SnackbarController,
    private val messages: PresetMessages,
    private val time: TimeSource,
    private val files: PresetFiles,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresetsUiState(presets = store.presets.value))
    val uiState: StateFlow<PresetsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.presets.collect { presets -> _uiState.update { it.copy(presets = presets) } }
        }
    }

    fun acceptIntent(intent: PresetsIntent) {
        when (intent) {
            PresetsIntent.Open -> show(PresetSheet.List)
            PresetsIntent.Close -> close()
            PresetsIntent.StartSave -> show(PresetSheet.Save)
            is PresetsIntent.Save -> save(intent.name, intent.includeChipSet)
            is PresetsIntent.Load -> load(intent.id, confirmed = false)
            is PresetsIntent.ConfirmLoad -> load(intent.id, confirmed = true)
            is PresetsIntent.StartRename -> show(PresetSheet.Rename(intent.id))
            is PresetsIntent.Rename -> rename(intent.id, intent.name)
            is PresetsIntent.Delete -> delete(intent.id)
            is PresetsIntent.FileIntent -> file(intent)
            is PresetsIntent.StarterIntent -> starter(intent)
        }
    }

    private fun file(intent: PresetsIntent.FileIntent) {
        when (intent) {
            is PresetsIntent.ShareFile -> _uiState.update { it.copy(sharing = store.get(intent.id)?.let(files::fileOf)) }
            PresetsIntent.FileShared -> _uiState.update { it.copy(sharing = null) }
            is PresetsIntent.ImportFile -> import(intent.uri)
            // The route opens the file picker
            PresetsIntent.PickFile -> Unit
        }
    }

    /**
     * A starter night (PP-113), fitted to tonight: loaded as a preset is (asking first when it would
     * replace what the host set, never once the clock has started), or copied into the host's presets.
     */
    private fun starter(intent: PresetsIntent.StarterIntent) {
        val starter = intent.starter
        val fitted = setup.starter(starter)
        when {
            intent is PresetsIntent.CopyStarter -> copyStarter(starter, fitted)
            !setup.canLoad() -> _uiState.update { it.copy(sheet = PresetSheet.List, canLoad = false) }
            intent is PresetsIntent.LoadStarter && setup.wouldOverwrite(fitted) ->
                _uiState.update { it.copy(sheet = PresetSheet.ConfirmStarter(starter)) }
            else -> applyStarter(messages.name(starter), fitted)
        }
    }

    /** Loads [starter], named [name]; Undo puts back the setup it replaced (unless the clock has started since). */
    private fun applyStarter(name: String, starter: PresetSetup) {
        val before = setup.capture(includeChipSet = false)
        if (!setup.apply(starter)) return
        close()
        offerUndo(messages.loaded(name)) { setup.apply(before) }
    }

    /**
     * Saves [fitted] among the host's presets under [starter]'s name, or the first of "Turbo 2",
     * "Turbo 3"... that is free; Undo deletes it.
     */
    private fun copyStarter(starter: Starter, fitted: PresetSetup) {
        val saved = store.save(store.freeName(messages.name(starter)), fitted, time.wallClockMillis())
        close()
        offerUndo(messages.copied(saved.name)) { store.delete(saved.id) }
    }

    /** Adds the presets in the file at [uri], closes the sheet and offers Undo; a file that won't do says why on the list. */
    private fun import(uri: Uri) {
        _uiState.update { it.copy(fileProblem = null) }
        viewModelScope.launch {
            val result = try {
                files.import(uri)
            } catch (expected: BackupException) {
                val none = expected.problem == BackupProblem.Empty
                _uiState.update { it.copy(fileProblem = if (none) R.string.presets_file_none else expected.problem.message) }
                return@launch
            }
            close()
            if (result.total == 0) {
                snackbars.showMessage(messages.nothingNew)
            } else {
                offerUndo(messages.added(result.total)) { result.undo() }
            }
        }
    }

    /**
     * Opens [sheet] with what it shows read fresh: whether loading is allowed, the chip set, and the
     * starters fitted to tonight.
     */
    private fun show(sheet: PresetSheet) {
        val chips = setup.chipSet()
        val summary = ChipSetSummary(
            colours = chips.inventory.owned.size,
            chips = chips.inventory.totalChips,
            ready = chips.inventoryReviewed,
        )
        _uiState.update {
            it.copy(sheet = sheet, canLoad = setup.canLoad(), chipSet = summary, fileProblem = null, starters = setup.starters())
        }
    }

    private fun close() = _uiState.update { it.copy(sheet = null) }

    private fun save(name: String, includeChipSet: Boolean) {
        val clean = TournamentPreset.cleanName(name)
        if (clean.isEmpty()) return
        val replaced = store.named(clean)
        val saved = store.save(clean, setup.capture(includeChipSet), time.wallClockMillis())
        close()
        val message = if (replaced != null) messages.updated(saved.name) else messages.saved(saved.name)
        offerUndo(message) {
            if (replaced != null) store.put(replaced) else store.delete(saved.id)
        }
    }

    private fun load(id: Long, confirmed: Boolean) {
        val preset = store.get(id)
        when {
            preset == null -> close()
            !setup.canLoad() -> _uiState.update { it.copy(sheet = PresetSheet.List, canLoad = false) }
            !confirmed && setup.wouldOverwrite(preset.setup) ->
                _uiState.update { it.copy(sheet = PresetSheet.ConfirmLoad(id)) }
            else -> apply(preset)
        }
    }

    /** Loads [preset]; Undo puts back the setup it replaced (unless the clock has started since). */
    private fun apply(preset: TournamentPreset) {
        val before = setup.capture(includeChipSet = preset.setup.chipSet != null)
        if (!setup.apply(preset.setup)) return
        store.markUsed(preset.id, time.wallClockMillis())
        close()
        offerUndo(messages.loaded(preset.name)) {
            if (setup.apply(before)) store.get(preset.id)?.let { store.put(it.copy(lastUsedMillis = preset.lastUsedMillis)) }
        }
    }

    private fun rename(id: Long, name: String) {
        val before = store.get(id)
        val clean = TournamentPreset.cleanName(name)
        val taken = store.named(clean)?.let { it.id != id } ?: false
        if (before == null || clean.isEmpty() || taken) return
        store.rename(id, clean)
        close()
        offerUndo(messages.renamed(clean)) { store.get(id)?.let { store.put(it.copy(name = before.name)) } }
    }

    private fun delete(id: Long) {
        val deleted = store.get(id) ?: return
        store.delete(id)
        close()
        offerUndo(messages.deleted(deleted.name)) { store.put(deleted) }
    }

    /**
     * "[message] · UNDO" for the Undo window; [undo] runs if it is pressed. The newest action gets the
     * snackbar at once: one still up from before is dismissed (its Undo with it), not queued behind.
     */
    private fun offerUndo(message: String, undo: () -> Unit) {
        viewModelScope.launch {
            if (snackbars.showUndo(message, messages.undo)) undo()
        }
    }
}

/** The few strings the presets ViewModel shows itself: its snackbars. */
class PresetMessages @Inject constructor(@ApplicationContext private val context: Context) {
    val undo: String get() = context.getString(R.string.presets_undo)

    fun saved(name: String): String = context.getString(R.string.presets_snackbar_saved, name)

    fun updated(name: String): String = context.getString(R.string.presets_snackbar_updated, name)

    fun loaded(name: String): String = context.getString(R.string.presets_snackbar_loaded, name)

    fun renamed(name: String): String = context.getString(R.string.presets_snackbar_renamed, name)

    fun deleted(name: String): String = context.getString(R.string.presets_snackbar_deleted, name)

    fun copied(name: String): String = context.getString(R.string.presets_snackbar_copied, name)

    /** A starter's name (PP-113), as the sheet shows it and a copy is saved under. */
    fun name(starter: Starter): String = context.getString(starter.title)

    fun added(count: Int): String = context.resources.getQuantityString(R.plurals.presets_file_added, count, count)

    val nothingNew: String get() = context.getString(R.string.presets_file_nothing_new)
}
