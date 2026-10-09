package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.backup.shareFile
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetFile
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsViewModel

/**
 * The presets sheet's intents, with the two that need the system: Open preset file… opens the file
 * picker (OpenDocument: no permission), and a preset file waiting to be shared
 * ([PresetsUiState.sharing]) goes to the share sheet as an attachment.
 */
@Composable
internal fun rememberPresetIntents(viewModel: PresetsViewModel, sharing: PresetFile?): (PresetsIntent) -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.presets_share_file_title)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.acceptIntent(PresetsIntent.ImportFile(it)) }
    }
    LaunchedEffect(sharing) {
        sharing?.let { file ->
            shareFile(context, file.name, PresetFile.MIME_TYPE, file.text, title)
            viewModel.acceptIntent(PresetsIntent.FileShared)
        }
    }
    return remember(viewModel, picker) {
        { intent ->
            if (intent == PresetsIntent.PickFile) {
                runCatching { picker.launch(PRESET_FILE_TYPES) }
            } else {
                viewModel.acceptIntent(intent)
            }
        }
    }
}

/** A preset file is JSON; some apps hand one over as plain text or as bytes of no known kind. */
private val PRESET_FILE_TYPES = arrayOf(PresetFile.MIME_TYPE, "text/plain", "application/octet-stream")
