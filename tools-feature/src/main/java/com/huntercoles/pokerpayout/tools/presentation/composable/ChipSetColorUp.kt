package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.ChipRef
import com.huntercoles.pokerpayout.core.utils.ColorUpPlan
import com.huntercoles.pokerpayout.core.utils.ColorUpStep
import com.huntercoles.pokerpayout.core.utils.StackPlan
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import kotlin.math.abs

/**
 * "Color-up plan": from the clock's blind schedule, which chips leave at which break (or level),
 * what they go into, and whether the box holds enough of the bigger chip. Not shown while there is
 * no stack to plan from.
 */
@Composable
internal fun ColorUpCard(state: ChipSetUiState) {
    val colorUp = when (val plan = state.plan) {
        is StackPlan.Ready -> plan.colorUp
        is StackPlan.Short -> plan.colorUp
        else -> null
    }
    if (state.hasSchedule && colorUp == null) return
    ChipSetSection {
        SectionHeader(stringResource(R.string.chip_set_color_up), stringResource(R.string.chip_set_color_up_source))
        when {
            colorUp == null -> Note(stringResource(R.string.chip_set_color_up_no_schedule))
            colorUp.steps.isEmpty() -> Note(stringResource(R.string.chip_set_color_up_none))
            else -> {
                Column {
                    colorUp.steps.forEachIndexed { i, step ->
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = PokerColors.FeltLine)
                        ColorUpRow(step)
                    }
                }
                Note(inPlayText(colorUp, state.players))
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
}

/** "Counted for 9 stacks in play", or with the stacks kept back for rebuys. */
@Composable
private fun inPlayText(colorUp: ColorUpPlan, players: Int): String {
    val stacks = colorUp.stacksInPlay
    return if (stacks > players) {
        stringResource(R.string.chip_set_color_up_in_play_reserve, stacks, players, stacks - players)
    } else {
        pluralStringResource(R.plurals.chip_set_color_up_in_play, stacks, stacks)
    }
}

/** Chips → bigger chip, then when, how many are needed, and a check or an alert. */
@Composable
private fun ColorUpRow(step: ColorUpStep) {
    val breakNumber = step.breakNumber
    val afterLevel = step.afterLevel
    val title = if (breakNumber != null && afterLevel != null) {
        stringResource(R.string.chip_set_color_up_break, breakNumber, afterLevel)
    } else {
        stringResource(R.string.chip_set_color_up_level, step.fromLevel)
    }
    val noun = colourNoun(step.into.colour, step.needed)
    val detail = if (step.spare >= 0) {
        stringResource(R.string.chip_set_color_up_spare, step.needed, noun, step.spare)
    } else {
        stringResource(R.string.chip_set_color_up_short, step.needed, noun, abs(step.spare))
    }
    val spoken = stringResource(
        R.string.chip_set_color_up_spoken,
        title,
        joinWithAnd(step.chips.map { spokenChips(it) }),
        spokenChips(step.into),
        detail,
    )
    val ok = step.spare >= 0
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.MinTouch)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        // Large text on a narrow card: the chips go above the words, which get the full width
        val stacked = maxWidth < StackedBelow * fontScale.coerceAtLeast(1f)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipsInto(step)
                    StatusIcon(ok)
                }
                StepText(title, detail, ok)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ChipsInto(step)
                StepText(title, detail, ok, Modifier.weight(1f))
                StatusIcon(ok)
            }
        }
    }
}

/** Below this width (times the font scale) a color-up row stacks its chips above its words. */
private val StackedBelow = 300.dp

/** The chips that leave, an arrow, and the chip they go into: a picture only (the row is read whole). */
@Composable
private fun ChipsInto(step: ColorUpStep) {
    Row(modifier = Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        OverlappedChips(step.chips)
        Icon(
            imageVector = PokerIcons.ChevronRight,
            contentDescription = null,
            tint = PokerColors.Chalk,
            modifier = Modifier.size(18.dp),
        )
        SmallChip(step.into)
    }
}

@Composable
private fun StepText(title: String, detail: String, ok: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = PokerColors.CardWhite)
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = if (ok) PokerColors.Chalk else PokerColors.Danger)
    }
}

@Composable
private fun StatusIcon(ok: Boolean) {
    Icon(
        imageVector = if (ok) PokerIcons.Check else PokerIcons.Info,
        contentDescription = null,
        tint = if (ok) PokerColors.Live else PokerColors.Danger,
        modifier = Modifier.size(20.dp),
    )
}

/** "green 25s": the chips in running text. */
@Composable
private fun spokenChips(chip: ChipRef): String =
    stringResource(R.string.chip_set_spoken_chips, colourAdjective(chip.colour), chipNumber(chip.value))

private val SmallChipSize = 32.dp
private val ChipOverlap = 20.dp

/** The chips that leave, overlapping like a fanned stack when there are several. */
@Composable
private fun OverlappedChips(chips: List<ChipRef>) {
    Box(Modifier.width(SmallChipSize + ChipOverlap * (chips.size - 1).coerceAtLeast(0))) {
        // The smallest chip on top, the rest fanned out behind it
        chips.indices.reversed().forEach { i -> SmallChip(chips[i], Modifier.offset(x = ChipOverlap * i)) }
    }
}

@Composable
private fun SmallChip(chip: ChipRef, modifier: Modifier = Modifier) {
    PokerChip(
        denomination = chip.value,
        modifier = modifier,
        size = SmallChipSize,
        contentDescription = "",
        color = chip.colour.paint(),
    )
}
