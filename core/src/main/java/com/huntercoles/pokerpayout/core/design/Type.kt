package com.huntercoles.pokerpayout.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.R

/**
 * Barlow Condensed (SIL OFL 1.1, bundled and subset to Latin-1; licence in
 * `assets/licenses/OFL-BarlowCondensed.txt`). Numbers, titles and eyebrow labels use it. Running
 * text stays in Roboto, the system font.
 *
 * Its default figures are proportional, so every numeric style sets [TABULAR_FIGURES].
 */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/**
 * OpenType features for numbers: tabular (every digit the same width, so a ticking clock never
 * wobbles) and lining figures. Set it on inline amounts in Roboto too.
 */
const val TABULAR_FIGURES = "tnum, lnum"

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** The number and heading styles. Anything a player reads as a number uses one of these. */
object PokerType {
    /** The portrait clock hero: "12:41". The table view fits it to the width instead. */
    val Clock = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 112.sp,
        lineHeight = 104.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR_FIGURES,
        lineHeightStyle = Trim,
    )

    /** Current blinds, prize pool. */
    val DisplayL = Clock.copy(fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = 0.sp)

    /** Equity %, the Payouts hero. */
    val DisplayM = DisplayL.copy(fontSize = 40.sp, lineHeight = 44.sp)

    /** Stats, next blinds. */
    val NumberL = DisplayL.copy(fontSize = 28.sp, lineHeight = 32.sp)

    /** Table cells, amounts, field values. */
    val NumberM = DisplayL.copy(fontSize = 20.sp, lineHeight = 24.sp)

    /** Small numbers: pills, segmented previews, chip values. */
    val NumberS = DisplayL.copy(fontSize = 15.sp, lineHeight = 18.sp)

    /** Screen titles (the top bar). */
    val Title = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.2.sp,
    )

    /** Section labels, uppercased by the caller ("LEVEL TIME LEFT"). Pills use it at 12 sp. */
    val Eyebrow = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.4.sp,
    )

    /** Card ranks ("10", "A") in Barlow 700; the card face sets the size. */
    val CardRank = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR_FIGURES,
        lineHeightStyle = Trim,
    )
}

/** The makeover's Material type scale. Text in Roboto; [PokerType] for numbers and titles. */
val PokerTypography = Typography(
    headlineSmall = PokerType.Title,
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
