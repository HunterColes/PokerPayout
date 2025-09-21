package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

        // Configuration Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "💵 Tournament Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold
                )

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

        // Payouts Section
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "💰 Tournament Payouts",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.PokerGold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PokerColors.AccentGreen)
                    }
                } else {
                    PayoutsList(payouts = uiState.payouts)
                }
            }
        }
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