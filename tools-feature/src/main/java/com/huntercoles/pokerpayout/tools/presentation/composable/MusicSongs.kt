package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.MusicIntent
import com.huntercoles.pokerpayout.tools.presentation.MusicUiState

/** The songs: tap one to play it, or Edit to move and remove them; then Add songs. */
@Composable
internal fun SongList(state: MusicUiState, onIntent: (MusicIntent) -> Unit, onAddSongs: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PokerEyebrow(stringResource(R.string.music_songs), modifier = Modifier.weight(1f))
        if (state.tracks.isNotEmpty()) {
            PokerButton(
                text = stringResource(if (state.editing) R.string.music_done else R.string.music_edit),
                onClick = { onIntent(MusicIntent.SetEditing(!state.editing)) },
                variant = PokerButtonVariant.Text,
                size = PokerButtonSize.Small,
                icon = if (state.editing) PokerIcons.Check else PokerIcons.Edit,
            )
        }
    }
    if (state.tracks.isEmpty()) {
        Text(
            text = stringResource(R.string.music_songs_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
    }
    if (state.nothingPlayable) {
        Text(
            text = stringResource(R.string.music_none_playable),
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.Danger,
        )
    }
    state.tracks.forEachIndexed { index, track ->
        val missing = track.ref in state.missing
        if (state.editing) {
            EditableSong(track, index, state.tracks.lastIndex, missing, onIntent)
        } else {
            SongRow(
                track,
                current = track.ref == state.currentRef,
                playing = state.playing,
                missing = missing,
                onIntent = onIntent,
            )
        }
    }
    PokerButton(
        text = stringResource(R.string.music_add_songs),
        onClick = onAddSongs,
        variant = PokerButtonVariant.Secondary,
        size = PokerButtonSize.Small,
        icon = PokerIcons.Plus,
    )
}

/** A song: one tap plays it. The current one is lit, with a speaker while it plays. */
@Composable
private fun SongRow(track: MusicTrack, current: Boolean, playing: Boolean, missing: Boolean, onIntent: (MusicIntent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .clip(RoundedCornerShape(PokerDimens.CornerControl))
            .background(if (current) PokerColors.FeltHigh else Color.Transparent)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.music_play_song, track.title),
                onClick = { onIntent(MusicIntent.PlayTrack(track.ref)) },
            )
            .padding(horizontal = PokerDimens.SpacingSmall, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Icon(
            imageVector = if (current && playing) PokerIcons.Volume else PokerIcons.MusicNote,
            contentDescription = null,
            tint = if (current) PokerColors.PokerGold else PokerColors.Chalk,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = track.title, style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite)
            if (missing) MissingLabel()
        }
    }
}

/**
 * A song while editing: its title, then up, down and remove; beside the title where the card is
 * wide enough, under it on a phone (so a long title never squeezes into a narrow column).
 */
@Composable
private fun EditableSong(track: MusicTrack, index: Int, lastIndex: Int, missing: Boolean, onIntent: (MusicIntent) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val title = @Composable {
            Text(text = track.title, style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite)
            if (missing) MissingLabel()
        }
        if (maxWidth >= EditInOneRowWidth) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(start = PokerDimens.SpacingSmall)) { title() }
                EditButtons(track, index, lastIndex, onIntent)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(start = PokerDimens.SpacingSmall)) {
                title()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EditButtons(track, index, lastIndex, onIntent)
                }
            }
        }
    }
}

/** Move up, move down, remove. */
@Composable
private fun EditButtons(track: MusicTrack, index: Int, lastIndex: Int, onIntent: (MusicIntent) -> Unit) {
    PokerIconButton(
        icon = PokerIcons.ArrowUp,
        contentDescription = stringResource(R.string.music_move_up, track.title),
        onClick = { onIntent(MusicIntent.Move(index, index - 1)) },
        enabled = index > 0,
    )
    PokerIconButton(
        icon = PokerIcons.ArrowDown,
        contentDescription = stringResource(R.string.music_move_down, track.title),
        onClick = { onIntent(MusicIntent.Move(index, index + 1)) },
        enabled = index < lastIndex,
    )
    PokerIconButton(
        icon = PokerIcons.Close,
        contentDescription = stringResource(R.string.music_remove, track.title),
        onClick = { onIntent(MusicIntent.Remove(track.ref)) },
        tint = PokerColors.Danger,
    )
}

@Composable
internal fun MissingLabel() {
    Text(text = stringResource(R.string.music_missing), style = MaterialTheme.typography.bodySmall, color = PokerColors.Danger)
}

/** From this card width a song's edit buttons sit beside its title. */
private val EditInOneRowWidth = 480.dp
