package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerUiState
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.core.utils.BlindLevel
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog
import java.text.NumberFormat
import java.util.Locale

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
fun TimerScreen(
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
        // Timer Display with integrated controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = PokerColors.FeltGreen)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp) // Fixed height for consistent centering
                    .clickable(onClick = { onIntent(TimerIntent.ToggleTimer) }),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !uiState.hasTimerStarted -> {
                        // Never started - show play button
                        IconButton(
                            onClick = { onIntent(TimerIntent.ToggleTimer) },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start timer",
                                tint = PokerColors.PokerGold,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    uiState.hasTimerStarted && !uiState.isRunning && !uiState.isFinished -> {
                        // Paused - show timer in more grayed out color with play button overlay
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.formattedTime,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = PokerColors.PokerGold.copy(alpha = PokerColors.PokerPausedAlpha)
                            )
                            
                            IconButton(
                                onClick = { onIntent(TimerIntent.ToggleTimer) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume timer",
                                    tint = PokerColors.PokerGold,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                    uiState.isRunning -> {
                        // Running - show timer with progress bar filling background
                        val targetColor = when {
                            uiState.isTimeCritical -> PokerColors.ErrorRed
                            uiState.isTimeLow -> PokerColors.PokerGold
                            else -> PokerColors.AccentGreen
                        }
                        
                        val progressColor by animateColorAsState(targetValue = targetColor, label = "progressColor")
                        
                        val animatedProgress by animateFloatAsState(targetValue = uiState.progress, label = "progress")
                        
                        val atFirstLevel = uiState.currentBlindLevelIndex <= 0
                        val atLastLevel = uiState.currentBlindLevelIndex >= uiState.blindLevels.size - 1
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = { onIntent(TimerIntent.ToggleTimer) }),
                            contentAlignment = Alignment.Center
                        ) {
                            // Progress fill background - fills from left to right
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedProgress)
                                    .background(progressColor.copy(alpha = PokerColors.PokerPausedBackgroundAlpha))
                                    .align(Alignment.CenterStart)
                            )
                            
                            // Seek back button
                            if (uiState.blindLevels.isNotEmpty()) {
                                IconButton(
                                    onClick = { onIntent(TimerIntent.PreviousBlindLevel) },
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 16.dp),
                                    enabled = !atFirstLevel
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous blind level",
                                        tint = if (atFirstLevel) PokerColors.PokerGold.copy(alpha = 0.3f) else PokerColors.PokerGold
                                    )
                                }
                            }
                            
                            Text(
                                text = uiState.formattedTime,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    uiState.isTimeLow -> PokerColors.ErrorRed
                                    uiState.isTimeCritical -> PokerColors.PokerGold
                                    else -> PokerColors.PokerGold
                                },
                                modifier = Modifier.clickable(
                                    onClick = { onIntent(TimerIntent.ToggleTimer) }
                                )
                            )
                            
                            // Seek forward button
                            if (uiState.blindLevels.isNotEmpty()) {
                                IconButton(
                                    onClick = { onIntent(TimerIntent.NextBlindLevel) },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 16.dp),
                                    enabled = !atLastLevel
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next blind level",
                                        tint = if (atLastLevel) PokerColors.PokerGold.copy(alpha = 0.3f) else PokerColors.PokerGold
                                    )
                                }
                            }
                        }
                    }
                    uiState.isFinished -> {
                        // Finished - show timer in gray with green background
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PokerColors.AccentGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.formattedTime,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = PokerColors.CardWhite.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }        // Blind Information Section (only show when blind config is collapsed and not paused)
        if (uiState.isBlindConfigCollapsed && (uiState.isRunning || uiState.isFinished || !uiState.hasTimerStarted)) {
            BlindInformationTile(
                uiState = uiState,
                onIntent = onIntent
            )
        }

        // Status Message
        if (uiState.isFinished || uiState.isOvertime) {
            val message = if (uiState.isOvertime && !uiState.isFinished) "⏱ Overtime!" else "🎉 Time's Up!"
            Text(
                text = message,
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
private fun BlindInformationTile(
    uiState: TimerUiState,
    onIntent: (TimerIntent) -> Unit
) {
    val formatter = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val levels = uiState.blindLevels
    val totalLevels = levels.size
    val currentIndex = uiState.currentBlindLevelIndex
    val listState = rememberLazyListState()
    val highlightColor = PokerColors.PokerGold.copy(alpha = 0.18f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            LaunchedEffect(totalLevels) {
                if (totalLevels > 0 && currentIndex in 0 until totalLevels) {
                    listState.scrollToItem(currentIndex)
                }
            }

            LaunchedEffect(currentIndex) {
                if (totalLevels > 0 && currentIndex in 0 until totalLevels) {
                    listState.animateScrollToItem(currentIndex)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = levels,
                    key = { it.level }
                ) { level ->
                    val isCurrent = level.level - 1 == currentIndex
                    BlindLevelRow(
                        level = level,
                        formatter = formatter,
                        isCurrent = isCurrent,
                        highlightColor = highlightColor
                    )
                }
            }

        }
    }
}

private fun formatChip(value: Int, formatter: NumberFormat): String = formatter.format(value)

private fun formatLevelOffset(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return "+${hours}:${remainingMinutes.toString().padStart(2, '0')}"
}

private fun formatCountdown(seconds: Int): String {
    if (seconds <= 0) return "--"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
    }
}

@Composable
private fun BlindLevelRow(
    level: BlindLevel,
    formatter: NumberFormat,
    isCurrent: Boolean,
    highlightColor: Color
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrent) highlightColor else Color.Transparent,
        label = "levelBackground"
    )
    val primaryColor by animateColorAsState(
        targetValue = if (isCurrent) PokerColors.PokerGold else PokerColors.CardWhite,
        label = "levelPrimary"
    )
    val secondaryColor by animateColorAsState(
        targetValue = if (isCurrent) PokerColors.PokerGold else PokerColors.CardWhite.copy(alpha = 0.8f),
        label = "levelSecondary"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Level ${level.level}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )

            Text(
                text = formatLevelOffset(level.roundStartMinute),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatChip(level.smallBlind, formatter)} / ${formatChip(level.bigBlind, formatter)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Text(
                text = if (level.ante > 0) "Ante ${formatChip(level.ante, formatter)}" else "No ante",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
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