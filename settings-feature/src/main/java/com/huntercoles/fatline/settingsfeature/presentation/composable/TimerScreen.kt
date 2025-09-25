package com.huntercoles.fatline.settingsfeature.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.settingsfeature.presentation.TimerDirection
import com.huntercoles.fatline.settingsfeature.presentation.TimerIntent
import com.huntercoles.fatline.settingsfeature.presentation.TimerUiState
import com.huntercoles.fatline.settingsfeature.presentation.TimerViewModel
import com.huntercoles.fatline.core.design.PokerColors

/**
 * Validates duration input to only allow digits and reasonable values
 */
private fun isValidDurationInput(text: String): Boolean {
    if (text.isEmpty()) return true
    return text.all { it.isDigit() } && text.length <= 4 // Max 9999 minutes
}

@Composable
fun TimerRoute(viewModel: TimerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TimerScreen(
        uiState = uiState,
        onIntent = viewModel::acceptIntent,
    )
}

@Composable
internal fun TimerScreen(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = "⏰ Play Timer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Blind Configuration Section
        BlindConfigurationSection(
            uiState = uiState,
            onIntent = onIntent,
            isExpanded = !uiState.isBlindConfigCollapsed,
            onExpandedChange = { expanded -> 
                onIntent(TimerIntent.ToggleBlindConfigCollapsed(!expanded))
            }
        )

        // Timer Display with integrated controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = PokerColors.FeltGreen)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start/Pause Button
                    IconButton(
                        onClick = { onIntent(TimerIntent.ToggleTimer) },
                        enabled = !uiState.isFinished,
                        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    ) {
                        if (uiState.isRunning) {
                            // Pause icon using Text
                            Text(
                                text = "⏸",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PokerColors.PokerGold,
                                fontSize = 32.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                tint = PokerColors.CardWhite,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Timer Display
                    Text(
                        text = uiState.formattedTime,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            uiState.isTimeLow -> PokerColors.ErrorRed
                            uiState.isTimeCritical -> PokerColors.PokerGold
                            else -> PokerColors.PokerGold
                        }
                    )

                    // Reset Button
                    IconButton(
                        onClick = { onIntent(TimerIntent.ResetTimer) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = PokerColors.CardWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = uiState.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = when {
                        uiState.isTimeCritical -> PokerColors.ErrorRed
                        uiState.isTimeLow -> PokerColors.PokerGold
                        else -> PokerColors.AccentGreen
                    },
                    trackColor = PokerColors.DarkGreen
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${String.format("%.0f", uiState.progress * 100)}% Complete",
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.CardWhite
                )
            }
        }

        // Blind Information Section (only show when blind config is collapsed)
        if (uiState.isBlindConfigCollapsed) {
            BlindInformationTile()
        }

        // Status Message
        if (uiState.isFinished) {
            Text(
                text = "🎉 Time's Up!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BlindInformationTile() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🃏 Blinds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Small Blind",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite
                    )
                    Text(
                        text = "$25",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Big Blind",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite
                    )
                    Text(
                        text = "$50",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ante",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite
                    )
                    Text(
                        text = "$5",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Next Level: 15:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.CardWhite
                )
                Text(
                    text = "Level 3 of 12",
                    style = MaterialTheme.typography.bodySmall,
                    color = PokerColors.CardWhite
                )
            }
        }
    }
}

@Composable
private fun BlindConfigurationSection(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with collapse arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎯 Blind Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock icon when timer has started
                    if (uiState.hasTimerStarted) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Configuration Locked",
                            tint = PokerColors.PokerGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { 
                            onExpandedChange(!isExpanded)
                        }
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = PokerColors.PokerGold
                        )
                    }
                }
            }
            
            // Collapsible content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val focusManager = LocalFocusManager.current
                    
                    // Duration Field (hours) - simplified to use same component
                    BlindConfigIntField(
                        value = uiState.gameDurationHours,
                        label = "Duration (Hours)",
                        onValueChange = { hours -> 
                            val cappedHours = minOf(hours, 24).coerceAtLeast(1)
                            onIntent(TimerIntent.GameDurationHoursChanged(cappedHours))
                        },
                        isLocked = uiState.hasTimerStarted,
                        focusManager = focusManager
                    )

                    // Smallest Chip
                    BlindConfigIntField(
                        value = uiState.blindConfiguration.smallestChip,
                        label = "Smallest Chip",
                        onValueChange = { onIntent(TimerIntent.UpdateSmallestChip(it)) },
                        isLocked = uiState.hasTimerStarted,
                        focusManager = focusManager
                    )

                    // Starting Chips  
                    BlindConfigIntField(
                        value = uiState.blindConfiguration.startingChips,
                        label = "Starting Chips",
                        onValueChange = { onIntent(TimerIntent.UpdateStartingChips(it)) },
                        isLocked = uiState.hasTimerStarted,
                        focusManager = focusManager
                    )

                    // Round Length
                    BlindConfigIntField(
                        value = uiState.blindConfiguration.roundLengthMinutes,
                        label = "Round Length (Min)",
                        onValueChange = { onIntent(TimerIntent.UpdateRoundLength(it)) },
                        isLocked = uiState.hasTimerStarted,
                        focusManager = focusManager
                    )

                    // Timer Direction (Mode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mode:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PokerColors.CardWhite,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            modifier = Modifier.weight(2f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = uiState.timerDirection == TimerDirection.COUNTDOWN,
                                    onClick = { onIntent(TimerIntent.TimerDirectionChanged(TimerDirection.COUNTDOWN)) },
                                    enabled = !uiState.isRunning,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = PokerColors.PokerGold,
                                        unselectedColor = PokerColors.CardWhite
                                    )
                                )
                                Text("Countdown", style = MaterialTheme.typography.bodySmall, color = PokerColors.CardWhite)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = uiState.timerDirection == TimerDirection.COUNTUP,
                                    onClick = { onIntent(TimerIntent.TimerDirectionChanged(TimerDirection.COUNTUP)) },
                                    enabled = !uiState.isRunning,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = PokerColors.PokerGold,
                                        unselectedColor = PokerColors.CardWhite
                                    )
                                )
                                Text("Count Up", style = MaterialTheme.typography.bodySmall, color = PokerColors.CardWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Validates integer input for blind configuration values
 */
private fun isValidBlindConfigInput(text: String): Boolean {
    if (text.isEmpty()) return true
    return text.all { it.isDigit() } && text.length <= 9 // Max 999,999,999
}

@Composable
private fun BlindConfigIntField(
    value: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    isLocked: Boolean,
    focusManager: FocusManager
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            if (isValidBlindConfigInput(newValue)) {
                textValue = newValue
                val intValue = newValue.toIntOrNull() ?: 0
                val cappedValue = minOf(intValue, 999_999_999).coerceAtLeast(0)
                onValueChange(cappedValue)
            }
        },
        label = { Text(label, color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite) },
        enabled = !isLocked,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
            unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
            focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
            disabledTextColor = PokerColors.PokerGold,
            cursorColor = PokerColors.PokerGold,
            selectionColors = TextSelectionColors(
                handleColor = PokerColors.PokerGold,
                backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
            )
        ),
        modifier = Modifier.fillMaxWidth()
    )
}