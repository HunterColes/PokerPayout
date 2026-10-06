package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.presentation.BlindConfiguration
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent

private const val MAX_DURATION_HOURS = 24
private const val MAX_BREAK_MINUTES = 120

/**
 * The setup's blinds (S1): game length, level length, starting stack, breaks, the smallest chip as a
 * row of real chips, break length and note, the big-blind ante, and the live verdict on whether it
 * all works, with one-tap fixes when it doesn't (PP-020, PP-026, PP-051).
 */
@Composable
fun BlindsConfigPanel(uiState: TimerUiState, onIntent: (TimerIntent) -> Unit, modifier: Modifier = Modifier) {
    val config = uiState.config
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TimingRow(config, onIntent)
        StackRow(config, onIntent)
        SmallestChipPicker(value = config.smallestChip, onPick = { onIntent(TimerIntent.UpdateSmallestChip(it)) })
        if (config.breaks.enabled) BreakRow(config.breaks, onIntent)
        AnteRow(
            fromLevel = config.bigBlindAnteFromLevel,
            levelCount = uiState.regularLevelCount,
            onChange = { onIntent(TimerIntent.UpdateBigBlindAnte(it)) },
        )
        SetupVerdict(uiState = uiState, onFix = { onIntent(TimerIntent.ApplyFix(it)) })
    }
}

/** Game length and level length: the money and blind fields' "Levels" row. */
@Composable
internal fun TimingRow(config: BlindConfiguration, onIntent: (TimerIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupNumberField(
            value = config.gameDurationHours,
            onCommit = { onIntent(TimerIntent.GameDurationHoursChanged(it)) },
            label = stringResource(R.string.setup_game_length),
            suffix = stringResource(R.string.setup_hours),
            range = 1..MAX_DURATION_HOURS,
            modifier = Modifier.weight(1f),
        )
        SetupNumberField(
            value = config.roundLengthMinutes,
            onCommit = { onIntent(TimerIntent.UpdateRoundLength(it)) },
            label = stringResource(R.string.setup_level_length),
            suffix = stringResource(R.string.setup_minutes),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StackRow(config: BlindConfiguration, onIntent: (TimerIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        SetupNumberField(
            value = config.startingChips,
            onCommit = { onIntent(TimerIntent.UpdateStartingChips(it)) },
            label = stringResource(R.string.setup_starting_stack),
            modifier = Modifier.weight(1f),
        )
        BreakIntervalPicker(
            everyLevels = config.breaks.everyLevels,
            onPick = { onIntent(TimerIntent.UpdateBreakEvery(it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Break length and the note shown on the clock during each break. Both stay editable mid-game. */
@Composable
internal fun BreakRow(breaks: BreakSettings, onIntent: (TimerIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        SetupNumberField(
            value = breaks.lengthMinutes,
            onCommit = { onIntent(TimerIntent.UpdateBreakLength(it)) },
            label = stringResource(R.string.setup_break_length),
            suffix = stringResource(R.string.setup_minutes),
            range = 1..MAX_BREAK_MINUTES,
            modifier = Modifier.weight(1f),
        )
        Column(Modifier.weight(1f)) {
            BreakMessageField(
                message = breaks.message,
                onChange = { onIntent(TimerIntent.UpdateBreakMessage(it)) },
                isLocked = false,
            )
        }
    }
}

/** The note shown on the clock during each break. */
@Composable
internal fun BreakMessageField(message: String, onChange: (String) -> Unit, isLocked: Boolean) {
    val focusManager = LocalFocusManager.current
    val interactions = remember { MutableInteractionSource() }
    val focusedLook by interactions.collectIsFocusedAsState()
    var text by remember { mutableStateOf(message) }
    var isFocused by remember { mutableStateOf(false) }
    // Outside changes (a reset) show up once the user isn't typing. While typing, the saved value
    // trails the field by a frame or two, and copying it back undid fast keystrokes: "Last rebuy"
    // came out as "Last ebuyr" on the device tour.
    LaunchedEffect(message) { if (!isFocused && message != text) text = message }
    val decor = FieldDecor(
        label = stringResource(R.string.setup_break_note),
        placeholder = stringResource(R.string.setup_break_note_hint),
    )
    BasicTextField(
        value = text,
        onValueChange = {
            text = it.take(BreakSettings.MAX_MESSAGE_LENGTH)
            onChange(text)
        },
        enabled = !isLocked,
        singleLine = true,
        textStyle = SetupFieldStyle.Text,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        interactionSource = interactions,
        cursorBrush = SolidColor(PokerColors.PokerGold),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) },
        decorationBox = { inner -> SetupFieldDecoration(decor, focusedLook, isEmpty = text.isEmpty(), inner = inner) },
    )
}

/** The big-blind ante: on or off, and from which level (one big blind, for the table). */
@Composable
internal fun AnteRow(fromLevel: Int, levelCount: Int, onChange: (Int) -> Unit) {
    val on = fromLevel > 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = on, role = Role.Switch, onValueChange = { onChange(if (it) 1 else 0) }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.setup_ante),
                    color = PokerColors.CardWhite,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (on) stringResource(R.string.setup_ante_on, fromLevel) else stringResource(R.string.setup_ante_off),
                    color = PokerColors.Chalk,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PokerColors.FeltDeep,
                    checkedTrackColor = PokerColors.PokerGold,
                    uncheckedThumbColor = PokerColors.Chalk,
                    uncheckedTrackColor = PokerColors.FeltDeep,
                    uncheckedBorderColor = PokerColors.FeltEdge,
                ),
            )
        }
        if (on) AnteSelect(fromLevel, levelCount, onChange, Modifier.fillMaxWidth())
    }
}

/** "Off", "From L1" … "From L9": the ante's first level, as a menu. */
@Composable
internal fun AnteSelect(fromLevel: Int, levelCount: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val last = maxOf(levelCount, fromLevel, 1)
    SetupSelectField(
        label = stringResource(R.string.setup_ante_from),
        options = listOf(0) + (1..last),
        selected = fromLevel,
        optionText = { level ->
            if (level == 0) {
                resources.getString(R.string.setup_ante_none)
            } else {
                resources.getString(R.string.setup_ante_from_level, level)
            }
        },
        onPick = onChange,
        modifier = modifier,
    )
}

/**
 * Keeps the Tournament settings' copy of the blind fields in step with the clock's, so Reset and its
 * "already default?" check see the same values (TournamentConfigViewModel still stores them).
 */
fun TimerIntent.toConfigIntent(): TournamentConfigIntent? = when (this) {
    is TimerIntent.GameDurationHoursChanged ->
        TournamentConfigIntent.UpdateGameDurationHours(hours.coerceIn(1, MAX_DURATION_HOURS))
    is TimerIntent.UpdateRoundLength -> TournamentConfigIntent.UpdateRoundLength(minutes)
    is TimerIntent.UpdateSmallestChip -> TournamentConfigIntent.UpdateSmallestChip(value)
    is TimerIntent.UpdateStartingChips -> TournamentConfigIntent.UpdateStartingChips(value)
    is TimerIntent.ApplyFix -> when (val fix = fix) {
        is BlindSetupFix.UseRoundLength -> TournamentConfigIntent.UpdateRoundLength(fix.minutes)
        is BlindSetupFix.UseStartingChips -> TournamentConfigIntent.UpdateStartingChips(fix.chips)
    }
    // A mid-game change is mirrored once the clock has applied it (it may be refused).
    else -> null
}

/** A blind setup change made mid-game, through "Unlock to edit…": the clock keeps its level. */
internal fun TimerIntent.keepingLevel(): TimerIntent = when (this) {
    is TimerIntent.GameDurationHoursChanged,
    is TimerIntent.UpdateRoundLength,
    is TimerIntent.UpdateSmallestChip,
    is TimerIntent.UpdateStartingChips,
    is TimerIntent.ApplyFix,
    -> TimerIntent.KeepingLevel(this)
    else -> this
}
