@file:Suppress("DEPRECATION") // the pre-makeover schemes below use the sunset colours until M2 deletes them

package com.huntercoles.pokerpayout.core.design

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
 * The makeover theme: dark only, Barlow Condensed numbers, the new scheme and shapes. It touches
 * no window state, so it works in previews and screenshot tests; edge-to-edge is set up in
 * `MainActivity` (M2), which then replaces [PokerPayoutTheme] with this.
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

private val PokerDarkColorScheme = darkColorScheme(
    primary = PokerColors.DarkGreen,
    secondary = PokerColors.MediumGreen,
    tertiary = PokerColors.AccentGreen,
    background = PokerColors.PokerBlack,
    surface = PokerColors.DarkGreen,
    onPrimary = PokerColors.CardWhite,
    onSecondary = PokerColors.CardWhite,
    onTertiary = PokerColors.FeltGreen,
    onBackground = PokerColors.CardWhite,
    onSurface = PokerColors.CardWhite,
)

private val PokerLightColorScheme = lightColorScheme(
    primary = PokerColors.MediumGreen,
    secondary = PokerColors.LightGreen,
    tertiary = PokerColors.AccentGreen,
    background = PokerColors.CardWhite,
    surface = PokerColors.LightGreen,
    onPrimary = PokerColors.CardWhite,
    onSecondary = PokerColors.CardWhite,
    onTertiary = PokerColors.FeltGreen,
    onBackground = PokerColors.FeltGreen,
    onSurface = PokerColors.FeltGreen,
)

/** The pre-makeover theme, still used by `MainActivity` until M2 switches to [PokerTheme]. */
@Composable
fun PokerPayoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = pickColorScheme(darkTheme)
    val view = LocalView.current

    if (!view.isInEditMode) {
        val currentWindow = (view.context as? Activity)?.window
            ?: error("Not in an activity - unable to get Window reference")

        SideEffect {
            currentWindow.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(currentWindow, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

@Composable
fun pickColorScheme(darkTheme: Boolean): ColorScheme = when {
    darkTheme -> PokerDarkColorScheme
    else -> PokerLightColorScheme
}
