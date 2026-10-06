package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.preferences.ChipSetSettings
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetIntent
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import kotlinx.coroutines.delay

/** A typed starting stack counts this long after the last key, or at once when the field loses focus. */
private const val STACK_COMMIT_DELAY_MS = 800L
private const val MAX_STACK_DIGITS = 9
private const val HALF_TURN = 180f

/**
 * "Stack settings": the old calculator's advanced settings, folded away by default. A starting
 * stack of your own (the Tournament's is the default), stacks to keep back for rebuys and add-ons,
 * the most colours a stack uses, and the stack shape (the old "distribution curve", in words).
 */
@Composable
internal fun SettingsCard(
    state: ChipSetUiState,
    onIntent: (ChipSetIntent) -> Unit,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
) {
    val settings = state.settings
    val shapes = stringArrayResource(R.array.chip_set_shape_names)
    val curves = ChipDistributionCurve.getAllCurves()
    ChipSetSection {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.MinTouch)
                .clip(RoundedCornerShape(PokerDimens.CornerControl))
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) R.string.chip_set_settings_hide else R.string.chip_set_settings_show
                    ),
                    role = Role.Button,
                    onClick = { onExpand(!expanded) },
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PokerEyebrow(stringResource(R.string.chip_set_settings), color = PokerColors.PokerGold)
                Text(
                    text = stringResource(
                        R.string.chip_set_settings_summary,
                        settings.reserveStacks,
                        settings.maxColours,
                        shapes[curves.indexOf(settings.shape).coerceAtLeast(0)].lowercase(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            }
            Icon(
                imageVector = PokerIcons.ChevronDown,
                contentDescription = null,
                tint = PokerColors.PokerGold,
                modifier = Modifier.rotate(if (expanded) HALF_TURN else 0f),
            )
        }
        if (expanded) {
            StackField(state, onIntent)
            CountSetting(
                label = stringResource(R.string.chip_set_reserve),
                value = settings.reserveStacks,
                range = ChipSetSettings.RESERVE_RANGE,
                onChange = { onIntent(ChipSetIntent.SetReserve(it)) },
            )
            CountSetting(
                label = stringResource(R.string.chip_set_max_colours),
                value = settings.maxColours,
                range = ChipSetSettings.MAX_COLOURS_RANGE,
                onChange = { onIntent(ChipSetIntent.SetMaxColours(it)) },
            )
            ShapeOptions(settings.shape, onIntent)
        }
    }
}

/** The starting stack to plan: the Tournament's, or one you type. */
@Composable
private fun StackField(state: ChipSetUiState, onIntent: (ChipSetIntent) -> Unit) {
    var text by remember(state.startingStack) { mutableStateOf(state.startingStack.toString()) }
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val current by rememberUpdatedState(state.startingStack)
    val commit = {
        text.toIntOrNull()?.takeIf { it > 0 && it != current }?.let { onIntent(ChipSetIntent.SetStackOverride(it)) }
    }
    LaunchedEffect(text) {
        delay(STACK_COMMIT_DELAY_MS)
        commit()
    }
    LaunchedEffect(focused) { if (!focused) commit() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PokerField(
            value = text,
            onValueChange = { typed -> text = typed.filter(Char::isDigit).take(MAX_STACK_DIGITS) },
            label = stringResource(R.string.chip_set_starting_stack),
            supportingText = if (state.stackFromTournament) {
                stringResource(R.string.chip_set_starting_stack_tournament)
            } else {
                stringResource(R.string.chip_set_starting_stack_own, chipNumber(state.tournamentStack))
            },
            interactionSource = interactions,
        )
        if (!state.stackFromTournament) {
            PokerButton(
                text = stringResource(R.string.chip_set_use_tournament_stack),
                onClick = { onIntent(ChipSetIntent.SetStackOverride(null)) },
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
            )
        }
    }
}

/** A labelled stepper; the stepper moves under the label when they don't fit side by side. */
@Composable
private fun CountSetting(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SideBySideOrStacked(
        modifier = Modifier.fillMaxWidth(),
        first = { Text(label, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite) },
        second = { PokerStepper(value = value, onValueChange = onChange, range = range, label = label) },
    )
}

/** The stack shape, one radio row each, in words rather than curve names. */
@Composable
private fun ShapeOptions(selected: ChipDistributionCurve, onIntent: (ChipSetIntent) -> Unit) {
    val names = stringArrayResource(R.array.chip_set_shape_names)
    val descriptions = stringArrayResource(R.array.chip_set_shape_descriptions)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.chip_set_shape), style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
        ChipDistributionCurve.getAllCurves().forEachIndexed { i, curve ->
            val isSelected = curve == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PokerDimens.MinTouch)
                    .clip(RoundedCornerShape(PokerDimens.CornerControl))
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onIntent(ChipSetIntent.SetShape(curve)) },
                    )
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioDot(isSelected)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = names[i],
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) PokerColors.PokerGold else PokerColors.CardWhite,
                    )
                    Text(descriptions[i], style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
                }
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(2.dp, if (selected) PokerColors.PokerGold else PokerColors.FeltEdge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).background(PokerColors.PokerGold, CircleShape))
    }
}
