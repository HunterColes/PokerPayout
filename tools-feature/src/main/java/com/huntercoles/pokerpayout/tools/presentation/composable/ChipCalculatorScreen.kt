package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerDialog
import com.huntercoles.pokerpayout.core.design.components.invertHorizontally
import com.huntercoles.pokerpayout.core.design.components.PokerTextFieldDefaults
import com.huntercoles.pokerpayout.core.utils.ChipDistributionCurve
import com.huntercoles.pokerpayout.tools.presentation.ChipCalculatorViewModel
import com.huntercoles.pokerpayout.tools.presentation.ChipBreakdown

/**
 * Chip Calculator Screen
 * Calculates optimal chip distribution for poker tournaments
 * Automatically syncs with tournament starting chips configuration via TournamentPreferences
 */
@Composable
fun ChipCalculatorScreen(
    viewModel: ChipCalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var totalChipsText by remember(uiState.totalChips) { mutableStateOf(uiState.totalChips.toString()) }

    // Update text field when totalChips changes
    LaunchedEffect(uiState.totalChips) {
        if (uiState.customTotalChips == 0) {
            totalChipsText = uiState.totalChips.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PokerColors.PokerBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Reset Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎰 Chip Calculator",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold,
                modifier = Modifier.weight(1f)
            )

            // Reset button
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = PokerColors.DarkGreen)
            ) {
                IconButton(
                    onClick = { viewModel.showResetDialog() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = PokerColors.PokerGold,
                        modifier = Modifier
                            .size(24.dp)
                            .invertHorizontally()
                    )
                }
            }
        }

        // Reset Confirmation Dialog
        if (uiState.showResetDialog) {
            PokerDialog(
                onDismissRequest = { viewModel.hideResetDialog() }
            ) {
                Text(
                    text = "Reset chip calculator?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PokerColors.PokerGold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PokerColors.FeltGreen,
                    border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "This will reset the total chips value to tournament starting chips.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PokerColors.CardWhite,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = { viewModel.hideResetDialog() }
                    ) {
                        Text(
                            text = "Cancel",
                            color = PokerColors.CardWhite
                        )
                    }

                    TextButton(
                        onClick = { viewModel.confirmReset() }
                    ) {
                        Text(
                            text = "Reset",
                            color = PokerColors.PokerGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Configuration Card: Curve Selection and Denomination Count
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
            border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Distribution Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = PokerColors.PokerGold
                )

                // Curve Selection Dropdown
                var curveExpanded by remember { mutableStateOf(false) }
                val availableCurves = remember { ChipDistributionCurve.getAllCurves() }
                
                ExposedDropdownMenuBox(
                    expanded = curveExpanded,
                    onExpandedChange = { curveExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.selectedCurve.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Distribution Curve") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select curve"
                            )
                        },
                        colors = PokerTextFieldDefaults.colors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = curveExpanded,
                        onDismissRequest = { curveExpanded = false }
                    ) {
                        availableCurves.forEach { curve ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = curve.displayName,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = curve.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PokerColors.TextSecondary
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateCurveSelection(curve)
                                    curveExpanded = false
                                }
                            )
                        }
                    }
                }

                // Denomination Count Input
                var denomCountText by remember { mutableStateOf(uiState.denominationCount.toString()) }
                
                OutlinedTextField(
                    value = denomCountText,
                    onValueChange = { newValue ->
                        denomCountText = newValue
                        newValue.toIntOrNull()?.let { count ->
                            if (count in 3..8) {
                                viewModel.updateDenominationCount(count)
                            }
                        }
                    },
                    label = { Text("Number of Denominations") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            denomCountText.toIntOrNull()?.let { count ->
                                val validCount = count.coerceIn(3, 8)
                                denomCountText = validCount.toString()
                                viewModel.updateDenominationCount(validCount)
                            }
                        }
                    ),
                    singleLine = true,
                    colors = PokerTextFieldDefaults.colors(),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "Recommended: 5 (Range: 3-8)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
            }
        }

        // Starting Chips Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
            border = BorderStroke(1.dp, PokerColors.AccentGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = totalChipsText,
                        onValueChange = { newValue ->
                            totalChipsText = newValue
                            newValue.toIntOrNull()?.let { chips ->
                                if (chips > 0) {
                                    viewModel.updateTotalChips(chips)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Starting Chips") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = PokerTextFieldDefaults.colors()
                    )

                    // Generate Button
                    Button(
                        onClick = { viewModel.calculateChipBreakdown() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PokerColors.AccentGreen,
                            contentColor = PokerColors.CardWhite
                        ),
                        enabled = uiState.totalChips > 0
                    ) {
                        Text(
                            text = "Generate",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Minimum Chip Value Display
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = PokerColors.FeltGreen.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minimum Chip Value:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PokerColors.CardWhite
                        )
                        Text(
                            text = "$${uiState.smallestChip}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = PokerColors.PokerGold
                        )
                    }
                }
            }
        }

        // Chip Breakdown Results
        if (uiState.chipBreakdown.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PokerColors.SurfacePrimary),
                border = BorderStroke(1.dp, PokerColors.AccentGreen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with fit score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💎 Chip Breakdown",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PokerColors.PokerGold
                        )
                        
                        // Fit Score Badge (1.0 = perfect, 0.0 = terrible)
                        uiState.fitScore?.let { score ->
                            val (scoreText, scoreColor) = when {
                                score > 0.9 -> "Excellent" to PokerColors.AccentGreen
                                score > 0.8 -> "Good" to Color(0xFF8BC34A)
                                score > 0.7 -> "Fair" to Color(0xFFFFC107)
                                else -> "Poor" to Color(0xFFFF5722)
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = scoreColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, scoreColor)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = scoreText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scoreColor
                                    )
                                    Text(
                                        text = "Fit: ${"%.3f".format(score)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PokerColors.CardWhite
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = PokerColors.PokerGold.copy(alpha = 0.3f))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Total Chips",
                            value = uiState.totalPhysicalChips.toString()
                        )
                        StatItem(
                            label = "Denominations",
                            value = uiState.chipBreakdown.size.toString()
                        )
                        StatItem(
                            label = "Total Value",
                            value = "$${uiState.chipBreakdown.sumOf { it.value * it.count }}"
                        )
                    }

                    Divider(color = PokerColors.PokerGold.copy(alpha = 0.3f))

                    Text(
                        text = "Recommended Chip Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PokerColors.PokerGold
                    )

                    uiState.chipBreakdown.forEach { chip ->
                        ChipBreakdownItem(chip = chip)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    HorizontalDivider(color = PokerColors.PokerGold.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PokerColors.CardWhite
                        )
                        Text(
                            text = "${uiState.chipBreakdown.sumOf { it.value * it.count }}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PokerColors.PokerGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChipBreakdownItem(chip: ChipBreakdown) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chip visual representation
        ChipView(
            color = chip.color,
            value = chip.value,
            size = 56.dp
        )

        // Chip info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chip.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PokerColors.CardWhite
            )
            Text(
                text = "Value: ${chip.value}",
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.TextSecondary
            )
        }

        // Count and subtotal
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "× ${chip.count}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = PokerColors.PokerGold
            )
            Text(
                text = "= ${chip.value * chip.count}",
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.TextSecondary
            )
        }
    }
}

@Composable
fun ChipView(
    color: Color,
    value: Int,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .border(
                width = 3.dp,
                color = if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = PokerColors.PokerGold.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                value >= 1000 -> "${value / 1000}K"
                else -> value.toString()
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (color.luminance() > 0.5f) Color.Black else Color.White,
            textAlign = TextAlign.Center
        )
    }
}

// Helper function to calculate luminance
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PokerColors.AccentGreen
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.TextSecondary
        )
    }
}
