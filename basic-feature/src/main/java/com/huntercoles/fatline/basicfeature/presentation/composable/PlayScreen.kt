package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.basicfeature.presentation.PlayConfigIntent
import com.huntercoles.fatline.basicfeature.presentation.PlayConfigUiState
import com.huntercoles.fatline.basicfeature.presentation.PlayConfigViewModel
import com.huntercoles.fatline.core.design.PokerColors
import com.huntercoles.fatline.core.design.PokerDialog
import com.huntercoles.fatline.settingsfeature.presentation.composable.TimerScreen
import com.huntercoles.fatline.settingsfeature.presentation.TimerIntent
import com.huntercoles.fatline.settingsfeature.presentation.TimerUiState
import com.huntercoles.fatline.settingsfeature.presentation.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    calculatorViewModel: PlayConfigViewModel = hiltViewModel(),
    timerViewModel: TimerViewModel = hiltViewModel()
) {
    val calculatorUiState by calculatorViewModel.uiState.collectAsStateWithLifecycle()
    val timerUiState by timerViewModel.uiState.collectAsStateWithLifecycle()

    PlayContent(
        calculatorUiState = calculatorUiState,
        timerUiState = timerUiState,
        onCalculatorIntent = calculatorViewModel::acceptIntent,
        onTimerIntent = timerViewModel::acceptIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayContent(
    calculatorUiState: PlayConfigUiState,
    timerUiState: TimerUiState,
    onCalculatorIntent: (PlayConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Clear focus immediately when this composable is disposed (tab switch)
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title with Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🃏 Poker Payout Calculator",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(1f)
            )

            // Green circular background with yellow refresh button
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen)
            ) {
                IconButton(
                    onClick = { onCalculatorIntent(PlayConfigIntent.ShowResetDialog) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset All Data",
                        tint = PokerColors.PokerGold,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(-1f, 1f) // Invert horizontally
                    )
                }
            }
        }

        // Reset Confirmation Dialog
        if (calculatorUiState.showResetDialog) {
            PokerDialog(
                onDismissRequest = { onCalculatorIntent(PlayConfigIntent.HideResetDialog) }
            ) {
                Text(
                    text = "Reset tournament?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PokerColors.PokerGold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PokerColors.FeltGreen,
                    border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "This will reset all tournament settings and timer data to defaults.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(onClick = { onCalculatorIntent(PlayConfigIntent.HideResetDialog) }) {
                        Text(
                            text = "Cancel",
                            color = PokerColors.CardWhite
                        )
                    }

                    TextButton(onClick = { 
                        onCalculatorIntent(PlayConfigIntent.ConfirmReset)
                        onTimerIntent(TimerIntent.ResetTimer)
                    }) {
                        Text(
                            text = "Reset",
                            color = PokerColors.PokerGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Configuration Section (Collapsible)
        TournamentConfigurationCard(
            uiState = calculatorUiState,
            onIntent = onCalculatorIntent,
            onTimerIntent = onTimerIntent,
            isExpanded = calculatorUiState.isConfigExpanded,
            onExpandedChange = { onCalculatorIntent(PlayConfigIntent.ToggleConfigExpanded(it)) }
        )

        // Timer Section (from Timer screen)
        TimerScreen(
            uiState = timerUiState,
            onIntent = onTimerIntent
        )
    }
}


@Composable
fun PlayerCountSlider(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    isLocked: Boolean = false
) {
    Column {
        Text(
            text = "Number of Players: $playerCount",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = playerCount.toFloat(),
            onValueChange = { if (!isLocked) onPlayerCountChange(it.toInt()) },
            valueRange = 3f..30f,
            steps = 26,
            enabled = !isLocked,
            colors = SliderDefaults.colors(
                thumbColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.PokerGold,
                activeTrackColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
                inactiveTrackColor = PokerColors.DarkGreen,
                disabledThumbColor = PokerColors.CardWhite.copy(alpha = 0.5f),
                disabledActiveTrackColor = PokerColors.CardWhite.copy(alpha = 0.5f),
                disabledInactiveTrackColor = PokerColors.DarkGreen
            )
        )
    }
}

@Composable
fun TournamentConfigurationCard(
    uiState: PlayConfigUiState,
    onIntent: (PlayConfigIntent) -> Unit,
    onTimerIntent: (TimerIntent) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
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
                    text = "💵 Tournament Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock icon when tournament is locked
                    if (uiState.isTournamentLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Tournament Locked",
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
                    // Pool Configuration (now includes Player panel, Player slider, and Blinds panel)
                    PoolConfigurationSection(
                        buyIn = uiState.tournamentConfig.buyIn,
                        foodPerPlayer = uiState.tournamentConfig.foodPerPlayer,
                        bountyPerPlayer = uiState.tournamentConfig.bountyPerPlayer,
                        rebuyPerPlayer = uiState.tournamentConfig.rebuyPerPlayer,
                        addOnPerPlayer = uiState.tournamentConfig.addOnPerPlayer,
                        onBuyInChange = { onIntent(PlayConfigIntent.UpdateBuyIn(it)) },
                        onFoodChange = { onIntent(PlayConfigIntent.UpdateFoodPerPlayer(it)) },
                        onBountyChange = { onIntent(PlayConfigIntent.UpdateBountyPerPlayer(it)) },
                        onRebuyChange = { onIntent(PlayConfigIntent.UpdateRebuyAmount(it)) },
                        onAddOnChange = { onIntent(PlayConfigIntent.UpdateAddOnAmount(it)) },
                        playerCount = uiState.tournamentConfig.numPlayers,
                        onPlayerCountChange = { count ->
                            onIntent(PlayConfigIntent.UpdatePlayerCount(count))
                        },
                        gameDurationHours = uiState.gameDurationHours,
                        roundLengthMinutes = uiState.roundLengthMinutes,
                        smallestChip = uiState.smallestChip,
                        startingChips = uiState.startingChips,
                        onGameDurationHoursChange = { hours ->
                            onIntent(PlayConfigIntent.UpdateGameDurationHours(hours))
                            onTimerIntent(TimerIntent.GameDurationHoursChanged(hours))
                        },
                        onRoundLengthChange = { minutes ->
                            onIntent(PlayConfigIntent.UpdateRoundLength(minutes))
                            onTimerIntent(TimerIntent.UpdateRoundLength(minutes))
                        },
                        onSmallestChipChange = { chip ->
                            onIntent(PlayConfigIntent.UpdateSmallestChip(chip))
                            onTimerIntent(TimerIntent.UpdateSmallestChip(chip))
                        },
                        onStartingChipsChange = { chips ->
                            onIntent(PlayConfigIntent.UpdateStartingChips(chips))
                            onTimerIntent(TimerIntent.UpdateStartingChips(chips))
                        },
                        isLocked = uiState.isTournamentLocked
                    )
                }
            }
        }
    }
}