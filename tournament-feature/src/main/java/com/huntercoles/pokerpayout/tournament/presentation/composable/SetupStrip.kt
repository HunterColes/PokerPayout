package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons
import com.huntercoles.pokerpayout.tournament.R

/**
 * S1 v2: setup folded into one 48 dp line above the clock ("9 players · $40 buy-in · 20-min levels ·
 * 5,000 chips  Setup ⌄"). Tapping it unfolds the setup panel over the clock, which keeps running. It
 * is the visible way back into setup (it replaces the header's sliders icon).
 *
 * The line shows as many of [parts] as fit, whole, from the first: never "20-min le…" (the device
 * matrix found that at 320 dp). If even the first doesn't fit (the largest text), it wraps. TalkBack
 * reads all of them.
 */
@Composable
internal fun SetupStrip(parts: List<String>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PokerDimens.CornerControl)
    val separator = SetupSummary.stripSeparator()
    val description = stringResource(R.string.strip_description, parts.joinToString(separator))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PokerDimens.ControlHeight)
            .clip(shape)
            .background(PokerColors.FeltGreen)
            .border(1.dp, PokerColors.FeltEdge, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(PokerIcons.Tune, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.size(18.dp))
        WithWidth(Modifier.weight(1f)) { width ->
            Text(
                text = fittingSummary(parts, separator, width),
                style = MaterialTheme.typography.bodyMedium,
                color = PokerColors.CardWhite,
            )
        }
        Text(
            text = stringResource(R.string.strip_setup),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = PokerColors.PokerGold,
        )
        Icon(PokerIcons.ChevronDown, contentDescription = null, tint = PokerColors.PokerGold, modifier = Modifier.size(18.dp))
    }
}

/** The longest run of [parts] from the first that fits [width] on one line, joined by [separator]. */
@Composable
private fun fittingSummary(parts: List<String>, separator: String, width: Dp): String {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium // as the Text draws it
    val available = with(LocalDensity.current) { width.roundToPx() }
    return remember(parts, separator, style, available, measurer) {
        fittingPrefix(parts, separator) { line ->
            measurer.measure(line, style, softWrap = false, maxLines = 1).size.width <= available
        }
    }
}

/**
 * The longest run of [parts] from the first, joined by [separator], that [fits]; the first part
 * alone when none does (it then wraps), and "" when there are none.
 */
internal fun fittingPrefix(parts: List<String>, separator: String, fits: (String) -> Boolean): String {
    var shown = parts.firstOrNull().orEmpty()
    for (count in 2..parts.size) {
        val line = parts.take(count).joinToString(separator)
        if (!fits(line)) break
        shown = line
    }
    return shown
}
