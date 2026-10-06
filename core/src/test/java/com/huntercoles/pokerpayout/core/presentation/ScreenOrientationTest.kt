package com.huntercoles.pokerpayout.core.presentation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.design.components.ContentMaxWidth
import com.huntercoles.pokerpayout.core.design.components.PokerAppShell
import com.huntercoles.pokerpayout.core.design.components.RequestShellChrome
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which way the app may turn (PP-079, PP-088), as [AppOrientation] asks the activity: portrait on
 * phones unless a screen on show asks for more (and back to portrait when it goes); free on tablets.
 * And what a screen may ask of the shell: the whole width (two panes) or the whole window (the
 * table view).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenOrientationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var asked by mutableIntStateOf(NONE)

    private fun showScreens() {
        compose.setContent {
            AppOrientation {
                if (asked != NONE) RequestOrientation(asked)
                Text("screen")
            }
        }
        compose.waitForIdle()
    }

    private val requested: Int get() = compose.activity.requestedOrientation

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun `a phone is portrait unless the screen on show asks, and portrait again when it goes`() {
        showScreens()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)

        asked = ActivityInfo.SCREEN_ORIENTATION_USER
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)

        asked = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, requested)

        asked = NONE
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)
    }

    @Test
    @Config(qualifiers = "w600dp-h960dp-port")
    fun `a foldable or tablet turns freely`() {
        showScreens()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requested)
        assertTrue(OrientationPolicy.isTablet(600))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, OrientationPolicy.base(599))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `a screen can take the whole window, with no tabs, and give it back`() {
        var immersive by mutableStateOf(false)
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                PokerAppShell(items = pokerNavItems(), selectedIndex = 0, onSelect = {}) {
                    RequestShellChrome(immersive = immersive)
                    Box(Modifier.fillMaxSize().testTag(SCREEN))
                }
            }
        }
        compose.waitForIdle()
        val capped = compose.onNodeWithTag(SCREEN).getBoundsInRoot()
        assertEquals(ContentMaxWidth, capped.right - capped.left)
        compose.onNodeWithText("Bank").assertExists() // the rail

        immersive = true
        compose.waitForIdle()
        compose.onNodeWithText("Bank").assertDoesNotExist() // no tabs
        val whole = compose.onNodeWithTag(SCREEN).getBoundsInRoot()
        assertEquals(0.dp, whole.left)
        assertEquals(1280.dp, whole.right)

        immersive = false
        compose.waitForIdle()
        compose.onNodeWithText("Bank").assertExists()
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
        const val SCREEN = "screen"
    }
}
