package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerType
import com.huntercoles.pokerpayout.core.utils.ChipColour
import com.huntercoles.pokerpayout.core.utils.ChipShortfall
import com.huntercoles.pokerpayout.core.utils.PlayerStack
import com.huntercoles.pokerpayout.core.utils.ReserveCheck
import com.huntercoles.pokerpayout.core.utils.StackChip
import com.huntercoles.pokerpayout.core.utils.StackPlan
import com.huntercoles.pokerpayout.tools.R
import com.huntercoles.pokerpayout.tools.presentation.ChipSetText
import com.huntercoles.pokerpayout.tools.presentation.ChipSetUiState
import kotlin.math.floor

/** "Each player gets": the stack as piles of chips, its totals, and the reserve check. */
@Composable
internal fun StackCard(state: ChipSetUiState) {
    val stackText = chipNumber(state.startingStack)
    ChipSetSection {
        SectionHeader(
            title = stringResource(R.string.chip_set_each_player),
            note = if (state.stackFromTournament) {
                stringResource(R.string.chip_set_stack_from_tournament, stackText)
            } else {
                stringResource(R.string.chip_set_stack_own, stackText)
            },
        )
        when (val plan = state.plan) {
            null -> SmallNote(stringResource(R.string.chip_set_working))
            is StackPlan.Ready -> {
                StackPicture(plan.stack)
                StackTotals(plan.stack, state.settings.maxColours)
                ReserveNote(plan.reserve, state.players)
            }
            is StackPlan.Short -> ShortNote(plan, state)
            is StackPlan.Unplannable -> NoteBox(ok = false) {
                Text(problemText(plan), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Danger)
            }
        }
    }
}

/** What is missing, how many stacks the set makes now, and the stack once the missing chips are in. */
@Composable
private fun ShortNote(plan: StackPlan.Short, state: ChipSetUiState) {
    val fix = plan.shortfall
    NoteBox(ok = false) {
        Text(
            text = if (fix != null) shortfallText(fix, state.players) else stringResource(R.string.chip_set_no_single_fix),
            style = MaterialTheme.typography.titleSmall,
            color = PokerColors.Danger,
        )
        Text(
            text = if (plan.stacksYouCanMake > 0) {
                val made = plan.stacksYouCanMake
                pluralStringResource(R.plurals.chip_set_can_make, made, made, chipNumber(state.startingStack))
            } else {
                stringResource(R.string.chip_set_can_make_none, chipNumber(state.startingStack))
            },
            style = MaterialTheme.typography.bodySmall,
            color = PokerColors.CardWhite,
        )
    }
    val fixed = plan.stackIfAdded
    if (fix != null && fixed != null) {
        SmallNote(stringResource(R.string.chip_set_with_more, fix.more))
        StackPicture(fixed, short = fix)
        StackTotals(fixed, state.settings.maxColours)
    }
}

/** "22 chips a stack · 4 colours · 5,000", and why the stack bends the settings, if it does. */
@Composable
private fun StackTotals(stack: PlayerStack, maxColours: Int) {
    SmallNote(
        pluralStringResource(
            R.plurals.chip_set_totals,
            stack.totalChips,
            stack.totalChips,
            stack.chips.size,
            ChipFormat.number(stack.totalValue),
        )
    )
    if (stack.moreColoursThanAsked) {
        // Only when the colour limit couldn't hold: the stack has more colours than asked
        SmallNote(pluralStringResource(R.plurals.chip_set_more_colours, maxColours, stack.chips.size, maxColours))
    }
    if (stack.shapeRelaxed) {
        SmallNote(stringResource(R.string.chip_set_shape_relaxed))
    }
}

/** How many more stacks the box makes after every player has one, and which colours run out. */
@Composable
private fun ReserveNote(reserve: ReserveCheck, players: Int) {
    NoteBox(ok = reserve.enough) {
        val left = if (reserve.extraStacks > 0) {
            pluralStringResource(R.plurals.chip_set_reserve_left, reserve.extraStacks, reserve.extraStacks, players)
        } else {
            stringResource(R.string.chip_set_reserve_none, players)
        }
        val kept = when {
            reserve.requested == 0 -> null
            reserve.enough -> stringResource(R.string.chip_set_reserve_kept, reserve.requested)
            else -> stringResource(R.string.chip_set_reserve_short, reserve.requested)
        }
        val first = reserve.runsOutFirst.map { colourAdjective(it) }
        val firstText = joinWithAnd(first).replaceFirstChar { it.uppercase() }
        val runsOut = pluralStringResource(R.plurals.chip_set_runs_out_first, first.size, firstText)
        Text(
            text = listOfNotNull(left, kept, runsOut.takeIf { first.isNotEmpty() }).joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            color = PokerColors.CardWhite,
        )
        reserve.shortfall?.let { fix ->
            Text(shortfallText(fix, players), style = MaterialTheme.typography.bodyMedium, color = PokerColors.Danger)
        }
    }
}

/** A line of small Chalk text under the stack. */
@Composable
private fun SmallNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk)
}

/** Columns of piles grow with large text; a column never gets narrower than this at 100%. */
private val PileColumnWidth = 68.dp

/** Large text widens the columns, though by less than the text grows (Android scales big text less). */
private const val PILE_TEXT_SHARE = 0.6f

/**
 * One player's stack, smallest chip first, as side-on piles: "8 × 25" and "200" under each. With
 * [short], that colour's column says how many more it needs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StackPicture(stack: PlayerStack, short: ChipShortfall? = null) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val column = PileColumnWidth * (1f + (fontScale - 1f).coerceAtLeast(0f) * PILE_TEXT_SHARE)
        val perRow = floor(maxWidth / column).toInt().coerceIn(1, stack.chips.size.coerceAtLeast(1))
        // Every pile stands on the same line, as tall as the tallest one needs
        val discs = stack.chips.maxOfOrNull { it.count.coerceIn(1, MAX_DISCS) } ?: 1
        val pileHeight = DiscHeight + DiscStep * (discs - 1)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = perRow,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stack.chips.forEach { chip ->
                PileColumn(chip, needed = short?.takeIf { it.colour == chip.colour }?.more, pileHeight, Modifier.weight(1f))
            }
            // Keep the last row's columns as wide as the rest
            repeat((perRow - stack.chips.size % perRow) % perRow) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PileColumn(chip: StackChip, needed: Int?, pileHeight: Dp, modifier: Modifier) {
    val colour = colourName(chip.colour)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChipPile(
            colour = chip.colour,
            count = chip.count,
            modifier = Modifier
                .size(PileWidth, pileHeight)
                .semantics { contentDescription = colour },
        )
        Text(
            text = stringResource(R.string.chip_set_pile_count, chip.count, ChipFormat.short(chip.value)),
            style = PokerType.NumberM,
            color = if (needed != null) PokerColors.Danger else PokerColors.CardWhite,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (needed != null) stringResource(R.string.chip_set_pile_more, needed) else ChipFormat.number(chip.worth),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = if (needed != null) PokerColors.Danger else PokerColors.Chalk,
            textAlign = TextAlign.Center,
        )
    }
}

private val PileWidth = 46.dp
private val DiscHeight = 7.dp
private val DiscStep = 6.dp

/** At most this many discs are drawn; the caption under the pile has the real count. */
private const val MAX_DISCS = 10

/** A pile of [count] chips seen from the side: striped discs, the bottom one on the felt. */
@Composable
private fun ChipPile(colour: ChipColour, count: Int, modifier: Modifier) {
    val base = colour.paint()
    val stripe = if (colour == ChipColour.White) WhiteChipStripe else ChipStripe
    Canvas(modifier) {
        val discs = count.coerceIn(0, MAX_DISCS)
        repeat(discs) { i ->
            val top = size.height - DiscHeight.toPx() - i * DiscStep.toPx()
            drawDisc(base, stripe, top, shifted = i % 2 == 1)
        }
    }
}

private fun DrawScope.drawDisc(base: Color, stripe: Color, top: Float, shifted: Boolean) {
    val height = DiscHeight.toPx()
    val corner = CornerRadius(3.dp.toPx())
    drawRoundRect(Color.Black.copy(alpha = SHADOW_ALPHA), Offset(0f, top + 1.dp.toPx()), Size(size.width, height), corner)
    drawRoundRect(base, Offset(0f, top), Size(size.width, height), corner)
    // Edge spots: 7 dp of colour, 3 dp of white, offset on every other disc
    val period = 10.dp.toPx()
    val spot = 3.dp.toPx()
    clipRect(left = 2.dp.toPx(), top = top, right = size.width - 2.dp.toPx(), bottom = top + height) {
        var x = 7.dp.toPx() - if (shifted) 5.dp.toPx() else 0f
        while (x < size.width) {
            drawRect(stripe, Offset(x, top), Size(spot, height))
            x += period
        }
    }
    drawRect(Color.White.copy(alpha = HIGHLIGHT_ALPHA), Offset(2.dp.toPx(), top), Size(size.width - 4.dp.toPx(), 1.dp.toPx()))
}

private val ChipStripe = Color(0xFFE9E9E9)
private val WhiteChipStripe = Color(0xFF2E7D9A)
private const val SHADOW_ALPHA = 0.65f
private const val HIGHLIGHT_ALPHA = 0.25f

/** Chip numbers as the chip set writes them. */
internal object ChipFormat {
    /** "1,000". */
    fun number(value: Long): String = ChipSetText.number(value)

    /** "25", "100", "1K", "2.5K", "1M": as the chips are printed. */
    fun short(value: Int): String = when {
        value >= MILLION && value % MILLION == 0 -> "${value / MILLION}M"
        value >= THOUSAND && value % THOUSAND == 0 -> "${value / THOUSAND}K"
        value >= THOUSAND && value % HALF_THOUSAND == 0 -> "${value / THOUSAND}.5K"
        else -> chipNumber(value)
    }

    private const val THOUSAND = 1_000
    private const val HALF_THOUSAND = 500
    private const val MILLION = 1_000_000
}
