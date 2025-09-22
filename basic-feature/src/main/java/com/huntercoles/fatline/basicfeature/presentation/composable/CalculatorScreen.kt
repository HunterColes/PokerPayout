package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var isConfigExpanded by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "🃏 Poker Payout Calculator",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Configuration Section (Collapsible)
        TournamentConfigurationCard(
            uiState = uiState,
            onIntent = onIntent,
            isExpanded = isConfigExpanded,
            onExpandedChange = { isConfigExpanded = it }
        )

        // Leaderboard/Payout Structure Section
        LeaderboardCard(
            payouts = uiState.payouts,
            onIntent = onIntent,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
fun PlayerCountSlider(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit
) {
    Column {
        Text(
            text = "Number of Players: $playerCount",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = PokerColors.CardWhite
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = playerCount.toFloat(),
            onValueChange = { onPlayerCountChange(it.toInt()) },
            valueRange = 3f..30f,
            steps = 26,
            colors = SliderDefaults.colors(
                thumbColor = PokerColors.PokerGold,
                activeTrackColor = PokerColors.AccentGreen,
                inactiveTrackColor = PokerColors.DarkGreen
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
                
                IconButton(
                    onClick = { onExpandedChange(!isExpanded) }
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = PokerColors.PokerGold
                    )
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
                        }
                    )

                    // Pool Configuration
                    PoolConfigurationSection(
                        buyIn = uiState.tournamentConfig.buyIn,
                        foodPerPlayer = uiState.tournamentConfig.foodPerPlayer,
                        bountyPerPlayer = uiState.tournamentConfig.bountyPerPlayer,
                        onBuyInChange = { onIntent(CalculatorIntent.UpdateBuyIn(it)) },
                        onFoodChange = { onIntent(CalculatorIntent.UpdateFoodPerPlayer(it)) },
                        onBountyChange = { onIntent(CalculatorIntent.UpdateBountyPerPlayer(it)) }
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
    onIntent: (CalculatorIntent) -> Unit,
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
                .fillMaxSize()
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
                    onClick = { showWeightsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Weights",
                        tint = PokerColors.AccentGreen
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Leaderboard positions (scrollable)
            if (payouts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(payouts) { payout ->
                        LeaderboardPosition(payout = payout)
                    }
                }
            }
        }
    }
    
    // Weights editor dialog
    if (showWeightsDialog) {
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
    payout: com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
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
                        text = "-----", // Placeholder for player name
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