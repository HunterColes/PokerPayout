package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerNumberField
import com.huntercoles.pokerpayout.core.design.components.PokerTextFieldDefaults
import com.huntercoles.pokerpayout.core.design.components.leaveOnHardwareEnter
import com.huntercoles.pokerpayout.core.utils.BlindSetupFix
import com.huntercoles.pokerpayout.tournament.domain.clock.BreakSettings
import com.huntercoles.pokerpayout.tournament.presentation.BlindConfiguration
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TournamentConfigIntent

private const val MAX_DURATION_HOURS = 24
private const val MAX_BREAK_MINUTES = 120
private const val MAX_ANTE_LEVEL = 99

/**
 * Tournament -> Blinds: duration, rounds, chips, breaks and ante, with a live verdict on whether the
 * setup works and one-tap fixes when it doesn't (PP-020, PP-026, PP-051).
 */
@Composable
fun BlindsConfigPanel(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val config = uiState.config
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TimingRow(config, onIntent, isLocked)
        ChipsRow(config, onIntent, isLocked)
        BreaksSection(config.breaks, onIntent, isLocked)
        AnteRow(
            fromLevel = config.bigBlindAnteFromLevel,
            onChange = { onIntent(TimerIntent.UpdateBigBlindAnte(it)) },
            isLocked = isLocked
        )
        SetupVerdict(uiState = uiState, onFix = { onIntent(TimerIntent.ApplyFix(it)) }, isLocked = isLocked)
    }
}

@Composable
private fun TimingRow(config: BlindConfiguration, onIntent: (TimerIntent) -> Unit, isLocked: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PokerNumberField(
            value = config.gameDurationHours,
            onValueChange = { onIntent(TimerIntent.GameDurationHoursChanged(it)) },
            label = "Duration (Hours)",
            isLocked = isLocked,
            minValue = 1,
            maxValue = MAX_DURATION_HOURS,
            modifier = Modifier.weight(1f)
        )
        PokerNumberField(
            value = config.roundLengthMinutes,
            onValueChange = { onIntent(TimerIntent.UpdateRoundLength(it)) },
            label = "Round Length (Min)",
            isLocked = isLocked,
            minValue = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChipsRow(config: BlindConfiguration, onIntent: (TimerIntent) -> Unit, isLocked: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallestChipPicker(
            value = config.smallestChip,
            onPick = { onIntent(TimerIntent.UpdateSmallestChip(it)) },
            isLocked = isLocked,
            modifier = Modifier.weight(1f)
        )
        PokerNumberField(
            value = config.startingChips,
            onValueChange = { onIntent(TimerIntent.UpdateStartingChips(it)) },
            label = "Starting Chips",
            isLocked = isLocked,
            minValue = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Breaks every N levels (or off), their length, and a note shown on the clock during each one. */
@Composable
private fun BreaksSection(breaks: BreakSettings, onIntent: (TimerIntent) -> Unit, isLocked: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BreakIntervalPicker(
            everyLevels = breaks.everyLevels,
            onPick = { onIntent(TimerIntent.UpdateBreakEvery(it)) },
            isLocked = isLocked,
            modifier = Modifier.weight(1f)
        )
        if (breaks.enabled) {
            PokerNumberField(
                value = breaks.lengthMinutes,
                onValueChange = { onIntent(TimerIntent.UpdateBreakLength(it)) },
                label = "Break (Min)",
                isLocked = isLocked,
                minValue = 1,
                maxValue = MAX_BREAK_MINUTES,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
    if (breaks.enabled) {
        BreakMessageField(
            message = breaks.message,
            onChange = { onIntent(TimerIntent.UpdateBreakMessage(it)) },
            isLocked = isLocked
        )
    }
}

/** The note shown on the clock during each break. */
@Composable
internal fun BreakMessageField(message: String, onChange: (String) -> Unit, isLocked: Boolean) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(message) }
    var isFocused by remember { mutableStateOf(false) }
    // Outside changes (a reset) show up once the user isn't typing. While typing, the saved value
    // trails the field by a frame or two, and copying it back undid fast keystrokes: "Last rebuy"
    // came out as "Last ebuyr" on the device tour.
    LaunchedEffect(message) { if (!isFocused && message != text) text = message }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.take(BreakSettings.MAX_MESSAGE_LENGTH)
            onChange(text)
        },
        enabled = !isLocked,
        singleLine = true,
        label = { Text("Break note", fontSize = 13.sp) },
        placeholder = { Text("e.g. Last rebuy", color = PokerColors.CardWhite.copy(alpha = 0.5f)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = PokerTextFieldDefaults.colors(isLocked = isLocked),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .leaveOnHardwareEnter { focusManager.clearFocus(force = true) }
    )
}

@Composable
private fun AnteRow(fromLevel: Int, onChange: (Int) -> Unit, isLocked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Switch(
            checked = fromLevel > 0,
            onCheckedChange = { onChange(if (it) 1 else 0) },
            enabled = !isLocked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PokerColors.PokerGold,
                checkedTrackColor = PokerColors.AccentGreen,
                uncheckedThumbColor = PokerColors.CardWhite.copy(alpha = 0.8f),
                uncheckedTrackColor = PokerColors.FeltGreen
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("Big-blind ante", color = PokerColors.CardWhite, fontWeight = FontWeight.SemiBold)
            Text(
                text = "The big blind antes one big blind for the table",
                color = PokerColors.CardWhite.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (fromLevel > 0) {
            PokerNumberField(
                value = fromLevel,
                onValueChange = onChange,
                label = "From level",
                isLocked = isLocked,
                minValue = 1,
                maxValue = MAX_ANTE_LEVEL,
                modifier = Modifier.width(112.dp)
            )
        }
    }
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
    else -> null
}
