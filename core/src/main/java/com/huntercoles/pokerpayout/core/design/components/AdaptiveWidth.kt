package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide the app's window is, from the window itself (no window-size-class dependency):
 * - [Small]: under 360 dp, small phones (12 dp gutters, Z1 and Z2).
 * - [Phone]: 360 to 599 dp, the layouts as drawn.
 * - [Medium]: 600 to 839 dp, foldables, small tablets and phones on their side.
 * - [Expanded]: 840 dp and up, 10 inch tablets in landscape (two panes, Z4 and Z5).
 */
enum class WidthClass { Small, Phone, Medium, Expanded }

fun widthClassOf(width: Dp): WidthClass = when {
    width < PhoneMinWidth -> WidthClass.Small
    width < RailMinWidth -> WidthClass.Phone
    width < ExpandedMinWidth -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** The window's width class, provided by [PokerAppShell]. Defaults to [WidthClass.Phone] elsewhere. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.Phone }

/** Where the four tabs live: a bottom bar, or a rail down the left side. */
enum class NavLayout { BottomBar, Rail }

/**
 * A bottom bar on phones held upright; a rail from 600 dp wide (PP-087), where a bottom bar would
 * stretch four tabs across the screen and cost height that a phone on its side doesn't have.
 */
fun navLayoutFor(width: Dp): NavLayout = if (width >= RailMinWidth) NavLayout.Rail else NavLayout.BottomBar

private val PhoneMinWidth = 360.dp

/** From here the tabs move to a rail (PP-087, Material's medium width class). */
val RailMinWidth: Dp = 600.dp
private val ExpandedMinWidth = 840.dp

/**
 * The widest a single-column screen gets; wider windows centre it. 720 dp keeps the 16 dp-gutter
 * phone layouts to a readable line length (about 90 characters of body text) and their rows short
 * enough to scan, and it is the width the design centres tablet screens at (design spec, section 4).
 * It only bites on wide windows: a tablet held upright is 800 dp, which leaves 704 dp next to the
 * rail, so it keeps the full width.
 */
val ContentMaxWidth: Dp = 720.dp
