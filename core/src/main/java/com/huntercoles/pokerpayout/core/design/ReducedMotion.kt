package com.huntercoles.pokerpayout.core.design

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when animations should be instant: the system "Remove animations" setting (animator
 * duration scale 0), or a screenshot test. Pulses become static outlines and transitions snap.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Reads the system animator duration scale once per composition of the theme. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
