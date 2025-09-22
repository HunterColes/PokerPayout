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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.settingsfeature.presentation.TimerDirection
import com.huntercoles.fatline.settingsfeature.presentation.TimerIntent
import com.huntercoles.fatline.settingsfeature.presentation.TimerUiState
import com.huntercoles.fatline.settingsfeature.presentation.TimerViewModel
import com.huntercoles.fatline.core.design.PokerColors

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
                        enabled = !uiState.isFinished
                    ) {
                        if (uiState.isRunning) {
                            // Pause icon using Text
                            Text(
                                text = "⏸",
                                style = MaterialTheme.typography.headlineLarge,
                                color = PokerColors.CardWhite
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

        // Blind Settings (moved above blind information)
        BlindSettingsCard(
            uiState = uiState,
            onIntent = onIntent
        )

        // Blind Information Section (renamed from "Current Blinds" to "Blinds")
        BlindInformationTile()

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
private fun BlindSettingsCard(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙️ Blind Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )
                Button(
                    onClick = { isExpanded = !isExpanded },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PokerColors.AccentGreen.copy(alpha = 0.3f),
                        contentColor = PokerColors.CardWhite
                    )
                ) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Duration Setting (Compact)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Duration:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.gameDurationMinutes.toString(),
                        onValueChange = { onIntent(TimerIntent.GameDurationChanged(it.toIntOrNull() ?: 180)) },
                        label = { Text("Min", color = PokerColors.CardWhite) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PokerColors.AccentGreen,
                            unfocusedBorderColor = PokerColors.CardWhite,
                            focusedTextColor = PokerColors.CardWhite,
                            unfocusedTextColor = PokerColors.CardWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timer Direction (Compact)
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