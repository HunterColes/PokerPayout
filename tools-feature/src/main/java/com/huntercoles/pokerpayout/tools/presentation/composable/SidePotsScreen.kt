package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.ToggleChip
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.PotPlayer
import com.huntercoles.pokerpayout.tools.presentation.SidePotsIntent
import com.huntercoles.pokerpayout.tools.presentation.SidePotsUiState
import com.huntercoles.pokerpayout.tools.presentation.SidePotsViewModel

/** The side pots route ("Side pots" in the Tools list). */
@Composable
fun SidePotsRoute(onBack: () -> Unit, viewModel: SidePotsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SidePotsContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/**
 * Side pots (S21): what each player put in (and who folded), then the main pot and each side pot
 * with who can win it, worked out as you type. New hand (the top bar's ↺) clears the chips and
 * keeps the players, with Undo.
 */
@Composable
fun SidePotsContent(
    state: SidePotsUiState,
    onIntent: (SidePotsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = stringResource(
        R.string.side_pots_subtitle,
        pluralStringResource(R.plurals.table_tools_players, state.players.size, state.players.size),
        chips(state.totalChips),
    )
    TableToolPage(
        title = stringResource(R.string.side_pots_title),
        subtitle = subtitle,
        onBack = onBack,
        modifier = modifier,
        actions = {
            PokerIconButton(
                icon = PokerIcons.Restart,
                contentDescription = stringResource(R.string.side_pots_new_hand),
                onClick = { onIntent(SidePotsIntent.NewHand) },
                tint = PokerColors.PokerGold,
                enabled = state.players.any { it.chips != null || it.folded },
            )
        },
        inputs = { PotPlayersCard(state, onIntent) },
        results = { PotsCard(state) },
    )
}

/** Who put in what: a well per player (name, chips, folded), then Add player. */
@Composable
private fun PotPlayersCard(state: SidePotsUiState, onIntent: (SidePotsIntent) -> Unit) {
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.side_pots_players_title),
            note = stringResource(R.string.side_pots_players_note),
        )
        state.players.forEachIndexed { index, player ->
            PotPlayerWell(index, player, canRemove = state.canRemove, onIntent = onIntent)
        }
        if (state.canAdd) {
            PokerButton(
                text = stringResource(R.string.table_tools_add_player),
                onClick = { onIntent(SidePotsIntent.AddPlayer) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Plus,
            )
        }
    }
}

/**
 * One player: the name and a remove button on top, the chips and Folded under them. Folded goes
 * under the chips when they don't fit side by side (small phones, large text).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PotPlayerWell(index: Int, player: PotPlayer, canRemove: Boolean, onIntent: (SidePotsIntent) -> Unit) {
    val name = playerName(index, player.name)
    ToolWell {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerNameField(
                number = index + 1,
                value = player.name,
                onChange = { onIntent(SidePotsIntent.SetName(index, it)) },
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                PokerIconButton(
                    icon = PokerIcons.Close,
                    contentDescription = stringResource(R.string.table_tools_remove_player, name),
                    onClick = { onIntent(SidePotsIntent.RemovePlayer(index)) },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipsField(
                value = player.chips,
                onChange = { onIntent(SidePotsIntent.SetChips(index, it)) },
                placeholder = stringResource(R.string.side_pots_chips_hint),
                description = stringResource(R.string.side_pots_chips_description, name),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = ChipsFieldMinWidth)
                    .align(Alignment.CenterVertically),
            )
            ToggleChip(
                label = stringResource(R.string.side_pots_folded),
                checked = player.folded,
                onCheckedChange = { onIntent(SidePotsIntent.SetFolded(index, it)) },
                icon = PokerIcons.Cards,
                spokenLabel = stringResource(R.string.side_pots_folded_description, name),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** Room for a five-figure stack at the largest text before Folded moves under it. */
private val ChipsFieldMinWidth = 132.dp
