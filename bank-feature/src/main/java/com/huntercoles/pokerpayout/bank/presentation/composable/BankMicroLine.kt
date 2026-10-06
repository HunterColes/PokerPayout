package com.huntercoles.pokerpayout.bank.presentation.composable

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.bank.R
import com.huntercoles.pokerpayout.bank.presentation.BankColumn
import com.huntercoles.pokerpayout.bank.presentation.BankRowModel
import com.huntercoles.pokerpayout.bank.presentation.BankSection
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.components.mayTruncate
import com.huntercoles.pokerpayout.core.design.icons.MoneyIcons
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/**
 * The line under a name, as one text with small icons in it: knockouts (☠ 2), who knocked the
 * player out and when ("by Marcus · L5"), "1st · champion", and on a small phone the counts of
 * columns that closed and moved here (↻ 1 · + 1). One line, ending in "…" if it must; above a 1.3
 * font scale it may wrap to a second.
 */
@Composable
internal fun MicroLine(row: BankRowModel, layout: BankLayout) {
    val color = when (row.section) {
        BankSection.OUT -> PokerColors.Danger
        BankSection.CHAMPION -> PokerColors.PokerGold
        BankSection.PLAYING -> PokerColors.Chalk
    }
    val parts = microParts(row, layout)
    if (parts.isEmpty()) return
    val separator = " · "
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(separator)
            part.icon?.let { icon ->
                appendInlineContent(icon.name, "•")
                append(" ")
            }
            append(part.text)
        }
    }
    val icons = parts.mapNotNull { it.icon }.distinct()
    val inline = icons.associate { icon ->
        icon.name to InlineTextContent(Placeholder(1.1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(icon.vector, contentDescription = null, tint = icon.tint ?: color)
        }
    }
    val wraps = LocalDensity.current.fontScale > MICRO_WRAPS_ABOVE
    Text(
        text = text,
        inlineContent = inline,
        style = MicroStyle,
        color = color,
        maxLines = if (wraps) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics {
            mayTruncate = true
            contentDescription = parts.joinToString(", ") { it.spoken ?: it.text }
        },
    )
}

private const val MICRO_WRAPS_ABOVE = 1.3f

/** One piece of the micro line: words, an optional icon before them, and what TalkBack says instead. */
private class MicroPart(val text: String, val icon: MicroIcon? = null, val spoken: String? = null)

private enum class MicroIcon(val vector: ImageVector, val tint: Color?) {
    Skull(PokerIcons.Skull, PokerColors.PokerGold),
    Renew(MoneyIcons.Renew, null),
    Plus(PokerIcons.Plus, null),
}

@Composable
private fun microParts(row: BankRowModel, layout: BankLayout): List<MicroPart> =
    placeParts(row, layout) + listOfNotNull(knockoutPart(row, layout)) + collapsedParts(row, layout)

/** "1st · champion", or who knocked the player out and in which level ("by Marcus · L5"). */
@Composable
private fun placeParts(row: BankRowModel, layout: BankLayout): List<MicroPart> = when (row.section) {
    BankSection.CHAMPION -> listOf(MicroPart(stringResource(R.string.bank_micro_champion)))
    BankSection.OUT -> {
        val by = row.knockedOutByName?.let { stringResource(R.string.bank_micro_by, it) }
            ?: stringResource(R.string.bank_micro_out)
        val level = if (layout.showAmounts) R.string.bank_micro_level_long else R.string.bank_micro_level
        listOfNotNull(MicroPart(by), row.outAtLevel?.let { MicroPart(stringResource(level, it)) })
    }
    BankSection.PLAYING -> emptyList()
}

/** The player's knockouts (☠ 2), spelled out where there is room. */
@Composable
private fun knockoutPart(row: BankRowModel, layout: BankLayout): MicroPart? {
    if (row.knockouts <= 0) return null
    val spoken = pluralStringResource(R.plurals.bank_micro_knockouts_long, row.knockouts, row.knockouts)
    val shown = if (layout.showAmounts) spoken else stringResource(R.string.bank_micro_knockouts, row.knockouts)
    return MicroPart(shown, MicroIcon.Skull, spoken)
}

/** On a small phone, the counts of the columns that closed and moved here (↻ 1 · + 1). */
@Composable
private fun collapsedParts(row: BankRowModel, layout: BankLayout): List<MicroPart> =
    layout.collapsed.mapNotNull { column ->
        val rebuy = column == BankColumn.REBUY
        val count = if (rebuy) row.rebuys else row.addOns
        val spoken = pluralStringResource(if (rebuy) R.plurals.bank_micro_rebuys else R.plurals.bank_micro_add_ons, count, count)
        MicroPart(count.toString(), if (rebuy) MicroIcon.Renew else MicroIcon.Plus, spoken).takeIf { count > 0 }
    }

private val MicroStyle = TextStyle(fontSize = 11.5.sp, lineHeight = 14.sp)
