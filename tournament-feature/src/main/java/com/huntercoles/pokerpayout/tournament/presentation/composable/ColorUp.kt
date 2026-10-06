package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.design.components.chipColourWord
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipSetChips
import com.huntercoles.pokerpayout.tournament.R
import com.huntercoles.pokerpayout.tournament.domain.clock.ColorUpSwap

/**
 * The swap as chips: four green 25s, an arrow, one black 100. Ratios above [MAX_DRAWN] chips draw
 * one chip with its count instead. TalkBack reads "4 green 25s for 1 black 100". With your chip set
 * ([chipSet], PP-091 #9) the chips are drawn and named in its colours: "4 white 25s for 1 red 100".
 */
@Composable
internal fun ColorUpExchange(swap: ColorUpSwap, chipSet: ChipSetChips?) {
    val ratio = swap.into / swap.chip
    val description = stringResource(
        R.string.break_color_up_chips,
        ratio,
        chipPhrase(swap.chip, plural = true, chipSet),
        chipPhrase(swap.into, plural = false, chipSet),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ratio <= MAX_DRAWN) {
                repeat(ratio) { SetChip(swap.chip, chipSet, SmallChip) }
            } else {
                Text("$ratio ×", style = PokerType.NumberM, color = PokerColors.CardWhite)
                SetChip(swap.chip, chipSet, SmallChip)
            }
        }
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(28.dp))
        SetChip(swap.into, chipSet, BigChip)
    }
}

/** 1. Each player swaps every four green 25s for one black 100. 2. Odd 25s go to a chip race. */
@Composable
internal fun ColorUpSteps(swaps: List<ColorUpSwap>, chipSet: ChipSetChips?) {
    if (swaps.isEmpty()) return
    val formatter = rememberChipFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        swaps.forEachIndexed { index, swap ->
            val many = chipPhrase(swap.chip, plural = true, chipSet)
            val one = chipPhrase(swap.into, plural = false, chipSet)
            val text = stringResource(R.string.break_color_up_swap, swap.into / swap.chip, many, one)
            StepLine(index + 1, text, listOf(many, one))
        }
        val odd = chipList(swaps.map { it.chip }, formatter)
        val won = chipList(swaps.map { it.into }.distinct(), formatter)
        StepLine(swaps.size + 1, stringResource(R.string.break_color_up_race, odd, won), emptyList())
    }
}

/** A chip drawn in your set's colour for its value, or in the standard colour without a chip set. */
@Composable
private fun SetChip(value: Int, chipSet: ChipSetChips?, size: Dp) {
    PokerChip(value, size = size, color = chipSet?.let { chipPaint(value, it) })
}

@Composable
private fun StepLine(number: Int, text: String, bold: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = number.toString(),
            style = PokerType.NumberM.copy(fontSize = PokerType.NumberS.fontSize),
            color = PokerColors.PokerGold,
            modifier = Modifier.widthIn(min = 14.dp),
        )
        Text(
            text = buildAnnotatedString {
                append(text)
                bold.forEach { phrase ->
                    val at = text.indexOf(phrase)
                    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + phrase.length)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
        )
    }
}

/** Your set's colour for [value] as a standard chip of that colour; null when your set has none. */
private fun chipPaint(value: Int, chipSet: ChipSetChips): Color? =
    chipSet.colourOf(value)?.let { ChipDenominations.getChipByValue(it.standardValue)?.color }

/**
 * "green 25s" or "black 100": the chip by the colour players know it by, in your set when you have
 * one ("white 25s" where your 25s are white), else the standard chip's.
 */
@Composable
private fun chipPhrase(value: Int, plural: Boolean, chipSet: ChipSetChips?): String {
    val formatter = rememberChipFormatter()
    val colour = (chipSet?.colourOf(value) ?: ChipColour.forStandardValue(value))?.let { chipColourWord(it) }.orEmpty()
    val amount = formatter.format(value)
    return when {
        colour.isEmpty() && plural -> stringResource(R.string.break_chips_number_plural, amount)
        colour.isEmpty() -> amount
        plural -> stringResource(R.string.break_chips_plural, colour, amount)
        else -> stringResource(R.string.break_chip_single, colour, amount)
    }
}

/** At most this many small chips are drawn for one swap (25 → 100 draws four). */
private const val MAX_DRAWN = 4
private val SmallChip = 34.dp
private val BigChip = 44.dp
