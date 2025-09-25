package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huntercoles.fatline.core.design.PokerColors

/**
 * Validates integer input to only allow digits
 */
private fun isValidIntegerInput(text: String): Boolean {
    if (text.isEmpty()) return true
    return text.all { it.isDigit() } && text.length <= 3 // Max 999
}

@Composable
fun WeightsEditorDialog(
    currentWeights: List<Int>,
    onWeightsChanged: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var weights by remember { mutableStateOf(currentWeights.toMutableList()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
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
                    text = "Adjust weights to customize payout distribution.\nHigher weights = larger payouts.",
                    fontSize = 14.sp,
                    color = PokerColors.CardWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Weights list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(weights) { index, weight ->
                        WeightRow(
                            position = index + 1,
                            weight = weight,
                            onWeightChange = { newWeight ->
                                if (newWeight > 0) {
                                    weights[index] = newWeight
                                }
                            },
                            onDelete = if (weights.size > 1) {
                                { weights.removeAt(index) }
                            } else null
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Add position button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { weights.add(1) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PokerColors.AccentGreen
                        ),
                        border = BorderStroke(
                            1.dp,
                            PokerColors.AccentGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Position",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Add Position")
                    }
                    
                    // Quick add button for common tournament structures
                    OutlinedButton(
                        onClick = { 
                            // Add positions to reach 15 total (good for larger tournaments)
                            repeat(kotlin.math.max(0, 15 - weights.size)) {
                                weights.add(1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PokerColors.PokerGold
                        ),
                        border = BorderStroke(
                            1.dp,
                            PokerColors.PokerGold
                        )
                    ) {
                        Text("15 Positions")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
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
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PokerColors.AccentGreen,
                            contentColor = PokerColors.DarkGreen
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WeightRow(
    position: Int,
    weight: Int,
    onWeightChange: (Int) -> Unit,
    onDelete: (() -> Unit)?
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
            var weightText by remember(weight) { mutableStateOf(weight.toString()) }
            val focusManager = LocalFocusManager.current
            
            OutlinedTextField(
                value = weightText,
                onValueChange = { newValue ->
                    // Only allow valid integer input
                    if (isValidIntegerInput(newValue)) {
                        weightText = newValue
                        newValue.toIntOrNull()?.let { newWeight ->
                            if (newWeight > 0 && newWeight <= 999) {
                                onWeightChange(newWeight)
                            }
                        }
                    }
                },
                modifier = Modifier.width(80.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PokerColors.AccentGreen,
                    unfocusedBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
                    focusedTextColor = PokerColors.CardWhite,
                    unfocusedTextColor = PokerColors.CardWhite,
                    cursorColor = PokerColors.PokerGold,
                    selectionColors = TextSelectionColors(
                        handleColor = PokerColors.PokerGold,
                        backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
                    )
                )
            )
            
            // Delete button (only show if more than 1 position and delete action is provided)
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Position",
                        tint = PokerColors.ErrorRed
                    )
                }
            } else {
                // Spacer to maintain layout consistency
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}