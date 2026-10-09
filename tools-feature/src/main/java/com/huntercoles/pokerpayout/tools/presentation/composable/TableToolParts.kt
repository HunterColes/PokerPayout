package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.components.PokerTopBar
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/** Below the phone width, gutters narrow to 12 dp (design spec, section 4). */
private val SmallWidth = 360.dp
private val SmallGutter = 12.dp

/** From this width what you type and what it works out sit side by side, each scrolling on its own. */
private val TwoPaneWidth = 600.dp

/**
 * A table tool's page (Side pots, Deal maker, Outs & pot odds): the top bar, then [inputs] and
 * [results] in one scrolling column, or side by side from 600 dp wide (tablets, phones on their
 * side) so the answer stays in view while you type.
 */
@Suppress("LongParameterList") // a page: its bar's three parts, its two panes and a modifier
@Composable
internal fun TableToolPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    inputs: @Composable ColumnScope.() -> Unit,
    results: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(PokerColors.PokerBlack)) {
        PokerTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < SmallWidth) SmallGutter else PokerDimens.Gutter
            if (maxWidth >= TwoPaneWidth) {
                Row(Modifier.fillMaxSize().padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolPane(0.dp, Modifier.weight(1f), inputs)
                    ToolPane(0.dp, Modifier.weight(1f), results)
                }
            } else {
                ToolPane(gutter, Modifier.fillMaxSize()) {
                    inputs()
                    results()
                }
            }
        }
    }
}

/** One scrolling column of cards. */
@Composable
private fun ToolPane(gutter: Dp, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, top = 4.dp, bottom = PokerDimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * A dark well inside a card: one player's fields, or one result. [merged] reads a result to
 * TalkBack in one go ("Main pot, 900, ...").
 */
@Composable
internal fun ToolWell(merged: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokerColors.FeltDeep, RoundedCornerShape(PokerDimens.CornerControl))
            .then(if (merged) Modifier.semantics(mergeDescendants = true) {} else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** A Chalk line with an icon, an info sign by default: how something is worked out. */
@Composable
internal fun ToolNote(text: String, modifier: Modifier = Modifier, icon: ImageVector = PokerIcons.Info) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = PokerColors.Chalk, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokerColors.Chalk, modifier = Modifier.weight(1f))
    }
}
