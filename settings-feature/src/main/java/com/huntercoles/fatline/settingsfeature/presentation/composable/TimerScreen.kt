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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "⏰ Blind Timer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        // Game Duration Setting
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Game Duration (minutes)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.gameDurationMinutes.toString(),
                    onValueChange = { onIntent(TimerIntent.GameDurationChanged(it.toIntOrNull() ?: 180)) },
                    label = { Text("Duration", color = PokerColors.CardWhite) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PokerColors.AccentGreen,
                        unfocusedBorderColor = PokerColors.CardWhite,
                        focusedTextColor = PokerColors.CardWhite,
                        unfocusedTextColor = PokerColors.CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Timer Mode Selection
                Text(
                    text = "Timer Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.CardWhite
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.timerDirection == TimerDirection.COUNTDOWN,
                            onClick = { onIntent(TimerIntent.TimerDirectionChanged(TimerDirection.COUNTDOWN)) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PokerColors.PokerGold,
                                unselectedColor = PokerColors.CardWhite
                            )
                        )
                        Text("Countdown", style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.timerDirection == TimerDirection.COUNTUP,
                            onClick = { onIntent(TimerIntent.TimerDirectionChanged(TimerDirection.COUNTUP)) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PokerColors.PokerGold,
                                unselectedColor = PokerColors.CardWhite
                            )
                        )
                        Text("Count Up", style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = PokerColors.FeltGreen
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.formattedTime,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        uiState.isTimeLow -> PokerColors.ErrorRed
                        uiState.isTimeCritical -> PokerColors.PokerGold
                        else -> PokerColors.PokerGold
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = uiState.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    color = when {
                        uiState.isTimeCritical -> PokerColors.ErrorRed
                        uiState.isTimeLow -> PokerColors.PokerGold
                        else -> PokerColors.AccentGreen
                    },
                    trackColor = PokerColors.DarkGreen
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${String.format("%.1f", uiState.progress * 100)}% Complete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { onIntent(TimerIntent.ToggleTimer) },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isFinished,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PokerColors.AccentGreen,
                    contentColor = PokerColors.DarkGreen
                )
            ) {
                Text(if (uiState.isRunning) "⏸️ Pause" else "▶️ Start")
            }

            Button(
                onClick = { onIntent(TimerIntent.ResetTimer) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PokerColors.LightGreen,
                    contentColor = PokerColors.CardWhite
                )
            ) {
                Text("🔄 Reset")
            }
        }

        // Status Message
        if (uiState.isFinished) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "🎉 Time's Up!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}