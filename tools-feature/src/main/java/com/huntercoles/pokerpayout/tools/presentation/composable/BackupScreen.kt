package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.backup.BackupJson
import com.huntercoles.pokerpayout.core.backup.BackupLine
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.BackupIntent
import com.huntercoles.pokerpayout.tools.presentation.BackupPreview
import com.huntercoles.pokerpayout.tools.presentation.BackupUiState
import com.huntercoles.pokerpayout.tools.presentation.BackupViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Backup route ("Backup" in the Tools list): [BackupContent], with the system's file picker to
 * save a backup (CreateDocument) and to open one (OpenDocument). A file opened shows what it holds
 * in a sheet ([BackupPreviewBody]) before anything changes.
 */
@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupJson.MIME_TYPE)) { uri ->
        uri?.let { viewModel.acceptIntent(BackupIntent.Save(it)) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.acceptIntent(BackupIntent.Open(it)) }
    }
    BackupContent(
        state = state,
        onBack = onBack,
        onSave = { runCatching { save.launch(fileName) } },
        onOpen = { runCatching { open.launch(OPEN_TYPES) } },
    )
    state.preview?.let { preview ->
        PokerSheet(
            onDismissRequest = { viewModel.acceptIntent(BackupIntent.ClosePreview) },
            title = stringResource(R.string.backup_preview_title),
        ) {
            BackupPreviewBody(preview, busy = state.busy, onIntent = viewModel::acceptIntent)
        }
    }
}

/**
 * What the file picker offers to open: JSON, and the types some apps give a .json file they don't
 * know. Anything that isn't a backup is turned away with a plain reason.
 */
private val OPEN_TYPES = arrayOf(BackupJson.MIME_TYPE, "text/plain", "application/octet-stream")

/**
 * Backup, stateless: why the last file couldn't be used (on top, so it is seen), save a backup, open
 * a file to restore, and where the files go (nowhere but where the player puts them).
 */
@Composable
fun BackupContent(
    state: BackupUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.backup_title),
            subtitle = stringResource(R.string.backup_subtitle),
            onBack = onBack,
        )
        HistoryPane {
            // First, so it shows without scrolling whatever the text size
            state.problem?.let { ProblemNote(stringResource(it)) }
            BackupCard(
                icon = PokerIcons.Save,
                heading = stringResource(R.string.backup_save_heading),
                body = stringResource(R.string.backup_save_body),
            ) {
                PokerButton(
                    text = stringResource(R.string.backup_save),
                    onClick = onSave,
                    icon = PokerIcons.Save,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BackupCard(
                icon = PokerIcons.FolderOpen,
                heading = stringResource(R.string.backup_open_heading),
                body = stringResource(R.string.backup_open_body),
            ) {
                PokerButton(
                    text = stringResource(R.string.backup_open),
                    onClick = onOpen,
                    variant = PokerButtonVariant.Secondary,
                    icon = PokerIcons.FolderOpen,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.backup_private),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
    }
}

/** A felt card: an icon tile, a heading, what it does, and its button. */
@Composable
private fun BackupCard(icon: ImageVector, heading: String, body: String, button: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(PokerDimens.MinTouch)
                    .background(PokerColors.DarkGreen, RoundedCornerShape(PokerDimens.CornerControl)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = PokerColors.PokerGold)
            }
            Text(
                text = heading,
                style = ToolTitle,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        button()
    }
}

/** Why the last file couldn't be saved or read. */
@Composable
private fun ProblemNote(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PokerIcons.Info, contentDescription = null, tint = PokerColors.Danger, modifier = Modifier.size(22.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * What an opened file holds, under the sheet's title: its name and when it was saved, a line per kind
 * of data, then Add to this phone (when it has presets or nights), Replace this phone's data, and
 * Cancel, each with what it does.
 */
@Composable
fun BackupPreviewBody(preview: BackupPreview, busy: Boolean, onIntent: (BackupIntent) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            preview.fileName?.let { name ->
                Text(wrappable(name), style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            }
            savedLine(preview)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk) }
        }
        PokerEyebrow(stringResource(R.string.backup_preview_holds), modifier = Modifier.semantics { heading() })
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            preview.lines.forEach { line -> HoldsLine(lineText(line)) }
        }
        if (preview.partial) {
            Text(
                text = stringResource(R.string.backup_preview_partial),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        if (preview.canMerge) {
            Choice(
                label = stringResource(R.string.backup_merge),
                help = stringResource(R.string.backup_merge_help),
                variant = PokerButtonVariant.Primary,
                enabled = !busy,
            ) { onIntent(BackupIntent.Merge) }
        }
        Choice(
            label = stringResource(R.string.backup_replace),
            help = stringResource(R.string.backup_replace_help),
            variant = PokerButtonVariant.Destructive,
            enabled = !busy,
        ) { onIntent(BackupIntent.Replace) }
        PokerButton(
            text = stringResource(R.string.backup_cancel),
            onClick = { onIntent(BackupIntent.ClosePreview) },
            variant = PokerButtonVariant.Text,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HoldsLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PokerIcons.Check, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite, modifier = Modifier.weight(1f))
    }
}

/** A choice in the preview: its button, and under it what it does. */
@Composable
private fun Choice(label: String, help: String, variant: PokerButtonVariant, enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PokerButton(text = label, onClick = onClick, variant = variant, enabled = enabled, modifier = Modifier.fillMaxWidth())
        Text(help, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
    }
}

/** "Saved Oct 8, 2026 by version 1.4.0", or as much of it as the file says. */
@Composable
private fun savedLine(preview: BackupPreview): String? {
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val day = preview.saved?.let(dates::format) ?: return null
    return preview.appVersion?.let { stringResource(R.string.backup_preview_saved_by, day, it) }
        ?: stringResource(R.string.backup_preview_saved, day)
}

/**
 * [name] with a place to wrap after each '-', '_' and '.', so at large text a long file name
 * ("poker-payout-2026-10-08.json") wraps between its parts, never inside one: on its own, a line
 * may not break before a digit after a hyphen (it reads as a minus sign).
 */
internal fun wrappable(name: String): String = buildString {
    name.forEach { char ->
        append(char)
        if (char in WRAP_AFTER) append(ZERO_WIDTH_SPACE)
    }
}

private val WRAP_AFTER = setOf('-', '_', '.')
private val ZERO_WIDTH_SPACE = Char(0x200B)

@Composable
private fun lineText(line: BackupLine): String = when (line) {
    is BackupLine.Counted -> pluralStringResource(line.plural, line.count, line.count)
    is BackupLine.Named -> stringResource(line.text)
}
