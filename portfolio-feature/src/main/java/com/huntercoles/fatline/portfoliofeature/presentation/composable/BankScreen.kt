package com.huntercoles.fatline.portfoliofeature.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.huntercoles.fatline.portfoliofeature.presentation.BankIntent
import com.huntercoles.fatline.portfoliofeature.presentation.BankUiState
import com.huntercoles.fatline.portfoliofeature.presentation.BankViewModel
import com.huntercoles.fatline.portfoliofeature.presentation.PlayerData
import com.huntercoles.fatline.core.design.PokerColors

@Composable
fun BankRoute(viewModel: BankViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BankScreen(
        uiState = uiState,
        onIntent = viewModel::acceptIntent,
    )
}

@Composable
internal fun BankScreen(
    uiState: BankUiState,
    onIntent: (BankIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Title
        Text(
            text = "🏦 Bank Tracker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pool Summary
            item {
                PoolSummaryCard(uiState = uiState)
            }

            // Header
            item {
                PlayerHeader()
            }

            // Player rows
            items(uiState.players) { player ->
                PlayerRow(
                    player = player,
                    onNameChange = { onIntent(BankIntent.PlayerNameChanged(player.id, it)) },
                    onBuyInToggle = { onIntent(BankIntent.BuyInToggled(player.id)) },
                    onFoodToggle = { onIntent(BankIntent.FoodToggled(player.id)) },
                    onBountyToggle = { onIntent(BankIntent.BountyToggled(player.id)) },
                    onAllToggle = { onIntent(BankIntent.AllToggled(player.id)) },
                    onEliminatedToggle = { onIntent(BankIntent.EliminatedToggled(player.id)) },
                    onPayedOutToggle = { onIntent(BankIntent.PayedOutToggled(player.id)) }
                )
            }
        }
    }
}

@Composable
private fun PoolSummaryCard(uiState: BankUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "💰 Pool Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Pool:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokerColors.CardWhite
                )
                Text(
                    text = "$${String.format("%.2f", uiState.totalPool)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.CardWhite
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Paid:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokerColors.CardWhite
                )
                Text(
                    text = "$${String.format("%.2f", uiState.totalPaid)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.CardWhite
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Percent Paid:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PokerColors.CardWhite
                )
                Text(
                    text = "${String.format("%.1f", uiState.percentPaid)}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        uiState.percentPaid >= 100 -> PokerColors.PokerGold
                        uiState.percentPaid >= 75 -> PokerColors.AccentGreen
                        else -> PokerColors.CardWhite
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Active: ${uiState.activePlayers} | Paid Out: ${uiState.payedOutCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlayerHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = PokerColors.FeltGreen
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Player Name",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Buy-In",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(0.4f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Food",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(0.4f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Bounty",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(0.4f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "All",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(0.4f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "❌",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(0.3f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "⭐",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                modifier = Modifier.weight(0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerData,
    onNameChange: (String) -> Unit,
    onBuyInToggle: () -> Unit,
    onFoodToggle: () -> Unit,
    onBountyToggle: () -> Unit,
    onAllToggle: () -> Unit,
    onEliminatedToggle: () -> Unit,
    onPayedOutToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.SurfaceSecondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = player.name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PokerColors.AccentGreen,
                    unfocusedBorderColor = PokerColors.CardWhite,
                    focusedTextColor = PokerColors.CardWhite,
                    unfocusedTextColor = PokerColors.CardWhite
                )
            )

            Spacer(modifier = Modifier.weight(0.1f))

            Checkbox(
                checked = player.buyIn,
                onCheckedChange = { onBuyInToggle() },
                modifier = Modifier.weight(0.4f),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = PokerColors.AccentGreen,
                    uncheckedColor = PokerColors.CardWhite,
                    checkmarkColor = PokerColors.DarkGreen
                )
            )

            Checkbox(
                checked = player.food,
                onCheckedChange = { onFoodToggle() },
                modifier = Modifier.weight(0.4f),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = PokerColors.AccentGreen,
                    uncheckedColor = PokerColors.CardWhite,
                    checkmarkColor = PokerColors.DarkGreen
                )
            )

            Checkbox(
                checked = player.bounty,
                onCheckedChange = { onBountyToggle() },
                modifier = Modifier.weight(0.4f),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = PokerColors.AccentGreen,
                    uncheckedColor = PokerColors.CardWhite,
                    checkmarkColor = PokerColors.DarkGreen
                )
            )

            Checkbox(
                checked = player.all,
                onCheckedChange = { onAllToggle() },
                modifier = Modifier.weight(0.4f),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = PokerColors.AccentGreen,
                    uncheckedColor = PokerColors.CardWhite,
                    checkmarkColor = PokerColors.DarkGreen
                )
            )

            Checkbox(
                checked = player.eliminated,
                onCheckedChange = { onEliminatedToggle() },
                modifier = Modifier.weight(0.3f),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = PokerColors.ErrorRed,
                    uncheckedColor = PokerColors.CardWhite,
                    checkmarkColor = PokerColors.CardWhite
                )
            )

            Checkbox(
                checked = player.payedOut,
                onCheckedChange = { onPayedOutToggle() },
                modifier = Modifier.weight(0.3f),
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = PokerColors.PokerGold,
                    uncheckedColor = PokerColors.CardWhite,
                    checkmarkColor = PokerColors.DarkGreen
                )
            )
        }
    }
}