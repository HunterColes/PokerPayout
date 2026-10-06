package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.domain.model.PayoutPreset
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding

@Composable
private fun pokerChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = PokerColors.FeltGreen,
    labelColor = PokerColors.CardWhite,
    selectedContainerColor = PokerColors.PokerGold,
    selectedLabelColor = PokerColors.PokerBlack,
    disabledContainerColor = PokerColors.FeltGreen.copy(alpha = 0.5f),
    disabledLabelColor = PokerColors.CardWhite.copy(alpha = 0.5f),
    disabledSelectedContainerColor = PokerColors.PokerGold.copy(alpha = 0.5f)
)

/**
 * One-tap payout presets. Shared by the Payouts panel (Tournament and Payouts tabs) and this editor.
 * The chips wrap onto a second line when they don't fit (a narrow phone, large text) instead of
 * being cut off ("Flat" showed as "F" on a 360 dp phone).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetChips(
    selected: PayoutPreset?,
    enabled: Boolean,
    onSelect: (PayoutPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PayoutPreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                label = { Text(preset.label, maxLines = 1) },
                enabled = enabled,
                colors = pokerChipColors()
            )
        }
    }
}

@Composable
internal fun RoundingChips(selected: PayoutRounding, enabled: Boolean, onSelect: (PayoutRounding) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Round to", color = PokerColors.CardWhite, fontSize = 14.sp)
        PayoutRounding.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
                enabled = enabled,
                colors = pokerChipColors()
            )
        }
    }
}
