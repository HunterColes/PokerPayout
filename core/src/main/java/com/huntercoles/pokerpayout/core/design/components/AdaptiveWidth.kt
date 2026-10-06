package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
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

/**
 * The width the shell has for the screen before the [ContentMaxWidth] cap. [PokerAppShell] records
 * it while measuring and offers it as [LocalShellWidth]; [fillShellWidth] reads it while measuring,
 * so a rotation only relays out.
 */
class ShellWidth {
    private var widthPx by mutableIntStateOf(UNKNOWN)

    /** The shell's own measure: records the width, then lays the screen out as before. */
    fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        if (constraints.hasBoundedWidth) widthPx = constraints.maxWidth
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    /** The width in pixels, or null before the shell has measured (or outside it). */
    val px: Int? get() = widthPx.takeIf { it != UNKNOWN }

    private companion object {
        const val UNKNOWN = -1
    }
}

/** The shell's width for two-pane screens; null outside [PokerAppShell]. */
val LocalShellWidth = staticCompositionLocalOf<ShellWidth?> { null }

/**
 * For the two-pane layouts from 840 dp (the Bank's Z5): lays the content out across the whole width
 * the shell has, instead of the centred [ContentMaxWidth] column every other screen keeps. The
 * content is centred on the column, so it spans the shell's width exactly. Taps reach it there too:
 * nothing between the shell and the screen clips.
 */
fun Modifier.fillShellWidth(): Modifier = composed {
    val shell = LocalShellWidth.current
    layout { measurable, constraints ->
        val width = maxOf(shell?.px ?: constraints.maxWidth, constraints.minWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) {
            placeable.place((constraints.maxWidth - width) / 2, 0)
        }
    }
}
