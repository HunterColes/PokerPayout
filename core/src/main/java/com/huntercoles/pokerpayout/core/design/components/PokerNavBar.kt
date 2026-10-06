@file:Suppress("MatchingDeclarationName") // PokerNavItem is the one class; PokerNavBar the component

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.design.icons.PokerIcons

/** A bottom-nav destination: a word and an icon (the word is always shown). */
@Immutable
data class PokerNavItem(val label: String, val icon: ImageVector)

/**
 * The app's four destinations in order: the tournament clock, Bank, Payouts and Tools (D1).
 * [firstTabLabel] names the first tab: "Tournament" today; D3 may rename it "Clock".
 */
@Composable
fun pokerNavItems(firstTabLabel: String = stringResource(R.string.navigation_tournament)): List<PokerNavItem> = listOf(
    PokerNavItem(firstTabLabel, PokerIcons.Timer),
    PokerNavItem(stringResource(R.string.navigation_bank), PokerIcons.Wallet),
    PokerNavItem(stringResource(R.string.navigation_payouts), PokerIcons.Trophy),
    PokerNavItem(stringResource(R.string.navigation_tools), PokerIcons.Wrench),
)

/**
 * The bottom navigation bar: FeltDeep with a thin DarkGold rail on top. The selected tab gets a
 * gold icon and label on a FeltHigh pill (5.1:1). Sub-screens keep their tab selected (Tools
 * stays selected inside a tool). Each tab is a full-height touch target and a TalkBack tab.
 *
 * Labels grow with the font size until the longest fills its tab; then all four hold at that size
 * (never below 12 sp unscaled) instead of truncating.
 */
@Composable
fun PokerNavBar(
    items: List<PokerNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(PokerColors.FeltDeep)
            .drawBehind {
                drawLine(
                    color = PokerColors.DarkGold.copy(alpha = RAIL_ALPHA),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val tabWidth = maxWidth / items.size.coerceAtLeast(1) - TabPadding * 2
        val labelStyle = rememberFittedStyle(NavLabel, items.map { it.label }, tabWidth, floor = NavLabelMin)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PokerDimens.NavBarHeight)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavTab(
                    item = item,
                    selected = index == selectedIndex,
                    labelStyle = labelStyle,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    item: PokerNavItem,
    selected: Boolean,
    labelStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactions = remember { MutableInteractionSource() }
    val color = if (selected) PokerColors.PokerGold else PokerColors.Chalk
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = PokerDimens.NavBarHeight)
            .padding(horizontal = TabPadding, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 30.dp)
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

private const val RAIL_ALPHA = 0.55f
private val TabPadding = 2.dp

/** Labels shrink to fit their tab at large font sizes, but never below their unscaled 12 sp. */
private val NavLabelMin = 12.dp
private val NavLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)

@Preview(name = "PokerNavBar", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
internal fun PokerNavBarPreview() {
    PokerPreviewPage(gutter = false) {
        PokerNavBar(items = pokerNavItems(), selectedIndex = 0, onSelect = {})
        PokerNavBar(items = pokerNavItems(firstTabLabel = "Clock"), selectedIndex = 3, onSelect = {})
    }
}
