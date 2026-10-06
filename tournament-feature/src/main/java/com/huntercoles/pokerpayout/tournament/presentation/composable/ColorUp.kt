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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.ChipDenominations
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.design.components.PokerChip
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.core.utils.ColorUpPlanner
import com.huntercoles.pokerpayout.tournament.R
import java.util.Locale

/** The chip a color-up turns [chip] into: the next one up that it divides (25 → 100), or null. */
internal fun colorUpTarget(chip: Int): Int? = ColorUpPlanner.nextChipUp(chip)

/**
 * The swap as chips: four green 25s, an arrow, one black 100. Ratios above [MAX_DRAWN] chips draw
 * one chip with its count instead. TalkBack reads "4 green 25s for 1 black 100".
 */
@Composable
internal fun ColorUpExchange(chip: Int) {
    val target = colorUpTarget(chip) ?: return
    val ratio = target / chip
    val description = stringResource(
        R.string.break_color_up_chips,
        ratio,
        chipPhrase(chip, plural = true),
        chipPhrase(target, plural = false),
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
                repeat(ratio) { PokerChip(chip, size = SmallChip) }
            } else {
                Text("$ratio ×", style = PokerType.NumberM, color = PokerColors.CardWhite)
                PokerChip(chip, size = SmallChip)
            }
        }
        Icon(PokerIcons.ChevronRight, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(28.dp))
        PokerChip(target, size = BigChip)
    }
}

/** 1. Each player swaps every four green 25s for one black 100. 2. Odd 25s go to a chip race. */
@Composable
internal fun ColorUpSteps(chips: List<Int>) {
    val steps = chips.mapNotNull { chip ->
        val target = colorUpTarget(chip) ?: return@mapNotNull null
        ColorUpStep(chip, target)
    }
    if (steps.isEmpty()) return
    val formatter = rememberChipFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            val many = chipPhrase(step.chip, plural = true)
            val one = chipPhrase(step.target, plural = false)
            val swap = stringResource(R.string.break_color_up_swap, step.target / step.chip, many, one)
            StepLine(index + 1, swap, listOf(many, one))
        }
        val odd = chipList(steps.map { it.chip }, formatter)
        val won = chipList(steps.map { it.target }.distinct(), formatter)
        StepLine(steps.size + 1, stringResource(R.string.break_color_up_race, odd, won), emptyList())
    }
}

private class ColorUpStep(val chip: Int, val target: Int)

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

/** "green 25s" or "black 100": the chip by the colour players know it by. */
@Composable
private fun chipPhrase(value: Int, plural: Boolean): String {
    val formatter = rememberChipFormatter()
    val colour = ChipDenominations.getChipByValue(value)?.name?.lowercase(Locale.ROOT).orEmpty()
    val amount = formatter.format(value)
    return when {
        colour.isEmpty() && plural -> "${amount}s"
        colour.isEmpty() -> amount
        plural -> stringResource(R.string.break_chips_plural, colour, amount)
        else -> stringResource(R.string.break_chip_single, colour, amount)
    }
}

/** At most this many small chips are drawn for one swap (25 → 100 draws four). */
private const val MAX_DRAWN = 4
private val SmallChip = 34.dp
private val BigChip = 44.dp
