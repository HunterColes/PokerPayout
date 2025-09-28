package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.basicfeature.presentation.CalculatorIntent
import com.huntercoles.fatline.basicfeature.presentation.CalculatorUiState
import com.huntercoles.fatline.basicfeature.presentation.CalculatorViewModel
import com.huntercoles.fatline.core.design.PokerColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalculatorContent(
        uiState = uiState,
        onIntent = viewModel::acceptIntent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorContent(
    uiState: CalculatorUiState,
    onIntent: (CalculatorIntent) -> Unit
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
                    onClick = { onIntent(CalculatorIntent.ShowResetDialog) },
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
        if (uiState.showResetDialog) {
            AlertDialog(
                onDismissRequest = { onIntent(CalculatorIntent.HideResetDialog) },
                containerColor = PokerColors.DarkGreen,
                title = {
                    Text(
                        text = "Reset?",
                        color = PokerColors.PokerGold,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will reset all tournament settings and timer data to defaults.",
                        color = PokerColors.CardWhite
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onIntent(CalculatorIntent.HideResetDialog) }
                    ) {
                        Text(
                            text = "No",
                            color = PokerColors.CardWhite
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onIntent(CalculatorIntent.ConfirmReset) }
                    ) {
                        Text(
                            text = "Yes",
                            color = PokerColors.PokerGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Configuration Section (Collapsible)
        TournamentConfigurationCard(
            uiState = uiState,
            onIntent = onIntent,
            isExpanded = uiState.isConfigExpanded,
            onExpandedChange = { onIntent(CalculatorIntent.ToggleConfigExpanded(it)) }
        )

        // Leaderboard/Payout Structure Section (only show when config is collapsed)
        if (!uiState.isConfigExpanded) {
            LeaderboardCard(
                payouts = uiState.payouts,
                leaderboardNames = uiState.leaderboardNames,
                onIntent = onIntent,
                isTournamentLocked = uiState.isTournamentLocked,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
    uiState: CalculatorUiState,
    onIntent: (CalculatorIntent) -> Unit,
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
                    // Player Count Slider
                    PlayerCountSlider(
                        playerCount = uiState.tournamentConfig.numPlayers,
                        onPlayerCountChange = { count ->
                            onIntent(CalculatorIntent.UpdatePlayerCount(count))
                        },
                        isLocked = uiState.isTournamentLocked
                    )

                    // Pool Configuration
                    PoolConfigurationSection(
                        buyIn = uiState.tournamentConfig.buyIn,
                        foodPerPlayer = uiState.tournamentConfig.foodPerPlayer,
                        bountyPerPlayer = uiState.tournamentConfig.bountyPerPlayer,
                        rebuyPerPlayer = uiState.tournamentConfig.rebuyPerPlayer,
                        addOnPerPlayer = uiState.tournamentConfig.addOnPerPlayer,
                        onBuyInChange = { onIntent(CalculatorIntent.UpdateBuyIn(it)) },
                        onFoodChange = { onIntent(CalculatorIntent.UpdateFoodPerPlayer(it)) },
                        onBountyChange = { onIntent(CalculatorIntent.UpdateBountyPerPlayer(it)) },
                        onRebuyChange = { onIntent(CalculatorIntent.UpdateRebuyAmount(it)) },
                        onAddOnChange = { onIntent(CalculatorIntent.UpdateAddOnAmount(it)) },
                        isLocked = uiState.isTournamentLocked
                    )

                    // Pool Summary
                    PoolSummarySection(uiState.tournamentConfig)
                }
            }
        }
    }
}

@Composable
fun LeaderboardCard(
    payouts: List<com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition>,
    leaderboardNames: Map<Int, String>,
    onIntent: (CalculatorIntent) -> Unit,
    isTournamentLocked: Boolean,
    modifier: Modifier = Modifier
) {
    var showWeightsDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with edit weights button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🏆 Tournament Leaderboard",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold
                    )
                    
                    if (payouts.isNotEmpty()) {
                        val payingPositions = payouts.count { it.isPaying }
                        Text(
                            text = "$payingPositions paying positions",
                            fontSize = 12.sp,
                            color = PokerColors.AccentGreen
                        )
                    }
                }
                
                IconButton(
                    onClick = { showWeightsDialog = true },
                    enabled = !isTournamentLocked
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Weights",
                        tint = if (isTournamentLocked) PokerColors.CardWhite.copy(alpha = 0.5f) 
                              else PokerColors.AccentGreen
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Leaderboard positions (scrollable)
            if (payouts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Configure tournament settings to see payouts",
                        color = PokerColors.CardWhite,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    payouts.forEach { payout ->
                        LeaderboardPosition(
                            payout = payout,
                            playerName = leaderboardNames[payout.position]
                        )
                    }
                }
            }
        }
    }
    
    // Weights editor dialog
    if (showWeightsDialog && !isTournamentLocked) {
        WeightsEditorDialog(
            currentWeights = payouts.map { it.weight },
            onWeightsChanged = { newWeights ->
                onIntent(CalculatorIntent.UpdateWeights(newWeights))
            },
            onDismiss = { showWeightsDialog = false }
        )
    }
}

@Composable
fun LeaderboardPosition(
    payout: com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition,
    playerName: String?
) {
    val trophy = when (payout.position) {
        1 -> "🥇"
        2 -> "🥈" 
        3 -> "🥉"
        else -> "" // No medal for positions 4+
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (payout.position <= 3) PokerColors.AccentGreen.copy(alpha = 0.3f) 
                          else PokerColors.DarkGreen
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position and player placeholder
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trophy.isNotEmpty()) {
                    Text(
                        text = trophy,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                
                Column {
                    Text(
                        text = "${payout.position}${payout.positionSuffix} Place",
                        color = PokerColors.CardWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    
                    Text(
                        text = playerName?.takeIf { it.isNotBlank() } ?: "-----",
                        color = PokerColors.CardWhite.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Payout amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = payout.formattedPayout,
                    color = PokerColors.PokerGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Text(
                    text = payout.formattedPercentage,
                    color = PokerColors.CardWhite.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
        }
    }
}