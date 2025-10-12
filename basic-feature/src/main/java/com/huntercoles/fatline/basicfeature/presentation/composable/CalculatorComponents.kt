package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import com.huntercoles.fatline.basicfeature.domain.model.PayoutPosition
import com.huntercoles.fatline.basicfeature.domain.model.TournamentConfig
import com.huntercoles.fatline.core.design.PokerColors

/**
 * Validates decimal input to only allow digits and one decimal point
 */
private fun isValidDecimalInput(text: String): Boolean {
    if (text.isEmpty()) return true
    
    // Check if it contains only digits and at most one decimal point
    val decimalCount = text.count { it == '.' }
    if (decimalCount > 1) return false
    
    // Check if all characters are digits or decimal point
    return text.all { it.isDigit() || it == '.' }
}

/**
 * Formats a double value to avoid scientific notation
 */
private fun formatDecimal(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        // It's a whole number
        value.toLong().toString()
    } else {
        // Format with appropriate decimal places, avoiding scientific notation
        String.format("%.2f", value).trimEnd('0').trimEnd('.')
    }
}

/**
 * Custom OutlinedTextField for decimal values that positions cursor before decimal point
 */
@Composable
private fun DecimalTextField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    
    var textFieldValue by remember(value) {
        val text = if (value == 0.0) "" else formatDecimal(value)
        val decimalIndex = text.indexOf('.')
        val cursorPosition = if (decimalIndex > 0) decimalIndex else text.length
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(cursorPosition)))
    }
    
    var isFocused by remember { mutableStateOf(false) }
    var hasAutoPositionedThisFocus by remember { mutableStateOf(false) }

    // Update text when value prop changes (from persistence/page switching)
    LaunchedEffect(value) {
        val newText = if (value == 0.0) "" else formatDecimal(value)
        if (newText != textFieldValue.text) {
            val decimalIndex = newText.indexOf('.')
            val cursorPosition = if (decimalIndex > 0) decimalIndex else newText.length
            textFieldValue = TextFieldValue(text = newText, selection = TextRange(cursorPosition))
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newTextFieldValue ->
            val newText = newTextFieldValue.text
            
            // Validate input: only allow digits, one decimal point, and reasonable length
            if (isValidDecimalInput(newText)) {
                val decimalIndex = newText.indexOf('.')
                val idealPosition = if (decimalIndex > 0) decimalIndex else newText.length
                
                // Only auto-position if this is a cursor movement (not text change) and we haven't done it yet this focus
                val isTextChange = newText != textFieldValue.text
                val isCursorMovement = !isTextChange && newTextFieldValue.selection != textFieldValue.selection
                
                val updatedValue = if (isFocused && 
                                       !hasAutoPositionedThisFocus && 
                                       isCursorMovement &&
                                       newTextFieldValue.selection.collapsed &&
                                       newTextFieldValue.selection.start != idealPosition) {
                    hasAutoPositionedThisFocus = true
                    newTextFieldValue.copy(selection = TextRange(idealPosition))
                } else {
                    newTextFieldValue
                }
                
                textFieldValue = updatedValue
                
                // Only call onValueChange for actual text changes
                if (isTextChange) {
                    val doubleValue = newText.toDoubleOrNull() ?: 0.0
                    // Cap at 999,999,999
                    val cappedValue = minOf(doubleValue, 999_999_999.0)
                    if (cappedValue != doubleValue) {
                        // If we had to cap it, update the text field
                        val cappedText = formatDecimal(cappedValue)
                        textFieldValue = textFieldValue.copy(text = cappedText)
                    }
                    onValueChange(cappedValue)
                }
            }
        },
        label = {
            Text(
                label,
                color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        ),
        singleLine = true,
        enabled = !isLocked,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
            unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
            focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
            disabledTextColor = PokerColors.PokerGold,
            cursorColor = PokerColors.PokerGold,
            selectionColors = TextSelectionColors(
                handleColor = PokerColors.PokerGold,
                backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
            )
        ),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!isEnter) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyUp -> {
                        focusManager.clearFocus(force = true)
                        true
                    }
                    KeyEventType.KeyDown -> true
                    else -> false
                }
            }
            .onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            
            // Reset auto-position flag when gaining focus
            if (!wasFocused && isFocused) {
                hasAutoPositionedThisFocus = false
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolConfigurationSection(
    buyIn: Double,
    foodPerPlayer: Double,
    bountyPerPlayer: Double,
    rebuyPerPlayer: Double,
    addOnPerPlayer: Double,
    onBuyInChange: (Double) -> Unit,
    onFoodChange: (Double) -> Unit,
    onBountyChange: (Double) -> Unit,
    onRebuyChange: (Double) -> Unit,
    onAddOnChange: (Double) -> Unit,
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    gameDurationHours: Int,
    roundLengthMinutes: Int,
    smallestChip: Int,
    startingChips: Int,
    onGameDurationHoursChange: (Int) -> Unit,
    onRoundLengthChange: (Int) -> Unit,
    onSmallestChipChange: (Int) -> Unit,
    onStartingChipsChange: (Int) -> Unit,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Player Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = PokerColors.SurfaceSecondary.copy(alpha = 0.8f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Player",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PokerColors.PokerGold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DecimalTextField(
                        value = buyIn,
                        onValueChange = { if (!isLocked) onBuyInChange(it) },
                        label = "Buy-in ($)",
                        isLocked = isLocked,
                        modifier = Modifier.weight(1f)
                    )

                    DecimalTextField(
                        value = foodPerPlayer,
                        onValueChange = { if (!isLocked) onFoodChange(it) },
                        label = "Food ($)",
                        isLocked = isLocked,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DecimalTextField(
                        value = bountyPerPlayer,
                        onValueChange = { if (!isLocked) onBountyChange(it) },
                        label = "Bounty ($)",
                        isLocked = isLocked,
                        modifier = Modifier.weight(1f)
                    )

                    DecimalTextField(
                        value = rebuyPerPlayer,
                        onValueChange = { if (!isLocked) onRebuyChange(it) },
                        label = "Rebuy ($)",
                        isLocked = isLocked,
                        modifier = Modifier.weight(1f)
                    )

                    DecimalTextField(
                        value = addOnPerPlayer,
                        onValueChange = { if (!isLocked) onAddOnChange(it) },
                        label = "Add-on ($)",
                        isLocked = isLocked,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Player Count Slider (moved here, below Player panel)
        PlayerCountSlider(
            playerCount = playerCount,
            onPlayerCountChange = onPlayerCountChange,
            isLocked = isLocked
        )

        // Blinds Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = PokerColors.SurfaceSecondary.copy(alpha = 0.8f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Blinds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PokerColors.PokerGold
                )

                val focusManager = LocalFocusManager.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BlindConfigIntField(
                        value = gameDurationHours,
                        label = "Duration (Hours)",
                        onValueChange = { hours ->
                            val cappedHours = minOf(hours, 24).coerceAtLeast(1)
                            if (!isLocked) onGameDurationHoursChange(cappedHours)
                        },
                        isLocked = isLocked,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f)
                    )

                    BlindConfigIntField(
                        value = roundLengthMinutes,
                        label = "Round Length (Min)",
                        onValueChange = { if (!isLocked) onRoundLengthChange(it) },
                        isLocked = isLocked,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BlindConfigIntField(
                        value = smallestChip,
                        label = "Smallest Chip",
                        onValueChange = { if (!isLocked) onSmallestChipChange(it) },
                        isLocked = isLocked,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f)
                    )

                    BlindConfigIntField(
                        value = startingChips,
                        label = "Starting Chips",
                        onValueChange = { if (!isLocked) onStartingChipsChange(it) },
                        isLocked = isLocked,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f)
                    )
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

/**
 * Validates integer input for blind configuration values
 */
private fun isValidBlindConfigInput(text: String): Boolean {
    if (text.isEmpty()) return true
    return text.all { it.isDigit() } && text.length <= 9 // Max 999,999,999
}

@Composable
fun BlindConfigIntField(
    value: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    isLocked: Boolean,
    focusManager: androidx.compose.ui.focus.FocusManager,
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(value.toString()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!isFocused) {
            textValue = value.toString()
        }
    }

    fun commitInput() {
        if (isLocked) return
        val sanitized = textValue.trim()
        val parsed = sanitized.toIntOrNull()
        if (parsed != null && parsed >= 0) {
            val cappedValue = minOf(parsed, 999_999_999)
            onValueChange(cappedValue)
            textValue = cappedValue.toString()
        } else {
            textValue = value.toString()
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            if (isLocked) return@OutlinedTextField
            if (isValidBlindConfigInput(newValue)) {
                textValue = newValue
            }
        },
        label = { Text(label, color = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite) },
        enabled = !isLocked,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                commitInput()
                focusManager.clearFocus()
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.AccentGreen,
            unfocusedBorderColor = if (isLocked) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.CardWhite,
            focusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            unfocusedTextColor = if (isLocked) PokerColors.PokerGold else PokerColors.CardWhite,
            disabledBorderColor = PokerColors.CardWhite.copy(alpha = 0.5f),
            disabledTextColor = PokerColors.PokerGold,
            cursorColor = PokerColors.PokerGold,
            selectionColors = TextSelectionColors(
                handleColor = PokerColors.PokerGold,
                backgroundColor = PokerColors.PokerGold.copy(alpha = 0.4f)
            )
        ),
        modifier = modifier
            .onFocusChanged { focusState ->
                val gainedFocus = focusState.isFocused
                if (!gainedFocus && isFocused) {
                    textValue = value.toString()
                }
                isFocused = gainedFocus
            }
    )
}