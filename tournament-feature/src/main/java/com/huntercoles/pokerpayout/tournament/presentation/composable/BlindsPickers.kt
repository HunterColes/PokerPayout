package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.PokerTextFieldDefaults
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices

/** Break intervals offered: off, or every 2 to 8 levels. */
private val BREAK_INTERVALS = listOf(0) + (2..8)

/** PP-051: the smallest chip is a real chip value, picked from a list, not typed. */
@Composable
internal fun SmallestChipPicker(
    value: Int,
    onPick: (Int) -> Unit,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val formatter = rememberChipFormatter()
    DropdownField(
        spec = DropdownSpec("Smallest Chip", SmallestChipChoices.values, value) { formatter.format(it) },
        onPick = onPick,
        isLocked = isLocked,
        modifier = modifier,
        leading = { ChipDot(it) }
    )
}

@Composable
internal fun BreakIntervalPicker(
    everyLevels: Int,
    onPick: (Int) -> Unit,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    DropdownField(
        spec = DropdownSpec("Breaks", BREAK_INTERVALS, everyLevels) { if (it == 0) "Off" else "Every $it levels" },
        onPick = onPick,
        isLocked = isLocked,
        modifier = modifier
    )
}

private class DropdownSpec(
    val label: String,
    val options: List<Int>,
    val selected: Int,
    val optionText: (Int) -> String
)

/** A read-only field that opens a menu of options; styled like the other Tournament fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    spec: DropdownSpec,
    onPick: (Int) -> Unit,
    isLocked: Boolean,
    modifier: Modifier,
    leading: (@Composable (Int) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && !isLocked,
        onExpandedChange = { if (!isLocked) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = spec.optionText(spec.selected),
            onValueChange = {},
            readOnly = true,
            enabled = !isLocked,
            singleLine = true,
            label = { Text(spec.label, fontSize = 13.sp) },
            leadingIcon = leading?.let { { it(spec.selected) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = PokerTextFieldDefaults.colors(isLocked = isLocked),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isLocked)
        )
        ExposedDropdownMenu(
            expanded = expanded && !isLocked,
            onDismissRequest = { expanded = false },
            containerColor = PokerColors.DarkGreen
        ) {
            spec.options.forEach { option ->
                val isSelected = option == spec.selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = spec.optionText(option),
                            color = if (isSelected) PokerColors.PokerGold else PokerColors.CardWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = leading?.let { { it(option) } },
                    onClick = {
                        expanded = false
                        onPick(option)
                    }
                )
            }
        }
    }
}

/** A chip in its usual color (white 1, red 5, green 25, black 100 ...). */
@Composable
private fun ChipDot(value: Int) {
    val color = ChipDenominations.getChipByValue(value)?.color ?: PokerColors.CardWhite
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
    )
}
