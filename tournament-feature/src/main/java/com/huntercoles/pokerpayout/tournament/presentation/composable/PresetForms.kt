package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.presets.TournamentPreset
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.presets.ChipSetSummary
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState

/**
 * "Save as preset" (PP-032): a name (it starts as the setup's buy-in and level length), whether to
 * take the chip set along (on once it is set up in Tools), and what is and isn't saved. A name a
 * preset already has saves over that preset, and says so.
 */
@Composable
internal fun PresetSaveForm(state: PresetsUiState, suggestedName: String, onIntent: (PresetsIntent) -> Unit) {
    var name by rememberSaveable { mutableStateOf(suggestedName) }
    var withChipSet by rememberSaveable { mutableStateOf(state.chipSet.ready) }
    val replacing = state.named(name) != null
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        PresetNameField(
            name = name,
            onChange = { name = it },
            help = stringResource(if (replacing) R.string.presets_replaces else R.string.presets_save_help),
            isError = false,
        )
        ChipSetToggle(state.chipSet, checked = withChipSet, onChange = { withChipSet = it })
        FormButtons(
            confirmLabel = stringResource(if (replacing) R.string.presets_replace else R.string.presets_save),
            confirmEnabled = TournamentPreset.cleanName(name).isNotEmpty(),
            onDismiss = { onIntent(PresetsIntent.Open) },
            onConfirm = {
                focusManager.clearFocus()
                onIntent(PresetsIntent.Save(name, withChipSet))
            },
        )
    }
}

/** "Rename preset": the name, refused while another preset has it. */
@Composable
internal fun PresetRenameForm(state: PresetsUiState, preset: TournamentPreset, onIntent: (PresetsIntent) -> Unit) {
    var name by rememberSaveable { mutableStateOf(preset.name) }
    val clean = TournamentPreset.cleanName(name)
    val taken = state.named(clean)?.let { it.id != preset.id } ?: false
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        PresetNameField(
            name = name,
            onChange = { name = it },
            help = if (taken) stringResource(R.string.presets_name_taken) else null,
            isError = taken,
        )
        FormButtons(
            confirmLabel = stringResource(R.string.presets_rename),
            confirmEnabled = clean.isNotEmpty() && !taken && clean != preset.name,
            onDismiss = { onIntent(PresetsIntent.Open) },
            onConfirm = {
                focusManager.clearFocus()
                onIntent(PresetsIntent.Rename(preset.id, name))
            },
        )
    }
}

/** The preset's name, typed in the body face. TalkBack and the device tour know it as "Preset name". */
@Composable
private fun PresetNameField(name: String, onChange: (String) -> Unit, help: String?, isError: Boolean) {
    val focusManager = LocalFocusManager.current
    val description = stringResource(R.string.presets_name_description)
    PokerField(
        value = name,
        onValueChange = { onChange(it.take(TournamentPreset.MAX_NAME_LENGTH)) },
        label = stringResource(R.string.presets_name),
        supportingText = help,
        isError = isError,
        keyboardType = KeyboardType.Text,
        textStyle = NameStyle,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        fieldModifier = Modifier
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
            .semantics { contentDescription = description },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Include the chip set · From Tools: 4 colours, 500 chips", as a switch. */
@Composable
private fun ChipSetToggle(chipSet: ChipSetSummary, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.presets_include_chip_set),
                style = MaterialTheme.typography.titleMedium,
                color = PokerColors.CardWhite,
            )
            Text(chipSetLine(chipSet), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PokerColors.FeltDeep,
                checkedTrackColor = PokerColors.PokerGold,
                uncheckedThumbColor = PokerColors.Chalk,
                uncheckedTrackColor = PokerColors.FeltDeep,
                uncheckedBorderColor = PokerColors.FeltEdge,
            ),
        )
    }
}

/** "From Tools: 4 colours, 500 chips", or that it isn't set up yet. */
@Composable
private fun chipSetLine(chipSet: ChipSetSummary): String {
    if (!chipSet.ready) return stringResource(R.string.presets_chip_set_not_set_up)
    val formatter = rememberChipFormatter()
    return stringResource(
        R.string.presets_chip_set_summary,
        pluralStringResource(R.plurals.presets_colours, chipSet.colours, chipSet.colours),
        pluralStringResource(R.plurals.presets_chips, chipSet.chips, formatter.format(chipSet.chips)),
    )
}

/** Cancel (back to the list) and the form's action, in one row. */
@Composable
private fun FormButtons(confirmLabel: String, confirmEnabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall), verticalAlignment = Alignment.CenterVertically) {
        PokerButton(
            text = stringResource(R.string.presets_cancel),
            onClick = onDismiss,
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
        PokerButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The name the save form starts with: "$40 · 20-min levels". */
@Composable
internal fun suggestedPresetName(setup: TournamentConfigUiState, timer: TimerUiState): String =
    stringResource(R.string.presets_suggested_name, money(setup.money.buyInCents), timer.config.roundLengthMinutes)

private val NameStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
