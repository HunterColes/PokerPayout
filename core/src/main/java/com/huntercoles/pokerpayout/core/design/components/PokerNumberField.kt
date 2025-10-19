package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.huntercoles.pokerpayout.core.design.PokerColors

/**
 * Validates integer input for number fields
 */
private fun isValidNumberInput(text: String): Boolean {
    if (text.isEmpty()) return true
    return text.all { it.isDigit() } && text.length <= 9 // Max 999,999,999
}

/**
 * A reusable number input field that only accepts digits and enforces integer constraints.
 * Provides consistent validation and theming across the app.
 *
 * @param value The current integer value
 * @param onValueChange Callback when the value changes (committed on focus loss or Enter)
 * @param label The label text for the field
 * @param modifier Modifier for the composable
 * @param minValue Minimum allowed value (default 0)
 * @param maxValue Maximum allowed value (default 999,999,999)
 * @param isLocked Whether the field is disabled/locked
 */
@Composable
fun PokerNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minValue: Int = 0,
    maxValue: Int = 999_999_999,
    isLocked: Boolean = false
) {
    val focusManager = LocalFocusManager.current
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
        if (parsed != null && parsed >= minValue) {
            val cappedValue = minOf(parsed, maxValue)
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
            if (isValidNumberInput(newValue)) {
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
        colors = PokerTextFieldDefaults.colors(isLocked = isLocked),
        modifier = modifier
            .onFocusChanged { focusState ->
                val gainedFocus = focusState.isFocused
                if (!gainedFocus && isFocused) {
                    commitInput()
                }
                isFocused = gainedFocus
            }
            .onPreviewKeyEvent { event ->
                val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (!isEnter) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyUp -> {
                        commitInput()
                        focusManager.clearFocus(force = true)
                        true
                    }
                    KeyEventType.KeyDown -> true
                    else -> false
                }
            }
    )
}