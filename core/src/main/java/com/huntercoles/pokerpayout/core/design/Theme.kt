package com.huntercoles.pokerpayout.core.design

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
