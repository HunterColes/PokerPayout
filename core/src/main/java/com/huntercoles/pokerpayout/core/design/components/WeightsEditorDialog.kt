package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.constants.TournamentConstants
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog

private const val MAX_WEIGHT_VALUE = 999
private val MAX_WEIGHT_POSITIONS = TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.size

/**
 * Validates that weights are in strictly decreasing order
 */
internal fun isValidWeightChange(weights: List<Int>, index: Int, newWeight: Int): Boolean {
    // Check against previous weight (should be less than)
    if (index > 0 && newWeight >= weights[index - 1]) {
        return false
    }
    // Check against next weight (should be greater than)
    if (index < weights.size - 1 && newWeight <= weights[index + 1]) {
        return false
    }
    return true
}

internal fun detectInvalidWeights(weights: List<Int>): List<Boolean> {
    if (weights.isEmpty()) return emptyList()
    return weights.mapIndexed { index, weight ->
        val violatesPrev = index > 0 && weight >= weights[index - 1]
        val violatesNext = index < weights.lastIndex && weight <= weights[index + 1]
        violatesPrev || violatesNext
    }
}

@Composable
fun WeightsEditorDialog(
    currentWeights: List<Int>,
    onWeightsChanged: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    isLocked: Boolean = false
) {
    val weights = remember(currentWeights) { mutableStateListOf<Int>().apply { addAll(currentWeights) } }

    // Track which positions violate the strictly decreasing requirement
    val invalidPositions by remember(weights) {
        derivedStateOf { detectInvalidWeights(weights) }
    }

    val hasErrors = invalidPositions.any { it }

    PokerDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Text(
            text = "⚖️ Edit Payout Weights",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Higher weights = larger payouts.",
            fontSize = 14.sp,
            color = PokerColors.CardWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(weights) { index, weight ->
                WeightRow(
                    position = index + 1,
                    weight = weight,
                    isError = invalidPositions.getOrElse(index) { false },
                    isLocked = isLocked,
                    onWeightChange = { newWeight ->
                        if (newWeight > 0) {
                            weights[index] = newWeight
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (weights.size > 1) {
                        weights.removeAt(weights.size - 1)
                    }
                },
                enabled = weights.size > 1 && !hasErrors && !isLocked,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isLocked) PokerColors.PokerGold.copy(alpha = 0.5f) else PokerColors.ErrorRed
                ),
                border = BorderStroke(
                    1.dp,
                    if (isLocked) PokerColors.PokerGold.copy(alpha = 0.5f) else PokerColors.ErrorRed
                )
            ) {
                Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    if (weights.size < MAX_WEIGHT_POSITIONS && !hasErrors) {
                        val nextPosition = weights.size + 1
                        val defaultWeight = if (nextPosition <= TournamentConstants.DEFAULT_PAYOUT_WEIGHTS.size) {
                            TournamentConstants.DEFAULT_PAYOUT_WEIGHTS[nextPosition - 1]
                        } else {
                            1 // Default for positions beyond the standard defaults
                        }
                        weights.add(defaultWeight)
                    }
                },
                enabled = weights.size < MAX_WEIGHT_POSITIONS && !hasErrors && !isLocked,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isLocked) PokerColors.PokerGold.copy(alpha = 0.5f) else PokerColors.AccentGreen
                ),
                border = BorderStroke(
                    1.dp,
                    if (isLocked) PokerColors.PokerGold.copy(alpha = 0.5f) else PokerColors.AccentGreen
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Position"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PokerColors.CardWhite
                )
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    onWeightsChanged(weights.toList())
                    onDismiss()
                },
                enabled = !hasErrors && !isLocked,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocked) PokerColors.PokerGold.copy(alpha = 0.5f) else PokerColors.AccentGreen,
                    contentColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.DarkGreen,
                    disabledContainerColor = PokerColors.CardWhite.copy(alpha = 0.3f),
                    disabledContentColor = PokerColors.CardWhite.copy(alpha = 0.5f)
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WeightRow(
    position: Int,
    weight: Int,
    isError: Boolean,
    isLocked: Boolean,
    onWeightChange: (Int) -> Unit
) {
    val trophy = when (position) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "🏅"
    }

    val positionSuffix = when {
        position % 100 in 10..20 -> "th"
        position % 10 == 1 -> "st"
        position % 10 == 2 -> "nd"
        position % 10 == 3 -> "rd"
        else -> "th"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (position <= 3) PokerColors.AccentGreen.copy(alpha = 0.2f)
                          else PokerColors.SurfaceSecondary
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Position info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = trophy,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Text(
                    text = "$position$positionSuffix",
                    color = PokerColors.CardWhite,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            // Weight input
            PokerNumberField(
                value = weight,
                onValueChange = onWeightChange,
                label = "",
                minValue = 1,
                maxValue = MAX_WEIGHT_VALUE,
                isLocked = isLocked,
                modifier = Modifier.width(80.dp)
            )
        }
    }
}