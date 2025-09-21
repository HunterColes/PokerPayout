package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.huntercoles.fatline.basicfeature.presentation.CalculatorIntent
import com.huntercoles.fatline.basicfeature.presentation.CalculatorUiState
import com.huntercoles.fatline.basicfeature.presentation.CalculatorViewModel
import com.huntercoles.fatline.core.design.PokerGold

@Composable
fun CalculatorRoute(viewModel: CalculatorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalculatorScreen(
        uiState = uiState,
        onIntent = viewModel::acceptIntent,
    )
}

@Composable
internal fun CalculatorScreen(
    uiState: CalculatorUiState,
    onIntent: (CalculatorIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Title
        Text(
            text = "🃏 Poker Payout Calculator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PokerGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Player Count Section
            item {
                PlayerCountSection(
                    playerCount = uiState.playerCount,
                    onPlayerCountChange = { onIntent(CalculatorIntent.PlayerCountChanged(it)) }
                )
            }

            // Pool Configuration Section
            item {
                PoolConfigurationSection(
                    buyIn = uiState.buyIn,
                    foodPool = uiState.foodPool,
                    bountyPool = uiState.bountyPool,
                    onBuyInChange = { onIntent(CalculatorIntent.BuyInChanged(it)) },
                    onFoodPoolChange = { onIntent(CalculatorIntent.FoodPoolChanged(it)) },
                    onBountyPoolChange = { onIntent(CalculatorIntent.BountyPoolChanged(it)) }
                )
            }

            // Total Per Player
            item {
                TotalPerPlayerCard(
                    total = uiState.totalPerPlayer
                )
            }

            // Payout Results
            if (uiState.payouts.isNotEmpty()) {
                item {
                    PayoutResultsSection(payouts = uiState.payouts)
                }
            }

            // Pool Summary
            item {
                PoolSummarySection(
                    prizePool = uiState.prizePool,
                    foodPool = uiState.foodPool * uiState.playerCount,
                    bountyPool = uiState.bountyPool * uiState.playerCount,
                    totalPool = uiState.totalPool
                )
            }
        }
    }
}

@Composable
private fun PlayerCountSection(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "👥 Number of Players",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$playerCount Players",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = playerCount.toFloat(),
                onValueChange = { onPlayerCountChange(it.toInt()) },
                valueRange = 3f..30f,
                steps = 27,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PoolConfigurationSection(
    buyIn: Double,
    foodPool: Double,
    bountyPool: Double,
    onBuyInChange: (Double) -> Unit,
    onFoodPoolChange: (Double) -> Unit,
    onBountyPoolChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💵 Pool Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerGold
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = buyIn.toString(),
                onValueChange = { onBuyInChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Buy-in per player ($)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = foodPool.toString(),
                onValueChange = { onFoodPoolChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Food pool per player ($)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = bountyPool.toString(),
                onValueChange = { onBountyPoolChange(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Bounty per player ($)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TotalPerPlayerCard(total: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💳 Total Due Per Player",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$${String.format("%.2f", total)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = PokerGold
            )
        }
    }
}

@Composable
private fun PayoutResultsSection(payouts: List<Pair<Int, Double>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💰 Tournament Payouts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerGold
            )

            Spacer(modifier = Modifier.height(16.dp))

            payouts.forEach { (position, amount) ->
                PayoutRow(position = position, amount = amount)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PayoutRow(position: Int, amount: Double) {
    val positionEmoji = when (position) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "🏅"
    }

    val suffix = when {
        position % 100 in 11..13 -> "th"
        position % 10 == 1 -> "st"
        position % 10 == 2 -> "nd"
        position % 10 == 3 -> "rd"
        else -> "th"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$positionEmoji ${position}$suffix Place",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "$${String.format("%.2f", amount)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = PokerGold
        )
    }
}

@Composable
private fun PoolSummarySection(
    prizePool: Double,
    foodPool: Double,
    bountyPool: Double,
    totalPool: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 Pool Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerGold
            )

            Spacer(modifier = Modifier.height(16.dp))

            PoolSummaryRow(label = "Prize Pool", amount = prizePool)
            PoolSummaryRow(label = "Food Pool", amount = foodPool)
            PoolSummaryRow(label = "Bounty Pool", amount = bountyPool)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Pool",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$${String.format("%.2f", totalPool)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = PokerGold
                )
            }
        }
    }
}

@Composable
private fun PoolSummaryRow(label: String, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "$${String.format("%.2f", amount)}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}