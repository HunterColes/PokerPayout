package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The makeover's sizes (design spec 2.5): spacing, touch targets, control heights and corner radii. */
object PokerDimens {
    // Spacing
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingDefault: Dp = 16.dp

    // Makeover layout (design spec 2.5). 16 dp screen gutter, 12 dp between cards, 8 dp inside them.
    val Gutter: Dp = 16.dp

    /** Every tappable thing is at least this big in both directions, whatever its visual size. */
    val MinTouch: Dp = 48.dp
    val TopBarHeight: Dp = 64.dp

    /** Plus the gesture inset. */
    val NavBarHeight: Dp = 64.dp

    /** Fields, steppers, segmented controls. */
    val ControlHeight: Dp = 48.dp

    /** Primary and secondary buttons (Regular). Was 48 dp before the makeover. */
    val ButtonHeight: Dp = 52.dp

    /** Small buttons: a 40 dp visual inside a 48 dp touch box. */
    val ButtonHeightSmall: Dp = 40.dp
    val RowMinHeight: Dp = 56.dp

    /** Bank toggle chips: a 36 dp visual inside a 48 dp touch box. */
    val ChipToggleHeight: Dp = 36.dp
    val PillHeight: Dp = 22.dp
    val KeypadKeyHeight: Dp = 48.dp
    val SuitKeyHeight: Dp = 56.dp

    // Shapes (corner radii). Pills use a fully rounded shape.
    val CornerCardFace: Dp = 6.dp

    /** Fields, keys, segmented track, small buttons. */
    val CornerControl: Dp = 12.dp

    /** Regular buttons. */
    val CornerButton: Dp = 14.dp

    /** Cards and sections. */
    val CornerCard: Dp = 16.dp
    val CornerSheet: Dp = 28.dp

    // Playing cards and chips
    val CardFaceSmall: DpSize = DpSize(28.dp, 40.dp)
    val CardFaceMedium: DpSize = DpSize(44.dp, 62.dp)
    val CardFaceLarge: DpSize = DpSize(58.dp, 82.dp)
    val PokerChipSmall: Dp = 24.dp
    val PokerChipMedium: Dp = 40.dp
    val PokerChipLarge: Dp = 56.dp

    // Elevation: everything is flat except the two overlays.
    /** Bottom sheets and the snackbar are the only surfaces that cast a shadow. */
    val ElevationOverlay: Dp = 8.dp
}
