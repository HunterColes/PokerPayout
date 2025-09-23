package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
import com.huntercoles.fatline.basicfeature.domain.model.TournamentConfig
import com.huntercoles.fatline.core.design.PokerColors
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolConfigurationSection(
    buyIn: Double,
    foodPerPlayer: Double,
    bountyPerPlayer: Double,
    onBuyInChange: (Double) -> Unit,
    onFoodChange: (Double) -> Unit,
    onBountyChange: (Double) -> Unit,
    isLocked: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Buy-in
        OutlinedTextField(
            value = if (buyIn == 0.0) "" else buyIn.toString(),
            onValueChange = { value ->
                if (!isLocked) {
                    value.toDoubleOrNull()?.let { onBuyInChange(it) }
                }
            },
            label = { Text("Buy-in per player ($)", color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !isLocked,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
                unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
                focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
                disabledTextColor = PokerColors.PokerGold
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Food per player
        OutlinedTextField(
            value = if (foodPerPlayer == 0.0) "" else foodPerPlayer.toString(),
            onValueChange = { value ->
                if (!isLocked) {
                    value.toDoubleOrNull()?.let { onFoodChange(it) }
                }
            },
            label = { Text("Food per player ($)", color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !isLocked,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
                unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
                focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
                disabledTextColor = PokerColors.PokerGold
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Bounty per player
        OutlinedTextField(
            value = if (bountyPerPlayer == 0.0) "" else bountyPerPlayer.toString(),
            onValueChange = { value ->
                if (!isLocked) {
                    value.toDoubleOrNull()?.let { onBountyChange(it) }
                }
            },
            label = { Text("Bounty per player ($)", color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !isLocked,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
                unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
                focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
                disabledTextColor = PokerColors.PokerGold
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PoolSummarySection(config: TournamentConfig) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.FeltGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "💰 Pool Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Pool:", color = PokerColors.CardWhite)
                Text("$${DecimalFormat("#,##0.00").format(config.totalPool)}", 
                     fontWeight = FontWeight.Bold, color = PokerColors.PokerGold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Prize Pool:", color = PokerColors.CardWhite)
                Text("$${DecimalFormat("#,##0.00").format(config.prizePool)}", 
                     fontWeight = FontWeight.Bold, color = PokerColors.PokerGold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Food Pool:", color = PokerColors.CardWhite)
                Text("$${DecimalFormat("#,##0.00").format(config.foodPool)}", 
                     fontWeight = FontWeight.Bold, color = PokerColors.PokerGold)
            }

            if (config.bountyPool > 0) {
                HorizontalDivider(color = PokerColors.AccentGreen)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bounty Pool:", color = PokerColors.CardWhite)
                    Text("$${DecimalFormat("#,##0.00").format(config.bountyPool)}", 
                         fontWeight = FontWeight.Bold, color = PokerColors.PokerGold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Per bounty:", color = PokerColors.CardWhite)
                    Text("$${DecimalFormat("#,##0.00").format(config.bountyPerPlayer)}", 
                         fontWeight = FontWeight.Bold, color = PokerColors.PokerGold)
                }
            }
        }
    }
}

@Composable
fun PayoutsList(payouts: List<PayoutPosition>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(payouts) { payout ->
            PayoutItem(payout = payout)
        }
    }
}

@Composable
fun PayoutItem(payout: PayoutPosition) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PokerColors.FeltGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = payout.formattedPosition,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PokerColors.CardWhite
                )
                Text(
                    text = "${payout.percentage}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PokerColors.CardWhite
                )
            }

            Text(
                text = "$${DecimalFormat("#,##0.00").format(payout.payout)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )
        }
    }
}