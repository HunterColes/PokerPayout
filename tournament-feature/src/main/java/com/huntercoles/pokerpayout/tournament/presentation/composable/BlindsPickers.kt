package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.utils.SmallestChipChoices
import com.huntercoles.pokerpayout.tournament.R

/** Break intervals offered: off, or every 2 to 8 levels. */
private val BREAK_INTERVALS = listOf(0) + (2..8)

/**
 * PP-051: the smallest chip is a real chip, picked from a row of them (S1), not typed. Each chip is a
 * radio button in a 48 dp box; the picked one has a gold ring. TalkBack reads "Green 25 chip".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmallestChipPicker(value: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.setup_smallest_chip), style = SetupFieldStyle.Label, color = PokerColors.Chalk)
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallestChipChoices.values.forEach { chip ->
                val picked = chip == value
                Box(
                    modifier = Modifier
                        .size(PokerDimens.MinTouch)
                        .selectable(selected = picked, role = Role.RadioButton, onClick = { onPick(chip) }),
                    contentAlignment = Alignment.Center,
                ) {
                    PokerChip(denomination = chip, size = ChipSize, selected = picked)
                }
            }
        }
    }
}

/** Breaks: off, or every N levels. */
@Composable
internal fun BreakIntervalPicker(everyLevels: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    SetupSelectField(
        label = stringResource(R.string.setup_breaks_label),
        options = BREAK_INTERVALS,
        selected = everyLevels,
        optionText = { levels ->
            if (levels == 0) {
                resources.getString(R.string.setup_breaks_off)
            } else {
                resources.getString(R.string.setup_breaks_every, levels)
            }
        },
        onPick = onPick,
        modifier = modifier,
    )
}

private val ChipSize = 38.dp
