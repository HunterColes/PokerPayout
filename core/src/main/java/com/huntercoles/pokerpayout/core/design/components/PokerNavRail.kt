package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors

/** Test tag of the rail, so a test can tell it from the bottom bar. */
const val POKER_NAV_RAIL_TAG = "PokerNavRail"

/**
 * The navigation rail that replaces [PokerNavBar] from 600 dp wide (PP-087): a 96 dp FeltDeep
 * column down the left edge with a thin DarkGold line on its right, the same four tabs top down,
 * and the same selected look (gold icon and label on a FeltHigh pill). Every tab is a full-width
 * touch target at least 56 dp tall and a TalkBack tab.
 *
 * The tabs start near the top, as Material's rail does. On a short window (a small phone on its
 * side) the spacing tightens so all four still fit, and only if they still don't (the largest font
 * sizes) does the rail scroll, rather than cut a tab off. Labels shrink to fit the rail together, as
 * in the bar.
 */
@Composable
fun PokerNavRail(
    items: List<PokerNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelStyle = rememberFittedStyle(NavLabel, items.map { it.label }, RailWidth - RailTabPadding * 2, floor = NavLabelMin)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .background(PokerColors.FeltDeep)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(
                    color = PokerColors.DarkGold.copy(alpha = RAIL_ALPHA),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .width(RailWidth)
            .testTag(POKER_NAV_RAIL_TAG),
    ) {
        RailColumn(viewportHeight = maxHeight) {
            items.forEachIndexed { index, item ->
                RailTab(
                    item = item,
                    selected = index == selectedIndex,
                    labelStyle = labelStyle,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/**
 * The tabs from the top, 20 dp in and 12 dp apart when the window has the room. When it doesn't,
 * both shrink together, down to nothing; only when the tabs alone are taller than the window does
 * the column scroll.
 */
@Composable
private fun RailColumn(viewportHeight: Dp, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
    ) { measurables, constraints ->
        val tabs = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
        val viewport = viewportHeight.roundToPx()
        val tabsHeight = tabs.sumOf { it.height }
        val gaps = (tabs.size - 1).coerceAtLeast(0)
        val wanted = RailTopPadding.roundToPx() * 2 + RailGap.roundToPx() * gaps
        val share = if (wanted == 0) 0f else ((viewport - tabsHeight).toFloat() / wanted).coerceIn(0f, 1f)
        val top = (RailTopPadding.roundToPx() * share).toInt()
        val gap = (RailGap.roundToPx() * share).toInt()
        val height = maxOf(viewport, tabsHeight + top * 2 + gap * gaps)
        layout(constraints.maxWidth, height) {
            var y = top
            tabs.forEach { tab ->
                tab.placeRelative(0, y)
                y += tab.height + gap
            }
        }
    }
}

@Composable
private fun RailTab(item: PokerNavItem, selected: Boolean, labelStyle: TextStyle, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val color = if (selected) PokerColors.PokerGold else PokerColors.Chalk
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = RailTabMinHeight)
            .padding(horizontal = RailTabPadding, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 32.dp)
                .background(if (selected) PokerColors.FeltHigh else PokerColors.FeltDeep, CircleShape)
                .indication(interactions, ripple(bounded = false, radius = 30.dp, color = color)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = color)
        }
        Text(
            text = item.label,
            color = color,
            style = labelStyle,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

/** The rail's width, not counting a display cutout or button bar on its side. */
val RailWidth = 96.dp
private val RailTabPadding = 4.dp
private val RailTabMinHeight = 56.dp
private val RailTopPadding = 20.dp
private val RailGap = 12.dp

@Preview(name = "PokerNavRail", widthDp = 360, heightDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerNavRailPreview() {
    PokerPreviewPage(gutter = false) {
        Row(Modifier.height(320.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PokerNavRail(items = pokerNavItems(), selectedIndex = 0, onSelect = {})
            PokerNavRail(items = pokerNavItems(firstTabLabel = "Clock"), selectedIndex = 3, onSelect = {})
        }
    }
}
