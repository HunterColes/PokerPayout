package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.ConfirmSheet
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.PokerButtonSize
import com.huntercoles.pokerpayout.core.design.components.PokerButtonVariant
import com.huntercoles.pokerpayout.core.design.components.PokerEyebrow
import com.huntercoles.pokerpayout.core.design.components.PokerIconButton
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent

private val PanelShape = RoundedCornerShape(bottomStart = PokerDimens.CornerSheet, bottomEnd = PokerDimens.CornerSheet)

/**
 * S1 v2, mid-game: setup unfolded down from the strip, over the clock, which keeps running.
 *
 * - **Change any time:** players (a late entry adds a Bank row), rebuys-until, the BB ante, break
 *   length and note. The clock keeps its place when breaks or the ante change.
 * - **Locked while the clock runs:** money and blinds, shown read-only behind "Unlock to edit…", which
 *   says what happens first (a [ConfirmSheet]). Unlocked, blind changes rebuild the schedule from the
 *   current level and keep its time left; one that can't be played is refused with the fixes.
 * - **Presets** (PP-032): save or share the setup; loading one waits for a new tournament.
 */
@Composable
internal fun SetupPanel(
    setup: TournamentConfigUiState,
    timer: TimerUiState,
    ui: TournamentUi,
    actions: TournamentActions,
    gutter: Dp,
) {
    var askUnlock by rememberSaveable { mutableStateOf(false) }
    val level = timer.currentLevelSegment?.level?.level ?: 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(PokerDimens.ElevationOverlay, PanelShape)
            .background(PokerColors.FeltGreen, PanelShape),
    ) {
        PanelHeader(timer, gutter) { actions.updateUi { it.closePanel() } }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = gutter, end = gutter, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnyTimeFields(setup, timer, actions)
            HorizontalDivider(color = PokerColors.FeltLine)
            if (ui.panelUnlocked) {
                UnlockedFields(setup, timer, actions)
            } else {
                LockedSummary(setup, timer)
                PokerButton(
                    text = stringResource(R.string.panel_unlock),
                    onClick = { askUnlock = true },
                    variant = PokerButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.panel_rebuild_note, level),
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.Chalk,
                )
            }
            PanelPresets(actions)
        }
        Handle()
    }
    if (askUnlock) {
        ConfirmSheet(
            title = stringResource(R.string.panel_unlock_title),
            body = stringResource(R.string.panel_unlock_body, level),
            dismissLabel = stringResource(R.string.panel_unlock_keep),
            confirmLabel = stringResource(R.string.panel_unlock_confirm),
            onDismiss = { askUnlock = false },
            onConfirm = {
                askUnlock = false
                actions.updateUi { it.unlock() }
            },
        )
    }
}

/** "SETUP · CLOCK STILL RUNNING" and the fold-up chevron. */
@Composable
private fun PanelHeader(timer: TimerUiState, gutter: Dp, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = gutter, end = gutter - 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PokerEyebrow(
            text = stringResource(if (timer.isRunning) R.string.panel_running else R.string.panel_paused),
            color = PokerColors.PokerGold,
            modifier = Modifier.weight(1f),
        )
        PokerIconButton(
            icon = PokerIcons.ChevronDown,
            contentDescription = stringResource(R.string.panel_close),
            onClick = onClose,
            tint = PokerColors.PokerGold,
            modifier = Modifier.rotate(HALF_TURN),
        )
    }
}

/** Players, rebuys-until, the BB ante, break length and note: safe to change with the clock running. */
@Composable
private fun AnyTimeFields(setup: TournamentConfigUiState, timer: TimerUiState, actions: TournamentActions) {
    PokerEyebrow(stringResource(R.string.panel_any_time))
    PlayersCard(setup.playerCount, hint = stringResource(R.string.setup_players_late_entry), framed = false) {
        actions.onSetupIntent(TournamentConfigIntent.UpdatePlayerCount(it))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        RebuysUntilSelect(timer, actions.onTimerIntent, Modifier.weight(1f))
        AnteSelect(
            fromLevel = timer.config.bigBlindAnteFromLevel,
            levelCount = timer.regularLevelCount,
            onChange = { actions.onTimerIntent(TimerIntent.UpdateBigBlindAnte(it)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (timer.config.breaks.enabled) BreakRow(timer.config.breaks, actions.onTimerIntent)
}

/** Money and blinds, read-only: "Buy-in · rebuy · add-on  $40 · $40 · $10" and so on. */
@Composable
private fun LockedSummary(setup: TournamentConfigUiState, timer: TimerUiState) {
    val formatter = rememberChipFormatter()
    val amounts = setup.money
    val config = timer.config
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PokerIcons.Lock, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(16.dp))
        PokerEyebrow(stringResource(R.string.panel_locked))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LockedValue(
            label = stringResource(R.string.panel_money_amounts),
            value = listOf(amounts.buyInCents, amounts.rebuyCents, amounts.addOnCents).joinToString(" · ") { money(it) },
            modifier = Modifier.weight(1f),
        )
        LockedValue(
            label = stringResource(R.string.panel_bounty_food),
            value = listOf(amounts.bountyCents, amounts.foodCents).joinToString(" · ") { money(it) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LockedValue(
            label = stringResource(R.string.panel_length_levels),
            value = stringResource(R.string.panel_hours_minutes, config.gameDurationHours, config.roundLengthMinutes),
            modifier = Modifier.weight(1f),
        )
        LockedValue(
            label = stringResource(R.string.panel_stack_chip),
            value = "${formatter.format(config.startingChips)} · ${formatter.format(config.smallestChip)}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LockedValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
        Text(value, style = PokerType.NumberM, color = PokerColors.CardWhite)
    }
}

/** After "Unlock to edit…": the money and the blinds, editable; blind changes keep the level. */
@Composable
private fun UnlockedFields(setup: TournamentConfigUiState, timer: TimerUiState, actions: TournamentActions) {
    val keepLevel: (TimerIntent) -> Unit = { actions.onTimerIntent(it.keepingLevel()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PokerEyebrow(stringResource(R.string.panel_unlocked), color = PokerColors.PokerGold, modifier = Modifier.weight(1f))
        PokerButton(
            text = stringResource(R.string.panel_lock_again),
            onClick = { actions.updateUi { it.lock() } },
            variant = PokerButtonVariant.Text,
            size = PokerButtonSize.Small,
        )
    }
    PanelMoneyGrid(setup.money, actions.onSetupIntent)
    TimingRow(timer.config, keepLevel)
    SetupNumberField(
        value = timer.config.startingChips,
        onCommit = { keepLevel(TimerIntent.UpdateStartingChips(it)) },
        label = stringResource(R.string.setup_starting_stack),
        modifier = Modifier.fillMaxWidth(),
    )
    SmallestChipPicker(value = timer.config.smallestChip, onPick = { keepLevel(TimerIntent.UpdateSmallestChip(it)) })
    timer.midGameProblem?.let { problem -> ProblemBox(problem, onFix = { keepLevel(TimerIntent.ApplyFix(it)) }) }
}

/** Save or share the night's setup; loading one waits for a new tournament (PP-032). */
@Composable
private fun PanelPresets(actions: TournamentActions) {
    HorizontalDivider(color = PokerColors.FeltLine)
    PresetsRow(midGame = true, onOpen = { actions.onPresetIntent(PresetsIntent.Open) }, framed = false)
}

/** The sheet's DarkGold handle, at its foot because it unfolds downward. */
@Composable
private fun Handle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(PokerColors.DarkGold, RoundedCornerShape(2.dp)),
        )
    }
}

private const val HALF_TURN = 180f
