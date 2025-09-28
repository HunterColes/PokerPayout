package com.huntercoles.fatline.portfoliofeature.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Title with Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏦 Bank Tracker",
                style = MaterialTheme.typography.headlineMedium,
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
                    onClick = { onIntent(BankIntent.ShowResetDialog) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Bank Data",
                        tint = PokerColors.PokerGold,
                        modifier = Modifier.size(24.dp).graphicsLayer(scaleX = -1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Reset Confirmation Dialog
        if (uiState.showResetDialog) {
            AlertDialog(
                onDismissRequest = { onIntent(BankIntent.HideResetDialog) },
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
                        text = "This will reset all player names and payment statuses to defaults.",
                        color = PokerColors.CardWhite
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { onIntent(BankIntent.HideResetDialog) }
                    ) {
                        Text(
                            text = "No",
                            color = PokerColors.CardWhite
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onIntent(BankIntent.ConfirmReset) }
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
                    onOutToggle = { onIntent(BankIntent.OutToggled(player.id)) },
                    onPayedOutToggle = { onIntent(BankIntent.PayedOutToggled(player.id)) },
                    onRebuyChange = { onIntent(BankIntent.PlayerRebuyChanged(player.id, it)) },
                    onAddonChange = { onIntent(BankIntent.PlayerAddonChanged(player.id, it)) }
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
                text = "✔️ ${uiState.activePlayers} | ⭐ ${uiState.payedOutCount}",
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
                modifier = Modifier.weight(0.8f) // Match player name field weight
            )

            Text(
                text = "🔄", // Rebuys header
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(0.35f), // Match rebuy field weight
                textAlign = TextAlign.Center
            )

            Text(
                text = "➕", // Addons header
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(0.35f), // Match addon field weight
                textAlign = TextAlign.Center
            )

            Text(
                text = "💰",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(0.3f), // Match checkbox weights
                textAlign = TextAlign.Center
            )

            Text(
                text = "❌",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.ErrorRed,
                modifier = Modifier.weight(0.3f), // Match checkbox weights
                textAlign = TextAlign.Center
            )

            Text(
                text = "⭐",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(0.3f), // Match checkbox weights
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
    onOutToggle: () -> Unit,
    onPayedOutToggle: () -> Unit,
    onRebuyChange: (Int) -> Unit,
    onAddonChange: (Int) -> Unit
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
                modifier = Modifier.weight(0.8f), // Made 20% smaller
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PokerColors.AccentGreen,
                    unfocusedBorderColor = PokerColors.CardWhite,
                    focusedTextColor = PokerColors.CardWhite,
                    unfocusedTextColor = PokerColors.CardWhite,
                    cursorColor = PokerColors.PokerGold,
                    selectionColors = TextSelectionColors(
                        handleColor = PokerColors.PokerGold,
                        backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
                    )
                )
            )

            // Rebuys - numeric input
            OutlinedTextField(
                value = if (player.rebuys == 0) "" else player.rebuys.toString(),
                onValueChange = { value ->
                    val numericValue = value.filter { it.isDigit() }.take(2) // Limit to 2 digits max
                    val intValue = if (numericValue.isEmpty()) 0 else numericValue.toIntOrNull() ?: 0
                    onRebuyChange(intValue)
                },
                modifier = Modifier.weight(0.35f), // Made bigger to fill gap
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp // Larger font for visibility
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PokerColors.AccentGreen,
                    unfocusedBorderColor = PokerColors.CardWhite,
                    focusedTextColor = PokerColors.CardWhite,
                    unfocusedTextColor = PokerColors.CardWhite,
                    cursorColor = PokerColors.PokerGold
                ),
                placeholder = { 
                    Text(
                        "0", 
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
                    ) 
                }
            )

            // Addons - numeric input
            OutlinedTextField(
                value = if (player.addons == 0) "" else player.addons.toString(),
                onValueChange = { value ->
                    val numericValue = value.filter { it.isDigit() }.take(2) // Limit to 2 digits max
                    val intValue = if (numericValue.isEmpty()) 0 else numericValue.toIntOrNull() ?: 0
                    onAddonChange(intValue)
                },
                modifier = Modifier.weight(0.35f), // Made bigger to fill gap
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp // Larger font for visibility
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PokerColors.AccentGreen,
                    unfocusedBorderColor = PokerColors.CardWhite,
                    focusedTextColor = PokerColors.CardWhite,
                    unfocusedTextColor = PokerColors.CardWhite,
                    cursorColor = PokerColors.PokerGold
                ),
                placeholder = { 
                    Text(
                        "0", 
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
                    ) 
                }
            )

            // Buy-In Status - clickable emoji
            TextButton(
                onClick = onBuyInToggle,
                modifier = Modifier.weight(0.3f) // Reduced weight for checkboxes
            ) {
                Text(
                    text = if (player.buyIn) "💰" else "⚪",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }

            // Knocked Out Status - clickable emoji
            TextButton(
                onClick = onOutToggle,
                modifier = Modifier.weight(0.3f) // Reduced weight for checkboxes
            ) {
                Text(
                    text = if (player.out) "❌" else "⚪",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }

            // Payed-Out Status - clickable emoji
            TextButton(
                onClick = onPayedOutToggle,
                modifier = Modifier.weight(0.3f) // Reduced weight for checkboxes
            ) {
                Text(
                    text = if (player.payedOut) "⭐" else "⚪",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}