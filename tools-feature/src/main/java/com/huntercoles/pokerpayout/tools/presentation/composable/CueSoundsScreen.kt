package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.audio.packs.CueEvent
import com.huntercoles.pokerpayout.core.audio.packs.SoundPack
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsIntent
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsUiState
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsViewModel

/** The Cue sounds route (Tools > Sound > Cue sounds). */
@Composable
fun CueSoundsRoute(onBack: () -> Unit, viewModel: CueSoundsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CueSoundsContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/**
 * Cue sounds (S18), stateless: each sound pack with a sound per moment (a new level, a minute left,
 * a break starting and ending, the game over, and the night's big moments: the bubble, the final
 * table, heads-up and the champion, PP-111), each one played on a tap, and the pack the clock plays
 * picked. An empty slot says so. With the sound off, it says the clock plays none of them.
 */
@Composable
fun CueSoundsContent(
    state: CueSoundsUiState,
    onIntent: (CueSoundsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(
            title = stringResource(R.string.cue_sounds_title),
            subtitle = stringResource(R.string.cue_sounds_subtitle),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 4.dp, bottom = PokerDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            if (!state.soundOn) {
                Text(
                    text = stringResource(R.string.cue_sounds_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite,
                )
            }
            state.packs.forEach { pack ->
                PackCard(
                    pack = pack,
                    picked = pack.id == state.picked.id,
                    onPick = { onIntent(CueSoundsIntent.Pick(pack.id)) },
                    onPreview = { event -> onIntent(CueSoundsIntent.Preview(pack.id, event)) },
                )
            }
            Text(
                text = stringResource(R.string.cue_sounds_more),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
    }
}

/** A pack: its name and what it plays, picked with the radio at the top; then each moment and its sound. */
@Composable
private fun PackCard(pack: SoundPack, picked: Boolean, onPick: () -> Unit, onPreview: (CueEvent) -> Unit) {
    val shape = RoundedCornerShape(PokerDimens.CornerCard)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PokerColors.FeltGreen)
            .then(if (picked) Modifier.border(1.dp, PokerColors.DarkGold, shape) else Modifier)
            .padding(PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.MinTouch)
                .selectable(selected = picked, role = Role.RadioButton, onClick = onPick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            RadioButton(
                selected = picked,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = PokerColors.PokerGold, unselectedColor = PokerColors.Chalk),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(pack.name),
                    style = ToolTitle,
                    color = if (picked) PokerColors.PokerGold else PokerColors.CardWhite,
                )
                Text(
                    text = stringResource(pack.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            }
        }
        HorizontalDivider(color = PokerColors.FeltLine)
        val packName = stringResource(pack.name)
        CueEvent.entries.forEach { event ->
            SlotRow(event = event, hasSound = pack.soundFor(event) != null, packName = packName, onPreview = { onPreview(event) })
        }
    }
}

/** One moment: its name, and a button to hear its sound, or "No sound" for an empty slot. */
@Composable
private fun SlotRow(event: CueEvent, hasSound: Boolean, packName: String, onPreview: () -> Unit) {
    val name = stringResource(eventName(event))
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = PokerDimens.MinTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = PokerColors.CardWhite,
            modifier = Modifier.weight(1f),
        )
        if (hasSound) {
            PokerIconButton(
                icon = PokerIcons.Play,
                contentDescription = stringResource(R.string.cue_sounds_preview, name, packName),
                onClick = onPreview,
                tint = PokerColors.PokerGold,
            )
        } else {
            Text(
                text = stringResource(R.string.cue_sounds_empty_slot),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                modifier = Modifier.padding(end = PokerDimens.SpacingMedium),
            )
        }
    }
}

@StringRes
private fun eventName(event: CueEvent): Int = when (event) {
    CueEvent.LEVEL_UP -> R.string.cue_event_level_up
    CueEvent.ONE_MINUTE -> R.string.cue_event_one_minute
    CueEvent.BREAK_START -> R.string.cue_event_break_start
    CueEvent.BREAK_END -> R.string.cue_event_break_end
    CueEvent.GAME_OVER -> R.string.cue_event_game_over
    CueEvent.BIG_MOMENT -> R.string.cue_event_big_moment
    CueEvent.CHAMPION -> R.string.cue_event_champion
}
