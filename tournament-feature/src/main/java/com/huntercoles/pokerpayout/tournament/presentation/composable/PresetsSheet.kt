package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.ConfirmSheetContent
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.presets.PresetSetup
import com.huntercoles.pokerpayout.tournament.domain.presets.TournamentPreset
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetSheet
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState
import com.huntercoles.pokerpayout.tournament.presentation.presets.SetupShareText
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * "Presets ›" (PP-032): on the setup page under the ticket, and at the foot of the panel mid-game
 * ([midGame]: save and share only, and it says why). Opens the presets sheet.
 */
@Composable
internal fun PresetsRow(midGame: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, framed: Boolean = true) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (framed) Modifier.background(PokerColors.FeltGreen) else Modifier)
            .clickable(onClickLabel = stringResource(R.string.presets_open), role = Role.Button, onClick = onOpen)
            .heightIn(min = PokerDimens.RowMinHeight)
            .padding(horizontal = if (framed) 16.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PokerIcons.List, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.presets_title),
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
            )
            Text(
                text = stringResource(if (midGame) R.string.presets_row_hint_mid_game else R.string.presets_row_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.Chalk)
    }
}

/** The presets sheet when one is open, as a modal sheet over the tab. Its body is screenshot-tested. */
@OptIn(ExperimentalMaterial3Api::class) // PokerSheet's default sheet state
@Composable
internal fun PresetsSheet(
    state: PresetsUiState,
    setup: TournamentConfigUiState,
    timer: TimerUiState,
    actions: TournamentActions,
) {
    val sheet = state.sheet ?: return
    val context = LocalContext.current
    val onIntent = actions.onPresetIntent
    PokerSheet(onDismissRequest = { onIntent(PresetsIntent.Close) }, title = presetsSheetTitle(state, sheet)) {
        PresetsSheetBody(
            state = state,
            sheet = sheet,
            suggestedName = suggestedPresetName(setup, timer),
            onIntent = onIntent,
            onShare = { actions.shareText(SetupShareText.build(context, setup, timer)) },
        )
    }
}

/** "Presets", "Save as preset", "Rename preset", "Load Friday?". */
@Composable
internal fun presetsSheetTitle(state: PresetsUiState, sheet: PresetSheet): String = when (sheet) {
    PresetSheet.List -> stringResource(R.string.presets_title)
    PresetSheet.Save -> stringResource(R.string.presets_save_title)
    is PresetSheet.Rename -> stringResource(R.string.presets_rename_title)
    is PresetSheet.ConfirmLoad -> stringResource(R.string.presets_load_title, state.preset(sheet.id)?.name.orEmpty())
}

/** What the open sheet shows, under its title: the list, a form, or the load question. */
@Composable
internal fun PresetsSheetBody(
    state: PresetsUiState,
    sheet: PresetSheet,
    suggestedName: String,
    onIntent: (PresetsIntent) -> Unit,
    onShare: () -> Unit,
) {
    when (sheet) {
        PresetSheet.List -> PresetsList(state, onIntent, onShare)
        PresetSheet.Save -> PresetSaveForm(state, suggestedName, onIntent)
        is PresetSheet.Rename -> state.preset(sheet.id)?.let { PresetRenameForm(state, it, onIntent) }
        is PresetSheet.ConfirmLoad -> state.preset(sheet.id)?.let { preset ->
            ConfirmSheetContent(
                body = stringResource(
                    if (preset.setup.chipSet != null) R.string.presets_load_body_chip_set else R.string.presets_load_body
                ),
                dismissLabel = stringResource(R.string.presets_keep_mine),
                confirmLabel = stringResource(R.string.presets_load_confirm),
                onDismiss = { onIntent(PresetsIntent.Open) },
                onConfirm = { onIntent(PresetsIntent.ConfirmLoad(preset.id)) },
            )
        }
    }
}

/**
 * Save, share, and the saved presets, the last used first: a tap loads one; ⋮ renames or deletes it.
 * Once the clock has started a preset can't be loaded, and the list says why.
 */
@Composable
private fun PresetsList(state: PresetsUiState, onIntent: (PresetsIntent) -> Unit, onShare: () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        if (!state.canLoad) LockedNote()
        PokerButton(
            text = stringResource(R.string.presets_save_as),
            onClick = { onIntent(PresetsIntent.StartSave) },
            variant = PokerButtonVariant.Secondary,
            icon = PokerIcons.Plus,
            modifier = Modifier.fillMaxWidth(),
        )
        PokerButton(
            text = stringResource(R.string.presets_share),
            onClick = onShare,
            variant = PokerButtonVariant.Secondary,
            icon = PokerIcons.Share,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.presets.isEmpty()) {
            Text(stringResource(R.string.presets_none), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
        } else {
            PokerEyebrow(stringResource(R.string.presets_saved))
            Column {
                state.presets.forEachIndexed { index, preset ->
                    if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
                    PresetItem(preset, state.canLoad, onIntent)
                }
            }
        }
    }
}

/** Why a preset can't be loaded mid-game. */
@Composable
private fun LockedNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            PokerIcons.Lock,
            contentDescription = null,
            tint = PokerColors.PokerGold,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Text(stringResource(R.string.presets_locked), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Chalk)
    }
}

/** One preset: its name, what it holds, when it was last used. A tap loads it. */
@Composable
private fun PresetItem(preset: TournamentPreset, canLoad: Boolean, onIntent: (PresetsIntent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = PokerDimens.RowMinHeight)
                .clip(RoundedCornerShape(PokerDimens.CornerControl))
                .clickable(
                    enabled = canLoad,
                    onClickLabel = stringResource(R.string.presets_load),
                    role = Role.Button,
                    onClick = { onIntent(PresetsIntent.Load(preset.id)) },
                )
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (canLoad) PokerColors.CardWhite else PokerColors.Chalk,
            )
            Text(presetSummary(preset.setup), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
            Text(
                text = stringResource(R.string.presets_last_used, presetDate(preset.lastUsedMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        PresetMenu(preset, onIntent)
    }
}

/** ⋮: Rename… and Delete (at once, with Undo). Both work mid-game: neither touches the game. */
@Composable
private fun PresetMenu(preset: TournamentPreset, onIntent: (PresetsIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokerIconButton(
            icon = PokerIcons.More,
            contentDescription = stringResource(R.string.presets_more, preset.name),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PokerColors.DarkGreen) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.presets_rename_action), color = PokerColors.CardWhite) },
                onClick = {
                    open = false
                    onIntent(PresetsIntent.StartRename(preset.id))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.presets_delete_action), color = PokerColors.Danger) },
                onClick = {
                    open = false
                    onIntent(PresetsIntent.Delete(preset.id))
                },
            )
        }
    }
}

/** "$40 buy-in · 20-min levels · 5,000 chips · Standard · 3 paid · chip set". */
@Composable
private fun presetSummary(setup: PresetSetup): String {
    val formatter = rememberChipFormatter()
    val payouts = setup.payouts
    return listOfNotNull(
        stringResource(R.string.strip_buy_in, money(setup.money.buyInCents)),
        stringResource(R.string.strip_level_length, setup.blinds.roundLengthMinutes),
        stringResource(R.string.strip_chips, formatter.format(setup.blinds.startingChips)),
        payouts.preset?.label ?: stringResource(R.string.setup_payouts_custom),
        stringResource(R.string.strip_paid, payouts.weights.size),
        stringResource(R.string.presets_with_chip_set).takeIf { setup.chipSet != null },
    ).joinToString(stringResource(R.string.strip_separator))
}

/** "Oct 5, 2026", in the phone's own date style. */
@Composable
private fun presetDate(millis: Long): String {
    val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()) }
    return format.format(Date(millis))
}
