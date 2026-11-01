package com.huntercoles.pokerpayout.tournament.presentation.payouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.PokerType

/**
 * The winner's name and the amount on one line, or (#49), when a word of the name and the amount
 * can't share the line, the name on its own line with the amount under it, so no name breaks
 * mid-word ("Bartholomew-Ash / ford") at a small width or a large font. A hyphenated name may wrap
 * after its hyphen.
 */
@Composable
internal fun NameAndAmount(holder: String, amount: String, nameColor: Color, amountColor: Color) {
    val nameStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gap = PokerDimens.SpacingSmall
    // A zero-width space after each hyphen lets "Bartholomew-Ashford" wrap there and nowhere else.
    val name = holder.replace("-", "-\u200B")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = remember(holder, amount, nameStyle, maxWidth, density) {
            val widestWord = holder.split(' ', '-')
                .maxOfOrNull { measurer.measure("$it-", nameStyle, maxLines = 1, softWrap = false).size.width } ?: 0
            val amountWidth = measurer.measure(amount, PokerType.NumberL, maxLines = 1, softWrap = false).size.width
            with(density) { widestWord.toDp() + gap + amountWidth.toDp() > maxWidth }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                Text(text = name, style = nameStyle, color = nameColor)
                Text(
                    text = amount,
                    style = PokerType.NumberL,
                    color = amountColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Text(
                    text = name,
                    style = nameStyle,
                    color = nameColor,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
                Text(
                    text = amount,
                    style = PokerType.NumberL,
                    color = amountColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}
