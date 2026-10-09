package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.audio.music.MusicTrack
import com.huntercoles.pokerpayout.core.audio.music.RepeatMode
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

/** What plays (or plays next), the player's buttons, and the music's own volume. */
@Composable
internal fun NowPlaying(state: MusicUiState, onIntent: (MusicIntent) -> Unit) {
    val current = state.current
    PokerEyebrow(
        text = stringResource(if (state.playing) R.string.music_now_playing else R.string.music_up_next),
        color = if (state.playing) PokerColors.PokerGold else PokerColors.Chalk,
    )
    if (current == null) {
        Text(
            text = stringResource(R.string.music_no_song),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.Chalk,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = current.title, style = ToolTitle, color = PokerColors.CardWhite)
            Text(
                text = stringResource(R.string.music_position, state.position, state.tracks.size),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
            if (current.ref in state.missing) MissingLabel()
        }
    }
    Transport(state, onIntent)
    VolumeRow(
        volume = state.volume,
        enabled = true,
        label = stringResource(R.string.music_volume),
        onVolume = { onIntent(MusicIntent.SetVolume(it)) },
    )
}

/** Shuffle, previous, play or pause, next, repeat: across the card. */
@Composable
private fun Transport(state: MusicUiState, onIntent: (MusicIntent) -> Unit) {
    val hasSongs = state.tracks.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(
            icon = PokerIcons.Shuffle,
            label = stringResource(R.string.music_shuffle),
            on = state.shuffle,
            enabled = hasSongs,
            onToggle = { onIntent(MusicIntent.SetShuffle(it)) },
        )
        PokerIconButton(
            icon = PokerIcons.Previous,
            contentDescription = stringResource(R.string.music_previous),
            onClick = { onIntent(MusicIntent.Previous) },
            tint = PokerColors.CardWhite,
            enabled = hasSongs,
        )
        PlayButton(playing = state.playing, enabled = hasSongs, onClick = { onIntent(MusicIntent.TogglePlay) })
        PokerIconButton(
            icon = PokerIcons.Next,
            contentDescription = stringResource(R.string.music_next),
            onClick = { onIntent(MusicIntent.Next) },
            tint = PokerColors.CardWhite,
            enabled = hasSongs,
        )
        RepeatButton(repeat = state.repeat, enabled = hasSongs, onClick = { onIntent(MusicIntent.CycleRepeat) })
    }
}

/** The big gold play or pause button. */
@Composable
private fun PlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (playing) R.string.music_pause else R.string.music_play)
    Box(
        modifier = Modifier
            .size(PlayButtonSize)
            .clip(CircleShape)
            .background(if (enabled) PokerColors.PokerGold else PokerColors.FeltHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) PokerIcons.Pause else PokerIcons.Play,
            contentDescription = null,
            tint = if (enabled) PokerColors.FeltDeep else PokerColors.ChalkDim,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** A 48 dp icon that is a switch (shuffle): gold with a dot under it when on. */
@Suppress("LongParameterList") // icon, its name, its state, enabled, what it does, modifier
@Composable
private fun ToggleIcon(
    icon: ImageVector,
    label: String,
    on: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(PokerDimens.MinTouch)
            .clip(CircleShape)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        StateIcon(icon, lit = on, enabled = enabled)
    }
}

/** Repeat: off, all songs (gold), this song (gold, with a 1). One tap moves it on. */
@Composable
private fun RepeatButton(repeat: RepeatMode, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when (repeat) {
            RepeatMode.OFF -> R.string.music_repeat_off
            RepeatMode.ALL -> R.string.music_repeat_all
            RepeatMode.ONE -> R.string.music_repeat_one
        },
    )
    Box(
        modifier = Modifier
            .size(PokerDimens.MinTouch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        StateIcon(
            if (repeat == RepeatMode.ONE) PokerIcons.RepeatOne else PokerIcons.Repeat,
            lit = repeat != RepeatMode.OFF,
            enabled = enabled,
        )
    }
}

/** An icon gold with a dot under it when [lit], chalk when not, dim while not [enabled]. */
@Composable
private fun StateIcon(icon: ImageVector, lit: Boolean, enabled: Boolean) {
    val tint = when {
        !enabled -> PokerColors.ChalkDim
        lit -> PokerColors.PokerGold
        else -> PokerColors.Chalk
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = tint)
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (lit) tint else Color.Transparent))
    }
}

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

/** A song while editing: its title, then up, down and remove. */
@Composable
private fun EditableSong(track: MusicTrack, index: Int, lastIndex: Int, missing: Boolean, onIntent: (MusicIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = PokerDimens.SpacingSmall)) {
        Text(text = track.title, style = MaterialTheme.typography.bodyLarge, color = PokerColors.CardWhite)
        if (missing) MissingLabel()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
    }
}

@Composable
internal fun MissingLabel() {
    Text(text = stringResource(R.string.music_missing), style = MaterialTheme.typography.bodySmall, color = PokerColors.Danger)
}

private val PlayButtonSize = 56.dp
