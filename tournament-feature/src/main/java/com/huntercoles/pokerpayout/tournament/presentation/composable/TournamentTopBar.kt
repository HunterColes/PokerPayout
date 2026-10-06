package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.core.R as CoreR

/**
 * The Tournament tab's header: the tab's name (renaming the tab, D3, renames it), a live subtitle
 * ("Level 6 of 9 · running"), and its actions. Before the start: reset. With a clock: the bell (mute
 * the chimes; hold it for Tools, Sound), ⤢ (the table view), and ⋮ (New tournament…, Sound settings).
 * On small phones the bell moves into ⋮.
 */
@Composable
internal fun TournamentTopBar(
    timer: TimerUiState,
    mode: TournamentMode,
    actions: TournamentActions,
    small: Boolean,
    wide: Boolean,
) {
    val reset = { actions.onSetupIntent(TournamentConfigIntent.ShowResetDialog) }
    PokerTopBar(
        title = stringResource(CoreR.string.navigation_tournament),
        subtitle = subtitle(timer, mode, wide),
    ) {
        if (mode == TournamentMode.Setup) {
            PokerIconButton(
                icon = PokerIcons.Restart,
                contentDescription = stringResource(R.string.tournament_reset),
                onClick = reset,
            )
            MoreMenu(actions, timer, newTournament = null, bellInMenu = false)
        } else {
            if (!small) BellButton(timer.isMuted, actions)
            PokerIconButton(
                icon = PokerIcons.Fullscreen,
                contentDescription = stringResource(R.string.clock_table_view),
                onClick = {
                    actions.updateUi { it.pauseRotation(false) }
                    actions.onTimerIntent(TimerIntent.SetTableView(true))
                },
            )
            MoreMenu(actions, timer, newTournament = reset, bellInMenu = small)
        }
    }
}

/** "Level 6 of 9 · running", "Break 1 · 8 of 9 left", "Setup · not started". */
@Composable
private fun subtitle(timer: TimerUiState, mode: TournamentMode, wide: Boolean): String {
    val level = timer.currentLevelSegment?.level?.level ?: 1
    val levels = timer.regularLevelCount
    val table = timer.table
    val clockShown = mode != TournamentMode.Setup && mode != TournamentMode.Folding
    val base = when {
        mode == TournamentMode.Setup -> stringResource(R.string.clock_subtitle_setup)
        mode == TournamentMode.Folding -> stringResource(R.string.clock_subtitle_starting, level)
        timer.isFinished -> stringResource(R.string.clock_subtitle_finished)
        timer.isOnBreak -> stringResource(
            R.string.clock_subtitle_break,
            timer.currentBreak?.number ?: 1,
            table.playersLeft,
            table.playerCount,
        )
        timer.currentLevelSegment?.isOvertime == true -> stringResource(R.string.clock_subtitle_overtime, level)
        timer.isRunning -> stringResource(R.string.clock_subtitle_running, level, levels)
        else -> stringResource(R.string.clock_subtitle_paused, level, levels)
    }
    val withPlayers = wide && clockShown && !timer.isOnBreak
    return if (withPlayers) {
        stringResource(R.string.clock_subtitle_with_players, base, table.playersLeft, table.playerCount)
    } else {
        base
    }
}

/** The bell: tap to mute or unmute the chimes; hold to open Tools, Sound. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BellButton(muted: Boolean, actions: TournamentActions) {
    val description = stringResource(if (muted) R.string.clock_unmute else R.string.clock_mute)
    val sound = stringResource(R.string.clock_sound_settings)
    Box(
        modifier = Modifier
            .size(PokerDimens.MinTouch)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = sound,
                onLongClick = actions.openSound,
                onClick = { actions.onTimerIntent(TimerIntent.ToggleMute) },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PokerIcons.Bell,
            contentDescription = null,
            tint = if (muted) PokerColors.ChalkDim else PokerColors.Chalk,
            modifier = if (muted) Modifier.slashed() else Modifier,
        )
    }
}

/** A muted bell: the bell with a stroke through it. */
private fun Modifier.slashed(): Modifier = drawWithContent {
    drawContent()
    val inset = size.minDimension * SLASH_INSET
    drawLine(
        color = PokerColors.Chalk,
        start = Offset(inset, inset),
        end = Offset(size.width - inset, size.height - inset),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

@Composable
private fun MoreMenu(actions: TournamentActions, timer: TimerUiState, newTournament: (() -> Unit)?, bellInMenu: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokerIconButton(
            icon = PokerIcons.More,
            contentDescription = stringResource(R.string.clock_more),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = PokerColors.DarkGreen) {
            if (bellInMenu) {
                MenuItem(stringResource(if (timer.isMuted) R.string.clock_unmute else R.string.clock_mute)) {
                    open = false
                    actions.onTimerIntent(TimerIntent.ToggleMute)
                }
            }
            newTournament?.let { start ->
                MenuItem(stringResource(R.string.clock_new_tournament)) {
                    open = false
                    start()
                }
            }
            MenuItem(stringResource(R.string.clock_sound_settings)) {
                open = false
                actions.openSound()
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, color = PokerColors.CardWhite) }, onClick = onClick)
}

private const val SLASH_INSET = 0.18f
