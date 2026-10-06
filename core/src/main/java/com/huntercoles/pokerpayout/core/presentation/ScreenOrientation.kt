package com.huntercoles.pokerpayout.core.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Which way the app may turn (PP-079, PP-088).
 *
 * - **Phones** are portrait on every screen. A screen may ask for more while it shows (the
 *   Tournament tab follows the phone's own rotation once a clock exists, so turning it shows the
 *   table view), and the phone goes back to portrait when the screen goes.
 * - **Tablets and foldables** (smallest width 600 dp or more) turn freely on every screen.
 *
 * The manifest doesn't lock the activity, so [base] applies from the first frame (MainActivity
 * sets it before its content) and [AppOrientation] keeps it applied.
 */
object OrientationPolicy {
    /** Smallest width from which a device counts as a tablet (Material's medium width class). */
    const val TABLET_SMALLEST_WIDTH_DP = 600

    fun isTablet(smallestScreenWidthDp: Int): Boolean = smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP

    /** Every screen's orientation unless the screen asks for another: portrait on phones, free on tablets. */
    fun base(smallestScreenWidthDp: Int): Int =
        if (isTablet(smallestScreenWidthDp)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
}

/** The orientation a screen on show has asked for, if any. The newest request wins. */
@Stable
class OrientationRequests {
    internal var requested: Int? by mutableStateOf(null)
}

val LocalOrientationRequests = staticCompositionLocalOf<OrientationRequests?> { null }

/**
 * Applies [OrientationPolicy] to the activity: the base orientation, or what a screen inside
 * [content] asked for with [RequestOrientation]. It applies the settled value once per change, after
 * the screens of a frame have made their requests, so a rotation never sees a passing value (a
 * screen recreated by the rotation asks again before anything is applied).
 */
@Composable
fun AppOrientation(content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val requests = remember { OrientationRequests() }
    val base by rememberUpdatedState(OrientationPolicy.base(LocalConfiguration.current.smallestScreenWidthDp))
    if (activity != null) {
        LaunchedEffect(activity, requests) {
            snapshotFlow { requests.requested ?: base }.collect { orientation ->
                val leaving = activity.isChangingConfigurations || activity.isFinishing
                if (!leaving && activity.requestedOrientation != orientation) {
                    activity.requestedOrientation = orientation
                }
            }
        }
    }
    CompositionLocalProvider(LocalOrientationRequests provides requests, content = content)
}

/**
 * While this is composed, the activity may turn to [orientation] (an `ActivityInfo.SCREEN_ORIENTATION_*`
 * value). When it leaves composition, the base orientation comes back (portrait on phones).
 */
@Composable
fun RequestOrientation(orientation: Int) {
    val requests = LocalOrientationRequests.current ?: return
    DisposableEffect(requests, orientation) {
        requests.requested = orientation
        onDispose { if (requests.requested == orientation) requests.requested = null }
    }
}

/**
 * Hides the status and navigation bars while composed (the table view: a propped-up phone shows only
 * the clock). A swipe from the edge shows them for a moment.
 */
@Composable
fun HideSystemBars() {
    val view = LocalView.current
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
