package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.graphics.Color

/**
 * The Poker Payout palette: a black room, a felt table, and brass for whatever is live.
 *
 * - [PokerBlack] is the ground. Felt greens are the surfaces you read; elevation comes from tone,
 *   not shadows (dark to light: FeltDeep, FeltGreen, DarkGreen, FeltHigh).
 * - Gold marks one live thing per screen: the countdown, the selection, the primary button, focus.
 * - Live and Danger are signals and are never used as the accent. They always come with a word or
 *   a symbol, never colour alone.
 *
 * Contrast (WCAG relative luminance; AA is 4.5:1 for text, 3:1 for large text and UI):
 * - CardWhite and PokerGold are fine for any text on every felt.
 * - Chalk is secondary text on FeltDeep / FeltGreen / DarkGreen, but **never on FeltHigh** (4.2:1):
 *   use CardWhite there.
 * - DarkGold is 3.7:1 on felt: rails, handles and large glyphs only, never small text.
 * - FeltEdge is at least 3:1 on every felt, so it marks control boundaries (WCAG 1.4.11).
 * - Danger on DarkGreen is 4.2:1: large text or icons only.
 *
 * The full table is in the makeover design spec (PP-058, section 2.2).
 */
object PokerColors {
    // Ground and felt, dark to light. Elevation is expressed by tone.

    /** App ground and table view. Gold on it: 14.0:1. */
    val PokerBlack = Color(0xFF0B0B0B)

    /** Nav bar, wells, segmented track, keypad tray, stat strip. */
    val FeltDeep = Color(0xFF072A20)

    /** Cards, sections, sheets. White on it: 11.2:1. */
    val FeltGreen = Color(0xFF0A3D2E)

    /** Raised: inputs, keys, steppers, pick-list chips. */
    val DarkGreen = Color(0xFF0D4F3C)

    /** Selected or pressed: nav indicator, segment thumb, "on" chips, snackbar. White text only. */
    val FeltHigh = Color(0xFF146349)

    /** Dividers only. Deliberately below 3:1 so dividers stay quiet. */
    val FeltLine = Color(0xFF1D6E54)

    /** Outlines of inputs and controls. At least 3:1 on every felt. */
    val FeltEdge = Color(0xFF5AA88C)

    // Brass

    /** The live accent: countdown, primary button, selection, focus. 8.7:1 on felt. */
    val PokerGold = Color(0xFFFFD700)

    /** Rails, sheet handles and large glyphs. 3.7:1 on felt, so never small text. */
    val DarkGold = Color(0xFFB8860B)

    /** Gold at 14% over FeltGreen: the current-level row. Gold on it: 6.3:1. */
    val GoldWash = Color(0xFF2C5328)

    // Ink

    /** Primary text and card faces. */
    val CardWhite = Color(0xFFF5F5F5)

    /** Secondary text. Replaces TextSecondary and white-at-alpha text. Never on FeltHigh. */
    val Chalk = Color(0xFFB9C8C2)

    /** Disabled only. Exempt from contrast rules, so never used for content. */
    val ChalkDim = Color(0xFF6F8B82)

    // Signals (never used as the accent)

    /** Running, paid, balanced, "fits". 6.5:1 on FeltGreen. */
    val Live = Color(0xFF5FD38A)

    /** Out, final minutes, overtime, errors (text and icons). 5.3:1 on FeltGreen. */
    val Danger = Color(0xFFFF8A80)

    /** Destructive button fill, with white text at 8.2:1. */
    val DangerFill = Color(0xFF8E1B2B)

    /** Knocked-out rows and error containers. Danger on it: 6.2:1. */
    val DangerWash = Color(0xFF3A1A1E)

    // Card faces (all at least 5:1 on CardWhite)

    val SuitRed = Color(0xFFC8102E)
    val SuitBlack = Color(0xFF111111)

    /** Clubs in the optional four-colour deck. */
    val SuitClub4 = Color(0xFF0A7A3D)

    /** Diamonds in the optional four-colour deck. */
    val SuitDiamond4 = Color(0xFF1A5FD0)

    // Standardized alpha values used across the UI.

    /** The paused clock dims its digits to 70%. */
    const val PokerPausedAlpha = 0.7f

    // Sunset colours: kept so the feature screens compile until they move to the new tokens (M1+).

    @Deprecated("Off palette: a gold icon on it is 2.1:1. Use Live for status, FeltHigh for selection.", ReplaceWith("Live"))
    val AccentGreen = Color(0xFF4CAF50)

    @Deprecated("Off palette. Use Live.", ReplaceWith("Live"))
    val SuccessGreen = Color(0xFF32CD32)

    @Deprecated("2.5:1 on felt as text. Use Danger for text and icons, DangerFill for fills.", ReplaceWith("Danger"))
    val ErrorRed = Color(0xFFDC143C)

    @Deprecated("Use Chalk for secondary text.", ReplaceWith("Chalk"))
    val TextSecondary = Color(0xFFE0E0E0)

    @Deprecated("Fights the gold. Use FeltHigh for selected or pressed surfaces.", ReplaceWith("FeltHigh"))
    val MediumGreen = Color(0xFF1B5E20)

    @Deprecated("Fights the gold. Use FeltHigh for selected or pressed surfaces.", ReplaceWith("FeltHigh"))
    val LightGreen = Color(0xFF2E7D32)

    @Deprecated("Unused accent. Use CardWhite for text on felt.", ReplaceWith("CardWhite"))
    val LightGold = Color(0xFFFFF8DC)

    // The role aliases below hide which felt a surface uses. Map each caller by hand.

    @Deprecated("Name the felt directly.", ReplaceWith("FeltGreen"))
    val BackgroundPrimary = FeltGreen

    @Deprecated("Name the felt directly.", ReplaceWith("DarkGreen"))
    val BackgroundSecondary = DarkGreen

    @Suppress("DEPRECATION")
    @Deprecated("Name the felt directly: FeltHigh for selected, DarkGreen for raised.", ReplaceWith("FeltHigh"))
    val BackgroundTertiary = MediumGreen

    @Deprecated("Name the felt directly.", ReplaceWith("DarkGreen"))
    val SurfacePrimary = DarkGreen

    @Suppress("DEPRECATION")
    @Deprecated("Name the felt directly: FeltHigh for selected, DarkGreen for raised.", ReplaceWith("FeltHigh"))
    val SurfaceSecondary = LightGreen

    @Suppress("DEPRECATION")
    @Deprecated("Name the felt directly: FeltHigh for selected, DarkGreen for raised.", ReplaceWith("FeltHigh"))
    val SurfaceTertiary = MediumGreen
}
