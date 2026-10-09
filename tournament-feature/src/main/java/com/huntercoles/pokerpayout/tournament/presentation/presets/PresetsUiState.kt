package com.huntercoles.pokerpayout.tournament.presentation.presets

import android.net.Uri
import androidx.annotation.StringRes
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetFile
import com.huntercoles.pokerpayout.tournament.domain.presets.TournamentPreset

/** What the presets sheet can be asked to do (PP-032). */
sealed interface PresetsIntent {
    /** The list: load, save, rename, delete, share. Also "back" from a form or the load question. */
    data object Open : PresetsIntent

    data object Close : PresetsIntent

    /** The save form. */
    data object StartSave : PresetsIntent

    /** Saves the setup now as [name] (in place of a preset already called that), with the chip set if asked. */
    data class Save(val name: String, val includeChipSet: Boolean) : PresetsIntent

    /** Loads a preset; asks first when it would replace what the host set. */
    data class Load(val id: Long) : PresetsIntent

    /** "Load" in the question. */
    data class ConfirmLoad(val id: Long) : PresetsIntent

    data class StartRename(val id: Long) : PresetsIntent

    data class Rename(val id: Long, val name: String) : PresetsIntent

    /** At once, with Undo. */
    data class Delete(val id: Long) : PresetsIntent

    /** Share the preset as a small file: [PresetsUiState.sharing] until the route hands it to the share sheet. */
    data class ShareFile(val id: Long) : PresetsIntent

    /** The route handed [PresetsUiState.sharing] to the share sheet. */
    data object FileShared : PresetsIntent

    /** Pick a preset file to open (the route opens the system's file picker). */
    data object PickFile : PresetsIntent

    /** The file picker chose [uri]: add the presets in it. */
    data class ImportFile(val uri: Uri) : PresetsIntent
}

/** Which presets sheet is open. */
sealed interface PresetSheet {
    data object List : PresetSheet

    data object Save : PresetSheet

    data class Rename(val id: Long) : PresetSheet

    /** "Load Friday?": loading it would replace what the host set. */
    data class ConfirmLoad(val id: Long) : PresetSheet
}

/** The chip set from Tools, as the save form offers it: [ready] once the host has checked it. */
data class ChipSetSummary(val colours: Int = 0, val chips: Int = 0, val ready: Boolean = false)

/**
 * The presets sheet (PP-032).
 *
 * @property canLoad false from the clock's first start until a reset: a preset would replace the
 *   blinds of the game being played, so the list says why and its presets can't be loaded.
 * @property chipSet offered by the save form, on by default once [ChipSetSummary.ready].
 * @property fileProblem why the preset file just opened couldn't be used, shown on the list.
 * @property sharing a preset file waiting for the share sheet.
 */
data class PresetsUiState(
    val presets: List<TournamentPreset> = emptyList(),
    val sheet: PresetSheet? = null,
    val canLoad: Boolean = true,
    val chipSet: ChipSetSummary = ChipSetSummary(),
    @StringRes val fileProblem: Int? = null,
    val sharing: PresetFile? = null,
) {
    fun preset(id: Long): TournamentPreset? = presets.firstOrNull { it.id == id }

    /** The preset already called [name] (as it would be saved), or null. */
    fun named(name: String): TournamentPreset? {
        val clean = TournamentPreset.cleanName(name)
        return presets.firstOrNull { it.name.equals(clean, ignoreCase = true) }
    }
}
