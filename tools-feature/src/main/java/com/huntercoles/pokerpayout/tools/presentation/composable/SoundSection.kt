package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.audio.packs.SoundPacks
import com.huntercoles.pokerpayout.core.constants.AudioConstants
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.MusicSummary
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeIntent
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import kotlin.math.roundToInt

/**
 * Sound, inline (it used to be a volume dialog behind a "Settings" tile): the chime on or off, its
 * volume, a button to hear it, and the way to the cue sound packs. Turning the sound off keeps the
 * volume for when it comes back. Then the quiet cues (PP-083), each its own switch and independent
 * of the sound, so a muted phone still buzzes and flashes; then the music, with a play button; and,
 * only while the app's notifications are off, the way to turn them on for the live clock (PP-081).
 */
@Composable
internal fun SoundSection(
    state: ToolsHomeUiState,
    onIntent: (ToolsHomeIntent) -> Unit,
    onAllowNotifications: () -> Unit,
    onOpen: (NavigationDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SwitchRow(
            title = stringResource(R.string.tools_sound_title),
            description = stringResource(R.string.tools_sound_description, AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS),
            checked = state.soundOn,
            onChange = { onIntent(ToolsHomeIntent.SetSoundOn(it)) },
        )
        VolumeRow(
            volume = state.volume,
            enabled = state.soundOn,
            label = stringResource(R.string.tools_volume),
            onVolume = { onIntent(ToolsHomeIntent.SetVolume(it)) },
        )
        PokerButton(
            text = stringResource(R.string.tools_test_chime),
            onClick = { onIntent(ToolsHomeIntent.TestChime) },
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            icon = PokerIcons.Bell,
            enabled = state.soundOn,
        )
        LinkRow(
            title = stringResource(R.string.tools_cue_sounds_title),
            description = stringResource(SoundPacks.byId(state.soundPack).name),
            onClick = { onOpen(NavigationDestination.CueSounds) },
        )
        HorizontalDivider(color = PokerColors.FeltLine)
        if (state.canVibrate) {
            SwitchRow(
                title = stringResource(R.string.tools_vibrate_title),
                description = stringResource(R.string.tools_vibrate_description),
                checked = state.vibrate,
                onChange = { onIntent(ToolsHomeIntent.SetVibrate(it)) },
            )
        }
        SwitchRow(
            title = stringResource(R.string.tools_flash_title),
            description = stringResource(R.string.tools_flash_description),
            checked = state.flash,
            onChange = { onIntent(ToolsHomeIntent.SetFlash(it)) },
        )
        HorizontalDivider(color = PokerColors.FeltLine)
        MusicRow(
            state.music,
            onToggle = { onIntent(ToolsHomeIntent.ToggleMusic) },
            onOpen = { onOpen(NavigationDestination.Music) },
        )
        if (state.notificationsOff) NotificationsOff(onAllowNotifications)
    }
}

/** A row that opens a screen: its name, what it is set to, and a chevron. The whole row is one button. */
@Composable
private fun LinkRow(title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = ToolTitle, color = PokerColors.CardWhite)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.Chalk)
    }
}

/** The music: a row to its screen saying what plays, and, with songs in the list, a play or pause button beside it. */
@Composable
private fun MusicRow(music: MusicSummary, onToggle: () -> Unit, onOpen: () -> Unit) {
    val description = when {
        music.songs == 0 -> stringResource(R.string.tools_music_none)
        music.playing && music.current != null -> stringResource(R.string.tools_music_playing, music.current)
        else -> pluralStringResource(R.plurals.tools_music_songs, music.songs, music.songs)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingSmall)) {
        if (music.songs > 0) {
            PokerIconButton(
                icon = if (music.playing) PokerIcons.Pause else PokerIcons.Play,
                contentDescription = stringResource(if (music.playing) R.string.music_pause else R.string.music_play),
                onClick = onToggle,
                tint = PokerColors.PokerGold,
            )
        }
        LinkRow(
            title = stringResource(R.string.tools_music_title),
            description = description,
            onClick = onOpen,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A setting that is one switch: the whole row toggles it, and TalkBack hears the title and what it does. */
@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = ToolTitle, color = PokerColors.CardWhite)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        }
        Switch(checked = checked, onCheckedChange = null, colors = soundSwitchColors())
    }
}

/** PP-081: notifications are off, so the running clock can't show on the lock screen; one tap to the setting. */
@Composable
private fun NotificationsOff(onAllow: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.tools_lock_screen_title), style = ToolTitle, color = PokerColors.CardWhite)
            Text(
                text = stringResource(R.string.tools_lock_screen_off),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        PokerButton(
            text = stringResource(R.string.tools_lock_screen_allow),
            onClick = onAllow,
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            icon = PokerIcons.Bell,
        )
    }
}

/** A volume slider, quiet to loud, and the volume in percent. TalkBack hears [label] ("Chime volume"). */
@Composable
internal fun VolumeRow(volume: Float, enabled: Boolean, label: String, onVolume: (Float) -> Unit) {
    val volumeLabel = label
    val accent = if (enabled) PokerColors.PokerGold else PokerColors.ChalkDim
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
    ) {
        Icon(PokerIcons.VolumeMute, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(22.dp))
        Slider(
            value = volume,
            onValueChange = onVolume,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = volumeLabel },
            thumb = { Box(Modifier.size(22.dp).background(accent, CircleShape)) },
            track = { sliderState -> VolumeTrack(sliderState, accent) },
        )
        Icon(PokerIcons.Volume, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(R.string.tools_volume_percent, (volume * PERCENT).roundToInt()),
            style = PokerType.NumberM.copy(fontSize = 18.sp, lineHeight = 22.sp),
            color = if (enabled) PokerColors.CardWhite else PokerColors.Chalk,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}

/** A 6 dp track, gold up to the volume. */
@Composable
private fun VolumeTrack(state: SliderState, accent: Color) {
    val range = state.valueRange
    val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(PokerColors.FeltDeep),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(accent),
        )
    }
}

/** Gold track and a dark thumb when on (as the primary button); an outlined dark track when off. */
@Composable
internal fun soundSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PokerColors.FeltDeep,
    checkedTrackColor = PokerColors.PokerGold,
    checkedBorderColor = PokerColors.PokerGold,
    uncheckedThumbColor = PokerColors.Chalk,
    uncheckedTrackColor = PokerColors.FeltDeep,
    uncheckedBorderColor = PokerColors.FeltEdge,
)

private const val PERCENT = 100
