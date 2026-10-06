package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.design.components.PokerStepper
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.ChipInventory
import com.huntercoles.pokerpayout.core.utils.InventoryChip
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetIntent
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import kotlin.math.max

/** A count stepper moves in fives: sets are counted in rolls, and the colour sheet takes exact counts. */
internal const val COUNT_STEP = 5

/** "Chips you own": a row per colour (chip, name, count stepper), then "Add a colour". */
@Composable
internal fun OwnedCard(state: ChipSetUiState, onIntent: (ChipSetIntent) -> Unit) {
    val inventory = state.inventory
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.chip_set_owned),
            note = stringResource(R.string.chip_set_owned_total, chipNumber(inventory.totalChips)),
            gold = false,
        )
        if (!state.settings.inventoryReviewed) {
            Text(
                text = stringResource(R.string.chip_set_check_counts),
                style = MaterialTheme.typography.bodySmall,
                color = PokerColors.Chalk,
            )
        }
        Column {
            inventory.chips.forEachIndexed { i, chip ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = PokerColors.FeltLine)
                InventoryRow(chip, onIntent)
            }
            if (inventory.missingColours.isNotEmpty()) {
                if (inventory.chips.isNotEmpty()) HorizontalDivider(thickness = 1.dp, color = PokerColors.FeltLine)
                AddColourRow { onIntent(ChipSetIntent.AddColour) }
            }
        }
    }
}

/** One colour: tap the chip and name to edit it; the stepper sets how many you own. */
@Composable
private fun InventoryRow(chip: InventoryChip, onIntent: (ChipSetIntent) -> Unit) {
    val name = chipName(chip.colour, chip.value)
    SideBySideOrStacked(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.RowMinHeight)
            .padding(vertical = 4.dp),
        first = {
            Row(
                modifier = Modifier
                    .heightIn(min = PokerDimens.MinTouch)
                    .clip(RoundedCornerShape(PokerDimens.CornerControl))
                    .clickable(
                        onClickLabel = stringResource(R.string.chip_set_edit_colour),
                        role = Role.Button,
                        onClick = { onIntent(ChipSetIntent.EditColour(chip.colour)) },
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The name beside it says what the chip is; TalkBack reads the name once
                PokerChip(denomination = chip.value, color = chip.colour.paint(), contentDescription = "")
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = PokerColors.CardWhite,
                )
            }
        },
        second = {
            PokerStepper(
                value = chip.count,
                onValueChange = { next -> onIntent(ChipSetIntent.SetCount(chip.colour, steppedCount(chip.count, next))) },
                range = 0..ChipInventory.MAX_COUNT,
                label = stringResource(R.string.chip_set_count_label, colourName(chip.colour), chipNumber(chip.value)),
            )
        },
    )
}

/**
 * The stepper asks for one more or one fewer; the set moves to the next or previous multiple of
 * [COUNT_STEP] instead (152 goes up to 155 and down to 150).
 */
internal fun steppedCount(current: Int, next: Int): Int {
    val stepped = if (next > current) (current / COUNT_STEP + 1) * COUNT_STEP else ((current - 1) / COUNT_STEP) * COUNT_STEP
    return stepped.coerceIn(0, ChipInventory.MAX_COUNT)
}

@Composable
private fun AddColourRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.RowMinHeight)
            .clip(RoundedCornerShape(PokerDimens.CornerControl))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(PokerDimens.PokerChipMedium)
                .border(1.5.dp, PokerColors.FeltEdge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PokerIcons.Plus, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(20.dp))
        }
        Text(
            text = stringResource(R.string.chip_set_add_colour),
            style = MaterialTheme.typography.titleSmall,
            color = PokerColors.PokerGold,
        )
    }
}

/**
 * [first] and [second] on one line, [second] at the end, when [first] fits beside it without
 * breaking a word; else [second] goes on its own line under [first], still at the end. (A long
 * chip name and a stepper on a small phone at large text sizes.)
 */
@Composable
internal fun SideBySideOrStacked(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
) {
    Layout(contents = listOf(first, second), modifier = modifier) { (firstParts, secondParts), constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val end = secondParts.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val room = (width - end.width - gapPx).coerceAtLeast(0)
        val start = firstParts.first()
        if (start.minIntrinsicWidth(Constraints.Infinity) <= room) {
            val placed = start.measure(Constraints(minWidth = 0, maxWidth = room))
            val height = max(max(placed.height, end.height), constraints.minHeight)
            layout(width, height) {
                placed.place(0, (height - placed.height) / 2)
                end.place(width - end.width, (height - end.height) / 2)
            }
        } else {
            val placed = start.measure(Constraints(minWidth = 0, maxWidth = width))
            val height = max(placed.height + gapPx + end.height, constraints.minHeight)
            layout(width, height) {
                placed.place(0, 0)
                end.place(width - end.width, placed.height + gapPx)
            }
        }
    }
}
