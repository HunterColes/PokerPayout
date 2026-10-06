package com.huntercoles.pokerpayout.tournament.presentation.composable

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockSegment
import com.huntercoles.pokerpayout.tournament.presentation.ClockFormat
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState

/** Width of "12:34" in units of the font size, roughly; sizes the hero to fill its column. */
private const val HERO_WIDTH_EMS = 3.1f

/** In landscape the hero takes this share of the width; the blinds the rest. */
private const val HERO_COLUMN_SHARE = 0.55f
private const val BLINDS_COLUMN_SHARE = 1f - HERO_COLUMN_SHARE

/** The hero's font is capped at this share of the screen height. */
private const val HERO_MAX_HEIGHT_LANDSCAPE = 0.42f
private const val HERO_MAX_HEIGHT_PORTRAIT = 0.22f

/** Blinds text relative to the hero. */
private const val BLINDS_TO_HERO = 0.42f
private const val HERO_LINE_HEIGHT = 1.05f
private const val DIVIDER_HEIGHT = 0.7f
private const val PROGRESS_WIDTH = 0.85f

/**
 * Full-screen landscape clock for a propped-up phone or tablet (PP-025). Locks the activity to
 * landscape while open and restores the manifest orientation afterwards (the app is portrait otherwise).
 */
@Composable
internal fun TableViewDialog(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            // Rotating recreates the activity; only restore when the view is really closing.
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = activity.manifestOrientation()
            }
        }
    }
    Dialog(
        onDismissRequest = { onIntent(TimerIntent.SetTableView(false)) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The dialog is its own window: keep the screen on from here too while the clock runs
        val view = LocalView.current
        DisposableEffect(uiState.isRunning) {
            view.keepScreenOn = uiState.isRunning
            onDispose { view.keepScreenOn = false }
        }
        TableView(uiState, onIntent)
    }
}

@Composable
private fun TableView(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit) {
    val formatter = rememberChipFormatter()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PokerColors.PokerBlack)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        val landscape = maxWidth > maxHeight
        val heroSize = heroFontSize(
            width = if (landscape) maxWidth * HERO_COLUMN_SHARE else maxWidth,
            height = maxHeight * if (landscape) HERO_MAX_HEIGHT_LANDSCAPE else HERO_MAX_HEIGHT_PORTRAIT
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headline(uiState),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = PokerColors.PokerGold,
                    modifier = Modifier.weight(1f)
                )
                statusText(uiState)?.let { StatusPill(it) }
                IconButton(onClick = { onIntent(TimerIntent.SetTableView(false)) }) {
                    Icon(Icons.Default.Close, contentDescription = "Exit table view", tint = PokerColors.PokerGold)
                }
            }
            val segment = uiState.currentSegment
            if (segment != null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TableBody(uiState, segment, heroSize, landscape, onIntent)
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        TournamentLine(uiState, fontSize = 16.sp)
                        TableStatsLine(uiState, formatter, fontSize = 16.sp)
                    }
                    ClockControls(uiState, onIntent, playSize = 56.dp)
                }
            }
        }
    }
}

@Composable
private fun heroFontSize(width: Dp, height: Dp): TextUnit = with(LocalDensity.current) {
    minOf((width / HERO_WIDTH_EMS).toPx(), height.toPx()).toSp()
}

@Composable
private fun TableBody(
    uiState: TimerUiState,
    segment: ClockSegment,
    heroSize: TextUnit,
    landscape: Boolean,
    onIntent: (TimerIntent) -> Unit
) {
    val formatter = rememberChipFormatter()
    val blinds = @Composable {
        CurrentBlinds(segment, formatter, bigSize = heroSize * BLINDS_TO_HERO)
        Spacer(Modifier.height(12.dp))
        NextLevelLine(uiState, formatter, fontSize = 24.sp)
    }
    if (landscape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val centered = Alignment.CenterHorizontally
            Column(modifier = Modifier.weight(HERO_COLUMN_SHARE), horizontalAlignment = centered) {
                TableHero(uiState, heroSize, onIntent)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(DIVIDER_HEIGHT)
                    .background(PokerColors.CardWhite.copy(alpha = 0.15f))
            )
            Column(modifier = Modifier.weight(BLINDS_COLUMN_SHARE), horizontalAlignment = centered) {
                blinds()
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            TableHero(uiState, heroSize, onIntent)
            Spacer(Modifier.height(16.dp))
            blinds()
        }
    }
}

@Composable
private fun TableHero(uiState: TimerUiState, heroSize: TextUnit, onIntent: (TimerIntent) -> Unit) {
    Text(
        text = if (uiState.isOnBreak) "BREAK TIME LEFT" else "LEVEL TIME LEFT",
        style = Caption.copy(fontSize = 14.sp),
        color = Dim
    )
    Text(
        text = ClockFormat.clock(uiState.segmentRemainingSeconds),
        fontSize = heroSize,
        lineHeight = heroSize * HERO_LINE_HEIGHT,
        fontWeight = FontWeight.Bold,
        color = heroColor(uiState),
        maxLines = 1,
        modifier = Modifier.clickable(enabled = !uiState.isFinished) { onIntent(TimerIntent.ToggleTimer) }
    )
    ToneProgress(uiState, Modifier.fillMaxWidth(PROGRESS_WIDTH))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION") // the flags overload needs API 33
private fun Activity.manifestOrientation(): Int =
    runCatching { packageManager.getActivityInfo(componentName, 0).screenOrientation }
        .getOrDefault(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
