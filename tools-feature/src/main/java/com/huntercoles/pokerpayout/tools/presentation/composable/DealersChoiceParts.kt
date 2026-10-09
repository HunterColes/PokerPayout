package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerField
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerPill
import com.huntercoles.pokerpayout.core.design.components.PokerPillTone
import com.huntercoles.pokerpayout.core.design.components.ToggleChip
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.dealers.BuiltInGame
import com.huntercoles.pokerpayout.tools.dealers.GameChoice
import com.huntercoles.pokerpayout.tools.dealers.GameFamily
import com.huntercoles.pokerpayout.tools.dealers.GameSplit
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceIntent
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceUiState
import com.huntercoles.pokerpayout.tools.presentation.DealersChoiceViewModel

/**
 * What the wheel picked: "NEXT GAME", the game's name, what kind of game it is and its rules (a
 * house game: play it your way). While the wheel turns it says so, and before the first spin it
 * says what to do. TalkBack hears the name when the wheel stops.
 */
@Composable
internal fun GameResultCard(state: DealersChoiceUiState, revealed: Boolean) {
    val pick = state.pick
    ChipSetSection {
        if (pick == null) {
            Text(
                text = stringResource(R.string.dealers_first_spin),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.Chalk,
            )
            return@ChipSetSection
        }
        PokerEyebrow(stringResource(R.string.dealers_next_game), color = PokerColors.PokerGold)
        val name = if (revealed) gameName(pick) else stringResource(R.string.dealers_spinning)
        val spoken = if (revealed) stringResource(R.string.dealers_result_spoken, name) else name
        Text(
            text = name,
            style = PokerType.Title,
            color = PokerColors.CardWhite,
            modifier = Modifier.semantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (revealed) {
            GameRules(pick)
            if (!pick.onWheel) NoteLineText(stringResource(R.string.dealers_off_wheel))
        }
    }
}

/** A game's pills (what kind of game, who takes the pot) and its rules, one line each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameRules(choice: GameChoice) {
    val game = choice.game
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (game == null) {
            PokerPill(stringResource(R.string.dealers_house_game), tone = PokerPillTone.Outline)
        } else {
            PokerPill(stringResource(familyText(game.family)), tone = PokerPillTone.Outline)
            PokerPill(stringResource(splitText(game.split)), tone = PokerPillTone.Outline)
        }
    }
    val lines = if (game == null) listOf(stringResource(R.string.dealers_house_rules)) else gameRules(game)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).background(PokerColors.PokerGold, CircleShape))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun familyText(family: GameFamily): Int = when (family) {
    GameFamily.Flop -> R.string.dealers_family_flop
    GameFamily.Stud -> R.string.dealers_family_stud
    GameFamily.Draw -> R.string.dealers_family_draw
}

private fun splitText(split: GameSplit): Int = when (split) {
    GameSplit.High -> R.string.dealers_split_high
    GameSplit.Low -> R.string.dealers_split_low
    GameSplit.HiLo -> R.string.dealers_split_hilo
    GameSplit.Split -> R.string.dealers_split_split
}

/**
 * The games on the wheel: a chip per game of the app's, on or off; the house games, each with a
 * button to remove it; a field to add one; and the way to every game's rules.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GamesSection(state: DealersChoiceUiState, onIntent: (DealersChoiceIntent) -> Unit, onShowRules: () -> Unit) {
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.dealers_games),
            note = stringResource(R.string.dealers_games_count, state.wheel.size, state.games.size),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.games.filter { it.game != null }.forEach { choice ->
                ToggleChip(
                    label = gameName(choice),
                    checked = choice.onWheel,
                    onCheckedChange = { onIntent(DealersChoiceIntent.SetOnWheel(choice.id, it)) },
                )
            }
        }
        HorizontalDivider(color = PokerColors.FeltLine)
        PokerEyebrow(stringResource(R.string.dealers_house_games))
        state.houseGames.forEach { choice -> HouseGameRow(choice, onIntent) }
        AddHouseGame(state, onIntent)
        PokerButton(
            text = stringResource(R.string.dealers_all_rules),
            onClick = onShowRules,
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
            icon = PokerIcons.List,
        )
    }
}

/** A house game: its chip (on the wheel or not) and a button to remove it. */
@Composable
private fun HouseGameRow(choice: GameChoice, onIntent: (DealersChoiceIntent) -> Unit) {
    val name = choice.houseName.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleChip(
            label = name,
            checked = choice.onWheel,
            onCheckedChange = { onIntent(DealersChoiceIntent.SetOnWheel(choice.id, it)) },
            modifier = Modifier.weight(1f, fill = false),
        )
        PokerIconButton(
            icon = PokerIcons.Close,
            contentDescription = stringResource(R.string.dealers_remove, name),
            onClick = { onIntent(DealersChoiceIntent.RemoveHouseGame(choice.id)) },
        )
    }
}

/** A name field and Add; or, with the wheel full of house games, why there's no room. */
@Composable
private fun AddHouseGame(state: DealersChoiceUiState, onIntent: (DealersChoiceIntent) -> Unit) {
    if (!state.canAddHouseGame) {
        NoteLineText(stringResource(R.string.dealers_house_full))
        return
    }
    var draft by rememberSaveable { mutableStateOf("") }
    val label = stringResource(R.string.dealers_add_label)
    val add = {
        onIntent(DealersChoiceIntent.AddHouseGame(draft))
        draft = ""
    }
    SideBySideOrStacked(
        modifier = Modifier.fillMaxWidth(),
        first = {
            PokerField(
                value = draft,
                onValueChange = { draft = it.take(DealersChoiceViewModel.MAX_NAME_LENGTH) },
                label = label,
                keyboardType = KeyboardType.Text,
                fieldModifier = Modifier.semantics { contentDescription = label },
                keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) add() }),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        second = {
            PokerButton(
                text = stringResource(R.string.dealers_add),
                onClick = add,
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                icon = PokerIcons.Plus,
                enabled = draft.isNotBlank(),
                modifier = Modifier.padding(top = 20.dp),
            )
        },
    )
}

/** Every game's rules, in the sheet: a heading per game, its pills and its lines. */
@Composable
internal fun AllGameRules(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BuiltInGame.entries.forEach { game ->
            val choice = GameChoice.builtIn(game, onWheel = true)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = gameName(choice),
                    style = RulesHeading,
                    color = PokerColors.PokerGold,
                    modifier = Modifier.semantics { heading() },
                )
                GameRules(choice)
            }
        }
    }
}

private val RulesHeading = PokerType.Title.copy(fontSize = 20.sp, lineHeight = 24.sp)

/** A short Chalk note. */
@Composable
internal fun NoteLineText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
}
