package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.domain.model.PayoutPlaces
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.domain.model.ordinalOf
import com.huntercoles.pokerpayout.core.domain.usecase.CalculatePayoutsUseCase
import com.huntercoles.pokerpayout.core.utils.FormatUtils

private const val MAX_WEIGHT_VALUE = 999

/** Save takes a little more of the row than Cancel. */
private const val SAVE_WEIGHT = 1.4f
private const val PREVIEW_POOL_CENTS = 45_000L
private const val PREVIEW_PLAYERS = 9
private const val PREVIEW_PLACES = 3

/**
 * The payout structure editor as a bottom sheet (replaces `WeightsEditorDialog`, PP-048): presets
 * with what 1st would get, rounding, how many places to pay, and the weight of each place with the
 * amount it pays right now. The sheet is as tall as what it holds (the dialog it replaces was
 * full-height and mostly empty), and scrolls when that is taller than the screen. Opened from the
 * Bank and the Payouts tab; read-only while the clock runs ([isLocked]).
 */
@Composable
fun PayoutStructureSheet(
    current: PayoutSettings,
    preview: PayoutPreview,
    onSave: (PayoutSettings) -> Unit,
    onDismiss: () -> Unit,
    isLocked: Boolean = false
) {
    PokerSheet(onDismissRequest = onDismiss, title = stringResource(R.string.payout_structure_title)) {
        PayoutStructureContent(current, preview, onSave, onDismiss, isLocked)
    }
}

/** The editor's body, for [PayoutStructureSheet] and for screenshots ([PokerSheetContent]). */
@Composable
fun PayoutStructureContent(
    current: PayoutSettings,
    preview: PayoutPreview,
    onSave: (PayoutSettings) -> Unit,
    onDismiss: () -> Unit,
    isLocked: Boolean = false
) {
    val maxPlaces = PayoutPlaces.maxFor(preview.playerCount)
    var draft by remember(current, maxPlaces) {
        mutableStateOf(current.copy(weights = current.weights.take(maxPlaces).ifEmpty { listOf(1) }))
    }
    val invalid = detectInvalidWeights(draft.weights)
    val table = CalculatePayoutsUseCase()(preview.prizePoolCents, draft.weights, preview.playerCount, draft.rounding)
    val enabled = !isLocked
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(if (isLocked) R.string.payout_structure_locked else R.string.payout_structure_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
        PresetPicker(draft, preview, enabled) { preset -> draft = draft.copy(preset = preset).withPlaces(draft.weights.size) }
        Labelled(stringResource(R.string.payout_structure_round_to)) {
            PokerSegmentedControl(
                options = PayoutRounding.entries,
                selected = draft.rounding,
                onSelect = { draft = draft.copy(rounding = it) },
                label = { it.label },
                enabled = enabled,
            )
        }
        PlacesRow(draft.weights.size, maxPlaces, preview.playerCount, enabled && invalid.none { it }) { places ->
            draft = draft.withPlaces(places)
        }
        PokerEyebrow(stringResource(R.string.payout_structure_weights))
        draft.weights.forEachIndexed { index, weight ->
            WeightRow(index + 1, weight, table.amountFor(index + 1), invalid.getOrElse(index) { false }, enabled) { value ->
                draft = draft.copy(weights = draft.weights.toMutableList().also { it[index] = value }, preset = null)
            }
        }
        if (invalid.any { it }) {
            Text(
                stringResource(R.string.payout_structure_error),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Danger
            )
        }
        Text(
            text = stringResource(R.string.payout_structure_pool, FormatUtils.formatMoney(table.prizePoolCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
        EditorButtons(
            isLocked = isLocked,
            canSave = invalid.none { it },
            onCancel = onDismiss,
            onSave = { onSave(draft.copy(preset = draft.preset?.takeIf { it.weightsFor(draft.weights.size) == draft.weights })) },
        )
    }
}

@Composable
private fun PresetPicker(draft: PayoutSettings, preview: PayoutPreview, enabled: Boolean, onSelect: (PayoutPreset) -> Unit) {
    val places = draft.weights.size
    val labels = PayoutPreset.entries.associateWith { presetLabel(it) }
    Labelled(stringResource(R.string.payout_structure_presets)) {
        // Hand-edited weights select no preset.
        PokerSegmentedControl<PayoutPreset?>(
            options = PayoutPreset.entries,
            selected = draft.preset,
            onSelect = { preset -> preset?.let(onSelect) },
            label = { labels[it].orEmpty() },
            secondary = { preset ->
                preset?.let {
                    val calculate = CalculatePayoutsUseCase()
                    val first = calculate(preview.prizePoolCents, it.weightsFor(places), preview.playerCount, draft.rounding)
                    FormatUtils.formatMoney(first.amountFor(1))
                }
            },
            enabled = enabled,
        )
    }
}

/** A preset's name, from resources. */
@Composable
fun presetLabel(preset: PayoutPreset): String = stringResource(
    when (preset) {
        PayoutPreset.TOP_HEAVY -> R.string.payout_preset_top_heavy
        PayoutPreset.STANDARD -> R.string.payout_preset_standard
        PayoutPreset.FLAT -> R.string.payout_preset_flat
    }
)

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PokerEyebrow(label)
        content()
    }
}

@Composable
private fun PlacesRow(places: Int, maxPlaces: Int, playerCount: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val label = stringResource(R.string.payout_structure_places)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite, modifier = Modifier.weight(1f))
            PokerStepper(
                value = places,
                onValueChange = onChange,
                range = if (enabled) 1..maxPlaces else places..places,
                label = label,
            )
        }
        Text(
            text = pluralStringResource(
                R.plurals.payout_structure_places_hint,
                PayoutPlaces.recommended(playerCount),
                playerCount,
                PayoutPlaces.recommended(playerCount),
                maxPlaces
            ),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Chalk,
        )
    }
}

/**
 * One place: its amount and its weight. A place that pays more than the one above it is marked with
 * a warning sign and a red amount, and TalkBack reads the error, so it isn't told by colour alone.
 */
@Suppress("LongParameterList") // one row: place, weight, amount, error, lock, change
@Composable
private fun WeightRow(place: Int, weight: Int, amountCents: Long, isError: Boolean, enabled: Boolean, onChange: (Int) -> Unit) {
    val errorText = stringResource(R.string.payout_structure_error)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = ordinalOf(place),
            style = PokerType.NumberM,
            color = if (place == 1) PokerColors.PokerGold else PokerColors.CardWhite,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isError) {
                Icon(PokerIcons.Info, contentDescription = null, tint = PokerColors.Danger, modifier = Modifier.size(18.dp))
            }
            Text(
                text = FormatUtils.formatMoney(amountCents),
                style = PokerType.NumberM,
                color = if (isError) PokerColors.Danger else PokerColors.CardWhite,
                textAlign = TextAlign.End,
                modifier = Modifier.semantics { if (isError) error(errorText) },
            )
        }
        PokerStepper(
            value = weight,
            onValueChange = onChange,
            range = if (enabled) 1..MAX_WEIGHT_VALUE else weight..weight,
            label = stringResource(R.string.payout_structure_weight, ordinalOf(place)),
        )
    }
}

@Composable
private fun EditorButtons(isLocked: Boolean, canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isLocked) {
            PokerButton(
                text = stringResource(R.string.payout_structure_close),
                onClick = onCancel,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                modifier = Modifier.weight(1f),
            )
        } else {
            PokerButton(
                text = stringResource(R.string.payout_structure_cancel),
                onClick = onCancel,
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
                modifier = Modifier.weight(1f),
            )
            PokerButton(
                text = stringResource(R.string.payout_structure_save),
                onClick = onSave,
                enabled = canSave,
                size = PokerButtonSize.Small,
                modifier = Modifier.weight(SAVE_WEIGHT),
            )
        }
    }
}

@Preview(name = "PayoutStructureSheet", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PayoutStructureSheetPreview() {
    PokerPreviewPage(gutter = false) {
        PokerSheetContent(title = stringResource(R.string.payout_structure_title)) {
            PayoutStructureContent(
                current = PayoutSettings(
                    PayoutPreset.STANDARD.weightsFor(PREVIEW_PLACES),
                    PayoutPreset.STANDARD,
                    PayoutRounding.FIVE_DOLLARS,
                ),
                preview = PayoutPreview(prizePoolCents = PREVIEW_POOL_CENTS, playerCount = PREVIEW_PLAYERS),
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
