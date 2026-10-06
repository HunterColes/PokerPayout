package com.huntercoles.pokerpayout.core.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app is dark only, so the status and navigation bar icons must be light (B13, PP-019: the old
 * theme left dark icons on the dark app). `MainActivity` calls [drawBehindDarkSystemBars] first
 * thing; here it runs on an activity whose bars were set to dark icons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemBarsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the system bar icons are light on the dark app (B13)`() {
        compose.runOnUiThread {
            val activity = compose.activity
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            bars.isAppearanceLightStatusBars = true
            bars.isAppearanceLightNavigationBars = true
            assertTrue(bars.isAppearanceLightStatusBars)

            activity.drawBehindDarkSystemBars()

            assertFalse("status bar icons", bars.isAppearanceLightStatusBars)
            assertFalse("navigation bar icons", bars.isAppearanceLightNavigationBars)
        }
    }
}
