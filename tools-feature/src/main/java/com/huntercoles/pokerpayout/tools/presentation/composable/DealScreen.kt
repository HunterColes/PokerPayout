package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.FormatUtils
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.DealIntent
import com.huntercoles.pokerpayout.tools.presentation.DealPlayer
import com.huntercoles.pokerpayout.tools.presentation.DealUiState
import com.huntercoles.pokerpayout.tools.presentation.DealViewModel

/** The deal maker route ("Deal maker" in the Tools list). */
@Composable
fun DealRoute(onBack: () -> Unit, viewModel: DealViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DealContent(state = state, onIntent = viewModel::acceptIntent, onBack = onBack)
}

/**
 * Deal maker (S22): the players left and their chips, the prizes left (tonight's payouts, or typed),
 * an optional amount saved for the winner, then the deal by ICM and by chips side by side, each
 * adding up to the money shared to the cent. Start over (↺) reads tonight again, with Undo.
 */
@Composable
fun DealContent(
    state: DealUiState,
    onIntent: (DealIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = stringResource(
        R.string.deal_subtitle,
        pluralStringResource(R.plurals.table_tools_players, state.players.size, state.players.size),
        FormatUtils.formatMoney(state.deal?.splitCents ?: state.prizes.sum()),
    )
    TableToolPage(
        title = stringResource(R.string.deal_title),
        subtitle = subtitle,
        onBack = onBack,
        modifier = modifier,
        actions = {
            PokerIconButton(
                icon = PokerIcons.Restart,
                contentDescription = stringResource(R.string.deal_start_over),
                onClick = { onIntent(DealIntent.StartOver) },
                tint = PokerColors.PokerGold,
            )
        },
        inputs = {
            DealPlayersCard(state, onIntent)
            PrizesCard(state, onIntent)
        },
        results = { DealCard(state) },
    )
}

/** The players left: from the Bank, or typed; a well per player (name, chips), then Add player. */
@Composable
private fun DealPlayersCard(state: DealUiState, onIntent: (DealIntent) -> Unit) {
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.deal_players_title),
            note = stringResource(if (state.fromBank) R.string.deal_players_from_bank else R.string.deal_players_typed),
        )
        state.players.forEachIndexed { index, player ->
            DealPlayerWell(index, player, canRemove = state.canRemove, onIntent = onIntent)
        }
        if (state.canAdd) {
            PokerButton(
                text = stringResource(R.string.table_tools_add_player),
                onClick = { onIntent(DealIntent.AddPlayer) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Plus,
            )
        }
    }
}

/** One player: the name and a remove button on top, the chips under them. */
@Composable
private fun DealPlayerWell(index: Int, player: DealPlayer, canRemove: Boolean, onIntent: (DealIntent) -> Unit) {
    val name = playerName(index, player.name)
    ToolWell {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerNameField(
                number = index + 1,
                value = player.name,
                onChange = { onIntent(DealIntent.SetName(index, it)) },
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                PokerIconButton(
                    icon = PokerIcons.Close,
                    contentDescription = stringResource(R.string.table_tools_remove_player, name),
                    onClick = { onIntent(DealIntent.RemovePlayer(index)) },
                )
            }
        }
        ChipsField(
            value = player.chips,
            onChange = { onIntent(DealIntent.SetChips(index, it)) },
            placeholder = stringResource(R.string.deal_chips_hint),
            description = stringResource(R.string.deal_chips_description, name),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
