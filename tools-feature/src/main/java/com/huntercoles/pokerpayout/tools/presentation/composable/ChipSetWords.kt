package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipShortfall
import com.huntercoles.pokerpayout.core.utils.StackPlan
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetText

/** The physical chip's colour, for drawing it. */
internal fun ChipColour.paint(): Color = ChipDenominations.getChipByValue(standardValue)?.color ?: Color.Gray

/** "1,000". */
internal fun chipNumber(value: Int): String = ChipSetText.number(value)

/** "Green", "Light blue". */
@Composable
internal fun colourName(colour: ChipColour): String = stringArrayResource(R.array.chip_set_colour_names)[colour.ordinal]

/** "green", "light blue": in running text. */
@Composable
internal fun colourAdjective(colour: ChipColour): String = stringArrayResource(R.array.chip_set_colour_adjectives)[colour.ordinal]

/** "black" for one chip, "blacks" for more. */
@Composable
internal fun colourNoun(colour: ChipColour, count: Int): String =
    stringArrayResource(if (count == 1) R.array.chip_set_colour_one else R.array.chip_set_colour_other)[colour.ordinal]

/** "Green 25". */
@Composable
internal fun chipName(colour: ChipColour, value: Int): String =
    stringResource(R.string.chip_set_chip_name, colourName(colour), chipNumber(value))

/** "Green", "Green and black", "Green, black and purple". */
@Composable
internal fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.first()
    else -> stringResource(R.string.chip_set_and, items.dropLast(1).joinToString(", "), items.last())
}

/** "Short 27 black 100s for 9 players." (or "for 12 stacks" with a reserve). */
@Composable
internal fun shortfallText(fix: ChipShortfall, players: Int): String = pluralStringResource(
    if (fix.stacks == players) R.plurals.chip_set_short_players else R.plurals.chip_set_short_stacks,
    fix.more,
    fix.more,
    colourAdjective(fix.colour),
    chipNumber(fix.value),
    fix.stacks,
)

/** Why no stack can be planned, and what to do about it. */
@Composable
internal fun problemText(problem: StackPlan.Unplannable): String = when (problem) {
    StackPlan.NoChips -> stringResource(R.string.chip_set_no_chips)
    is StackPlan.NoChipForSmallBlind -> stringResource(R.string.chip_set_no_small_blind_chip, chipNumber(problem.smallBlind))
    is StackPlan.StackSmallerThanSmallestChip ->
        stringResource(R.string.chip_set_stack_too_small, chipNumber(problem.stack), chipNumber(problem.smallestChip))
    is StackPlan.StackNotReachable -> stringResource(
        R.string.chip_set_not_reachable,
        chipNumber(problem.stack),
        chipNumber(problem.unit),
        chipNumber(problem.nearestBelow),
        chipNumber(problem.nearestAbove),
    )
    is StackPlan.StackTooBig -> stringResource(R.string.chip_set_too_big, chipNumber(problem.stack), chipNumber(problem.unit))
    is StackPlan.InvalidInput -> when (problem.field) {
        StackPlan.Field.PLAYERS -> stringResource(R.string.chip_set_set_players)
        StackPlan.Field.SMALL_BLIND -> stringResource(R.string.chip_set_set_smallest_chip)
        StackPlan.Field.STARTING_STACK, StackPlan.Field.RESERVE -> stringResource(R.string.chip_set_set_stack)
    }
}
