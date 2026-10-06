package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetIntent
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import com.huntercoles.pokerpayout.tools.presentation.ColourEditor

private const val MAX_VALUE_DIGITS = 7
private const val MAX_COUNT_DIGITS = 4
private val ChoiceChip = 36.dp

/** "Add a colour", or "Edit Green 25". */
@Composable
internal fun colourSheetTitle(state: ChipSetUiState, editor: ColourEditor): String {
    val editing = editor.editing?.let { state.inventory[it] }
    return if (editing == null) {
        stringResource(R.string.chip_set_add_title)
    } else {
        stringResource(R.string.chip_set_edit_title, colourName(editing.colour), chipNumber(editing.value))
    }
}

/**
 * The colour sheet: pick the colour, what each chip is worth (a standard set's value to start
 * with) and how many you own. Two colours can't share a value. Editing also offers Remove (with
 * Undo on the snackbar).
 */
@Composable
fun ColourEditorContent(state: ChipSetUiState, editor: ColourEditor, onIntent: (ChipSetIntent) -> Unit) {
    val editing = editor.editing?.let { state.inventory[it] }
    val choices = ChipColour.entries.filter { it == editor.editing || state.inventory[it] == null }
    var colour by rememberSaveable { mutableStateOf(editing?.colour ?: choices.firstOrNull() ?: ChipColour.White) }
    var valueText by rememberSaveable { mutableStateOf((editing?.value ?: colour.standardValue).toString()) }
    var valueTyped by rememberSaveable { mutableStateOf(editing != null) }
    var countText by rememberSaveable { mutableStateOf(editing?.count?.toString().orEmpty()) }

    val value = valueText.toIntOrNull()?.takeIf { it in 1..ChipInventory.MAX_VALUE }
    val count = countText.toIntOrNull()?.takeIf { it in 0..ChipInventory.MAX_COUNT }
    val valueError = valueError(value, state.inventory.chips.firstOrNull { it.colour != editor.editing && it.value == value })
    val countError = stringResource(R.string.chip_set_count_invalid).takeIf { count == null && countText.isNotEmpty() }

    // Scrolls on a phone on its side, and above the keyboard
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ColourChoices(choices, colour, value ?: colour.standardValue) { picked ->
            colour = picked
            if (!valueTyped) valueText = picked.standardValue.toString()
        }
        PokerField(
            value = valueText,
            onValueChange = { typed ->
                valueText = typed.filter(Char::isDigit).take(MAX_VALUE_DIGITS)
                valueTyped = true
            },
            label = stringResource(R.string.chip_set_value),
            isError = valueError != null,
            supportingText = valueError,
        )
        PokerField(
            value = countText,
            onValueChange = { typed -> countText = typed.filter(Char::isDigit).take(MAX_COUNT_DIGITS) },
            label = stringResource(R.string.chip_set_count),
            isError = countError != null,
            supportingText = countError,
        )
        val chip = if (value != null && count != null && valueError == null) InventoryChip(colour, value, count) else null
        SheetButtons(editing, chip, onIntent)
    }
}

/** Why [value] can't be a chip's value (none, or [taken] already has it), or null when it can. */
@Composable
private fun valueError(value: Int?, taken: InventoryChip?): String? = when {
    value == null -> stringResource(R.string.chip_set_value_invalid)
    taken != null -> stringResource(R.string.chip_set_value_taken, colourName(taken.colour), chipNumber(taken.value))
    else -> null
}

/** Remove (when editing), then Cancel and Save / Add colour; Save waits for a valid [chip]. */
@Composable
private fun SheetButtons(editing: InventoryChip?, chip: InventoryChip?, onIntent: (ChipSetIntent) -> Unit) {
    if (editing != null) {
        PokerButton(
            text = stringResource(R.string.chip_set_remove, colourName(editing.colour), chipNumber(editing.value)),
            onClick = { onIntent(ChipSetIntent.RemoveColour(editing.colour)) },
            variant = PokerButtonVariant.DestructiveOutline,
            size = PokerButtonSize.Small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall), verticalAlignment = Alignment.CenterVertically) {
        PokerButton(
            text = stringResource(R.string.chip_set_cancel),
            onClick = { onIntent(ChipSetIntent.CloseEditor) },
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
        PokerButton(
            text = stringResource(if (editing == null) R.string.chip_set_add else R.string.chip_set_save),
            onClick = { chip?.let { onIntent(ChipSetIntent.SaveColour(it, editing?.colour)) } },
            enabled = chip != null,
            size = PokerButtonSize.Small,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The colours there are to pick from, each drawn as a chip worth [value]; radio buttons for TalkBack. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourChoices(choices: List<ChipColour>, selected: ChipColour, value: Int, onPick: (ChipColour) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.chip_set_colour), style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        FlowRow(Modifier.selectableGroup()) {
            choices.forEach { choice ->
                val name = colourName(choice)
                Box(
                    modifier = Modifier
                        .size(PokerDimens.MinTouch)
                        .clip(CircleShape)
                        .selectable(selected = choice == selected, role = Role.RadioButton, onClick = { onPick(choice) })
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    PokerChip(
                        denomination = value,
                        size = ChoiceChip,
                        selected = choice == selected,
                        color = choice.paint(),
                        contentDescription = "",
                    )
                }
            }
        }
    }
}
