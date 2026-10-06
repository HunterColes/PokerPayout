package com.huntercoles.pokerpayout.tools.presentation.composable

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavOptions
import com.huntercoles.pokerpayout.core.constants.AudioConstants
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.navigation.NavigationCommand
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeIntent
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeViewModel
import kotlin.math.roundToInt

/** One row of the Tools list: where it goes, its icon, its name and what it does. */
private data class Tool(
    val destination: NavigationDestination,
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val description: Int,
)

private val Tools = listOf(
    Tool(NavigationDestination.OddsCalculator, PokerIcons.Cards, R.string.tools_odds_title, R.string.tools_odds_description),
    Tool(NavigationDestination.ChipCalculator, PokerIcons.Chip, R.string.tools_chips_title, R.string.tools_chips_description),
    Tool(NavigationDestination.HandRanks, PokerIcons.List, R.string.tools_ranks_title, R.string.tools_ranks_description),
)

/** The Tools tab (S7): the tools as a list, then the Sound section, then the app's promise. */
@Composable
fun ToolsHomeScreen(
    navigationManager: NavigationManager,
    viewModel: ToolsHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }
    ToolsHomeContent(
        state = state,
        onIntent = viewModel::acceptIntent,
        onOpenTool = { destination ->
            navigationManager.navigate(object : NavigationCommand {
                override val destination = destination
                override val configuration: NavOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
            })
        },
        versionName = versionName,
    )
}

/** Stateless S7, for the app and for screenshot tests. */
@Composable
fun ToolsHomeContent(
    state: ToolsHomeUiState,
    onIntent: (ToolsHomeIntent) -> Unit,
    onOpenTool: (NavigationDestination) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PokerTopBar(title = stringResource(R.string.tools_title), subtitle = stringResource(R.string.tools_subtitle))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = PokerDimens.Gutter, end = PokerDimens.Gutter, top = 4.dp, bottom = PokerDimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            Tools.forEach { tool ->
                ToolRow(
                    icon = tool.icon,
                    title = stringResource(tool.title),
                    description = stringResource(tool.description),
                    onClick = { onOpenTool(tool.destination) },
                )
            }
            SoundSection(state = state, onIntent = onIntent)
            Text(
                text = stringResource(R.string.tools_footer, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PokerDimens.SpacingMedium, vertical = PokerDimens.SpacingSmall),
            )
        }
    }
}

/** A tool: icon tile, name and what it's for, and a chevron. The whole row is one button. */
@Composable
private fun ToolRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ToolRowMinHeight)
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(PokerDimens.MinTouch)
                .background(PokerColors.DarkGreen, RoundedCornerShape(PokerDimens.CornerControl)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = PokerColors.PokerGold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, style = ToolTitle, color = PokerColors.CardWhite)
            Text(text = description, style = ToolDescription, color = PokerColors.Chalk)
        }
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.Chalk)
    }
}

/**
 * Sound, inline (it used to be a volume dialog behind a "Settings" tile): the chime on or off, its
 * volume, and a button to hear it. Turning the sound off keeps the volume for when it comes back.
 */
@Composable
private fun SoundSection(state: ToolsHomeUiState, onIntent: (ToolsHomeIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PokerDimens.CornerCard))
            .background(PokerColors.FeltGreen)
            .padding(PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.MinTouch)
                .toggleable(
                    value = state.soundOn,
                    role = Role.Switch,
                    onValueChange = { onIntent(ToolsHomeIntent.SetSoundOn(it)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingMedium),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(R.string.tools_sound_title), style = ToolTitle, color = PokerColors.CardWhite)
                Text(
                    text = stringResource(R.string.tools_sound_description, AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            }
            Switch(checked = state.soundOn, onCheckedChange = null, colors = soundSwitchColors())
        }
        VolumeRow(volume = state.volume, enabled = state.soundOn, onVolume = { onIntent(ToolsHomeIntent.SetVolume(it)) })
        PokerButton(
            text = stringResource(R.string.tools_test_chime),
            onClick = { onIntent(ToolsHomeIntent.TestChime) },
            variant = PokerButtonVariant.Secondary,
            size = PokerButtonSize.Small,
            icon = PokerIcons.Bell,
            enabled = state.soundOn,
        )
    }
}

@Composable
private fun VolumeRow(volume: Float, enabled: Boolean, onVolume: (Float) -> Unit) {
    val volumeLabel = stringResource(R.string.tools_volume)
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
private fun soundSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PokerColors.FeltDeep,
    checkedTrackColor = PokerColors.PokerGold,
    checkedBorderColor = PokerColors.PokerGold,
    uncheckedThumbColor = PokerColors.Chalk,
    uncheckedTrackColor = PokerColors.FeltDeep,
    uncheckedBorderColor = PokerColors.FeltEdge,
)

/** The installed version, for the footer ("v1.3.0"); empty if the system won't say. */
private fun appVersionName(context: Context): String = runCatching {
    val packages = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packages.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull().orEmpty()

private const val PERCENT = 100
private val ToolRowMinHeight = 76.dp

/** Tool names and section titles: Barlow, as in the mockup's tool rows. */
private val ToolTitle = PokerType.Title.copy(fontSize = 21.sp, lineHeight = 24.sp)

/** What a tool does, under its name. */
private val ToolDescription = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
