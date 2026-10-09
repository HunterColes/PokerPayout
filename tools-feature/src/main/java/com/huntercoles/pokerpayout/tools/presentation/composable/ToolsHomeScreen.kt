package com.huntercoles.pokerpayout.tools.presentation.composable

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavOptions
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.navigation.NavigationCommand
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeIntent
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeViewModel

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
    Tool(NavigationDestination.SeatDraw, PokerIcons.Seat, R.string.tools_seats_title, R.string.tools_seats_description),
    Tool(NavigationDestination.History, PokerIcons.Trophy, R.string.tools_history_title, R.string.tools_history_description),
    Tool(NavigationDestination.Backup, PokerIcons.Save, R.string.tools_backup_title, R.string.tools_backup_description),
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
    val canVibrate = remember(context) { context.getSystemService(Vibrator::class.java)?.hasVibrator() == true }
    val notificationsOff = rememberNotificationsOff(context)
    ToolsHomeContent(
        state = state.copy(canVibrate = canVibrate, notificationsOff = notificationsOff),
        onIntent = viewModel::acceptIntent,
        onOpenTool = { destination ->
            navigationManager.navigate(object : NavigationCommand {
                override val destination = destination
                override val configuration: NavOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
            })
        },
        versionName = versionName,
        onAllowNotifications = { openNotificationSettings(context) },
    )
}

/** Stateless S7, for the app and for screenshot tests. */
@Suppress("LongParameterList") // state, intents, tool links, version, modifier, notification settings
@Composable
fun ToolsHomeContent(
    state: ToolsHomeUiState,
    onIntent: (ToolsHomeIntent) -> Unit,
    onOpenTool: (NavigationDestination) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
    onAllowNotifications: () -> Unit = {},
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
            SoundSection(state = state, onIntent = onIntent, onAllowNotifications = onAllowNotifications, onOpen = onOpenTool)
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
 * PP-081: whether the app's notifications are off (on Android 13 and up, also when the permission
 * was never given), checked again each time the screen comes back, as after a trip to the settings.
 */
@Composable
private fun rememberNotificationsOff(context: Context): Boolean {
    var off by remember(context) { mutableStateOf(notificationsOff(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) off = notificationsOff(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return off
}

private fun notificationsOff(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == false

/** The app's own notification settings, where they can be turned back on. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

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

private val ToolRowMinHeight = 76.dp

/** Tool names and section titles: Barlow, as in the mockup's tool rows. */
internal val ToolTitle = PokerType.Title.copy(fontSize = 21.sp, lineHeight = 24.sp)

/** What a tool does, under its name. */
private val ToolDescription = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
