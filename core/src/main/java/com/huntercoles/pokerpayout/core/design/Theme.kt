package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The makeover's Material scheme (dark only), so stray Material components look right. Poker
 * components still set their colours explicitly: the scheme is a safety net, not the source of truth.
 */
val PokerColorScheme: ColorScheme = darkColorScheme(
    primary = PokerColors.PokerGold, onPrimary = PokerColors.FeltDeep,
    primaryContainer = PokerColors.FeltHigh, onPrimaryContainer = PokerColors.PokerGold,
    secondary = PokerColors.Chalk, onSecondary = PokerColors.FeltDeep,
    secondaryContainer = PokerColors.DarkGreen, onSecondaryContainer = PokerColors.CardWhite,
    tertiary = PokerColors.Live, onTertiary = PokerColors.FeltDeep,
    background = PokerColors.PokerBlack, onBackground = PokerColors.CardWhite,
    surface = PokerColors.PokerBlack, onSurface = PokerColors.CardWhite,
    surfaceVariant = PokerColors.DarkGreen, onSurfaceVariant = PokerColors.Chalk,
    surfaceContainerLowest = PokerColors.PokerBlack, surfaceContainerLow = PokerColors.FeltDeep,
    surfaceContainer = PokerColors.FeltDeep, surfaceContainerHigh = PokerColors.FeltGreen,
    surfaceContainerHighest = PokerColors.FeltGreen,
    surfaceBright = PokerColors.FeltHigh, surfaceDim = PokerColors.PokerBlack,
    inverseSurface = PokerColors.CardWhite, inverseOnSurface = PokerColors.FeltDeep,
    inversePrimary = PokerColors.DarkGold,
    outline = PokerColors.FeltEdge, outlineVariant = PokerColors.FeltLine,
    error = PokerColors.Danger, onError = PokerColors.PokerBlack,
    errorContainer = PokerColors.DangerWash, onErrorContainer = PokerColors.Danger,
    scrim = Color.Black,
)

/** Corner radii: 6 card face, 12 control, 16 card, 28 sheet. */
val PokerShapes = Shapes(
    extraSmall = RoundedCornerShape(PokerDimens.CornerCardFace),
    small = RoundedCornerShape(PokerDimens.CornerControl),
    medium = RoundedCornerShape(PokerDimens.CornerCard),
    large = RoundedCornerShape(PokerDimens.CornerCard),
    extraLarge = RoundedCornerShape(PokerDimens.CornerSheet),
)

/**
 * The app's theme: dark only, Barlow Condensed numbers, the new scheme and shapes. It touches no
 * window state, so it works in previews and screenshot tests; `MainActivity` draws edge to edge.
 *
 * @param reducedMotion makes animations instant; defaults to the system "Remove animations" setting.
 */
@Composable
fun PokerTheme(
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = PokerColorScheme,
            typography = PokerTypography,
            shapes = PokerShapes,
            content = content,
        )
    }
}
