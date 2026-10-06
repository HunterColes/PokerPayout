package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Centralized dimension constants for consistent spacing throughout the app
 */
object PokerDimens {
    // Standard spacing scale
    val SpacingXXSmall: Dp = 2.dp
    val SpacingXSmall: Dp = 4.dp
    val SpacingSmall: Dp = 8.dp
    val SpacingMedium: Dp = 12.dp
    val SpacingDefault: Dp = 16.dp
    val SpacingLarge: Dp = 24.dp
    val SpacingXLarge: Dp = 32.dp
    val SpacingXXLarge: Dp = 48.dp
    val SpacingHuge: Dp = 64.dp

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
    val PlayButton: Dp = 76.dp
    val ClockControl: Dp = 52.dp
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

    /** Cards and sections (same as CornerDefault). */
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

    // Card and elevation. Default/Medium/Large go once callers move to tone-based elevation (M3-M6).
    val ElevationNone: Dp = 0.dp
    val ElevationSmall: Dp = 2.dp
    val ElevationDefault: Dp = 4.dp
    val ElevationMedium: Dp = 8.dp
    val ElevationLarge: Dp = 16.dp
    
    // Border widths
    val BorderThin: Dp = 1.dp
    val BorderMedium: Dp = 2.dp
    val BorderThick: Dp = 4.dp
    
    // Corner radius
    val CornerSmall: Dp = 8.dp
    val CornerMedium: Dp = 12.dp
    val CornerDefault: Dp = 16.dp
    val CornerLarge: Dp = 24.dp
    
    // Icon sizes
    val IconSmall: Dp = 16.dp
    val IconMedium: Dp = 20.dp
    val IconDefault: Dp = 24.dp
    val IconLarge: Dp = 48.dp
    val IconXLarge: Dp = 64.dp
    
    // Playing card dimensions. Replaced by CardFaceSmall/Medium/Large; removed once callers migrate.
    val CardWidth: Dp = 40.dp
    val CardHeight: Dp = 56.dp
    
    // Component heights
    val MinInputWidth: Dp = 120.dp
    val MaxInputWidth: Dp = 160.dp
    val TimerDisplayHeight: Dp = 120.dp
    val BottomSheetMaxHeight: Dp = 400.dp
    
    // Blind Panel dimensions
    val BlindItemPaddingHorizontal: Dp = 12.dp
    val BlindItemPaddingVertical: Dp = 10.dp
    val BlindItemSpacing: Dp = 8.dp
    val BlindItemInnerSpacing: Dp = 4.dp
    val BlindItemContentHeight: Dp = 46.dp // Measured text + inner spacing
    val BlindItemTotalHeight: Dp = 70.dp // Content + vertical padding (46 + 24)
    val BlindPanelCardPadding: Dp = 16.dp
    val BlindPanelCollapsedLevels: Int = 1
    val BlindPanelExpandedLevels: Int = 5
}
