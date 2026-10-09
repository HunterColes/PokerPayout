package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import com.huntercoles.pokerpayout.core.audio.music.RepeatMode
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
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
    if (current != null) {
        PokerEyebrow(
            text = stringResource(if (state.playing) R.string.music_now_playing else R.string.music_up_next),
            color = if (state.playing) PokerColors.PokerGold else PokerColors.Chalk,
        )
    }
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
    // Across the card on a phone; on a tablet together in the middle, not spread wide
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth()
            .widthIn(max = TransportMaxWidth)
            .fillMaxWidth(),
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

private val PlayButtonSize = 56.dp

/** The player's five buttons sit within this on a wide card. */
private val TransportMaxWidth = 400.dp
