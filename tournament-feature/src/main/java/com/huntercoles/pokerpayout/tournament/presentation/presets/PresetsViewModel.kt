package com.huntercoles.pokerpayout.tournament.presentation.presets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huntercoles.pokerpayout.core.design.components.SnackbarController
import com.huntercoles.pokerpayout.core.time.TimeSource
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.presets.CurrentSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetStore
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
 */
@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val store: PresetStore,
    private val setup: CurrentSetup,
    private val snackbars: SnackbarController,
    private val messages: PresetMessages,
    private val time: TimeSource,
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
        }
    }

    /** Opens [sheet] with what it shows read fresh: whether loading is allowed, and the chip set. */
    private fun show(sheet: PresetSheet) {
        val chips = setup.chipSet()
        val summary = ChipSetSummary(
            colours = chips.inventory.owned.size,
            chips = chips.inventory.totalChips,
            ready = chips.inventoryReviewed,
        )
        _uiState.update { it.copy(sheet = sheet, canLoad = setup.canLoad(), chipSet = summary) }
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
        snackbars.hostState.currentSnackbarData?.dismiss()
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
}
