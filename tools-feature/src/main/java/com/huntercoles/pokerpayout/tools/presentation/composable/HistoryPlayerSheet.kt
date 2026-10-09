package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerSheet
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.HistoryIntent
import com.huntercoles.pokerpayout.tools.presentation.MergeCandidate
import com.huntercoles.pokerpayout.tools.presentation.PlayerPanel

/** A player from the standings, as a modal bottom sheet titled with their name. */
@Composable
internal fun PlayerSheet(panel: PlayerPanel, onIntent: (HistoryIntent) -> Unit) {
    PokerSheet(onDismissRequest = { onIntent(HistoryIntent.ClosePlayer) }, title = panel.name) {
        PlayerSheetContent(panel, onIntent)
    }
}

/**
 * One person under two names (S26b, PP-110): the player's season all time; their other names, each
 * with Separate; and everyone who could be them (anyone who never played a night with them), likely
 * spellings first. One picked, the sheet asks which name to keep, and the merge applies at once with
 * Undo on the snackbar. The saved nights keep the names they were saved with.
 */
@Composable
internal fun PlayerSheetContent(panel: PlayerPanel, onIntent: (HistoryIntent) -> Unit) {
    val text = rememberHistoryText()
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        panel.standing?.let { standing ->
            // "13 points · 3 nights · 2 wins", all time
            val separator = stringResource(R.string.history_separator)
            Text(
                text = text.points(standing.points) + separator + text.record(standing),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.Chalk,
            )
        }
        val picked = panel.picked
        if (picked != null) {
            KeepWhich(panel.name, picked, onIntent)
        } else {
            if (panel.aliases.isNotEmpty()) OtherNames(panel.aliases, onIntent)
            SamePerson(panel, onIntent)
            PokerButton(
                text = stringResource(R.string.history_close),
                onClick = { onIntent(HistoryIntent.ClosePlayer) },
                variant = PokerButtonVariant.Secondary,
                size = PokerButtonSize.Small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "THEIR OTHER NAMES": each with Separate, which counts it on its own again. */
@Composable
private fun OtherNames(aliases: List<String>, onIntent: (HistoryIntent) -> Unit) {
    Column {
        Heading(stringResource(R.string.history_also_heading))
        aliases.forEachIndexed { index, alias ->
            if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
            val label = stringResource(R.string.history_separate_name, alias)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PokerDimens.MinTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = alias,
                    style = MaterialTheme.typography.titleMedium,
                    color = PokerColors.CardWhite,
                    modifier = Modifier.weight(1f),
                )
                PokerButton(
                    text = stringResource(R.string.history_separate),
                    onClick = { onIntent(HistoryIntent.Separate(alias)) },
                    variant = PokerButtonVariant.Text,
                    size = PokerButtonSize.Small,
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
    }
}

/** "SAME PERSON AS…": what a merge does, then everyone who could be them, or why nobody is listed. */
@Composable
private fun SamePerson(panel: PlayerPanel, onIntent: (HistoryIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Heading(stringResource(R.string.history_same_heading))
        Note(stringResource(if (panel.candidates.isEmpty()) R.string.history_same_none else R.string.history_same_note))
        panel.candidates.forEachIndexed { index, candidate ->
            if (index > 0) HorizontalDivider(color = PokerColors.FeltLine)
            CandidateRow(candidate) { onIntent(HistoryIntent.PickSame(candidate.name)) }
        }
        if (panel.leftOut > 0) {
            Note(
                text = pluralStringResource(R.plurals.history_same_left_out, panel.leftOut, panel.leftOut, panel.name),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** "Mike R. / 1 night  ›": one tap picks them; TalkBack reads it as one button. */
@Composable
private fun CandidateRow(candidate: MergeCandidate, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.RowMinHeight)
            .clickable(role = Role.Button, onClick = onPick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(candidate.name, style = MaterialTheme.typography.titleMedium, color = PokerColors.CardWhite)
            Text(
                text = if (candidate.nights > 0) {
                    pluralStringResource(R.plurals.history_nights, candidate.nights, candidate.nights)
                } else {
                    stringResource(R.string.history_no_night_yet)
                },
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        Icon(PokerIcons.Merge, contentDescription = null, tint = PokerColors.PokerGold)
    }
}

/** Two names, one person: keep either; Back goes back to the list. */
@Composable
private fun KeepWhich(name: String, picked: MergeCandidate, onIntent: (HistoryIntent) -> Unit) {
    Text(
        text = stringResource(R.string.history_keep_question, name, picked.name),
        style = MaterialTheme.typography.bodyLarge,
        color = PokerColors.CardWhite,
    )
    PokerButton(
        text = stringResource(R.string.history_keep, name),
        onClick = { onIntent(HistoryIntent.Merge(from = picked.name, into = name)) },
        icon = PokerIcons.Merge,
        modifier = Modifier.fillMaxWidth(),
    )
    PokerButton(
        text = stringResource(R.string.history_keep, picked.name),
        onClick = { onIntent(HistoryIntent.Merge(from = name, into = picked.name)) },
        variant = PokerButtonVariant.Secondary,
        icon = PokerIcons.Merge,
        modifier = Modifier.fillMaxWidth(),
    )
    PokerButton(
        text = stringResource(R.string.history_back),
        onClick = { onIntent(HistoryIntent.PickSame(null)) },
        variant = PokerButtonVariant.Text,
        size = PokerButtonSize.Small,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Heading(text: String) {
    PokerEyebrow(text = text, color = PokerColors.PokerGold, modifier = Modifier.padding(bottom = 4.dp).semantics { heading() })
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk, modifier = modifier)
}
