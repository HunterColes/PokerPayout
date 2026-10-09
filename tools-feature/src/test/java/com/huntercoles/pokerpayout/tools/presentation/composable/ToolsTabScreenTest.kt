package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.navigation.NavTab
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.InAppShell
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import com.huntercoles.pokerpayout.core.testing.forEachScrollPosition
import com.huntercoles.pokerpayout.tools.presentation.MusicSummary
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Tools tab (S7) inside the app's shell, on every cell of the device matrix: the layout checks
 * everywhere (text fits, nothing clipped at any scroll position, 48 dp targets that don't overlap),
 * and a golden on the [DeviceMatrix.goldens] cells. The tools' own screens have their own tests
 * (`HandRanksScreenTest`, `ChipSetScreenTest`, the odds tests).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ToolsTabScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun toolsDefault() = check("S7_tools_default", ToolsHomeUiState(soundOn = true, volume = 0.7f))

    @Test
    fun toolsMuted() = check("S7_tools_muted", ToolsHomeUiState(soundOn = false, volume = 0.7f))

    /** PP-083: the quiet cues switched off, the chime on. */
    @Test
    fun toolsCuesOff() = check(
        "S7_tools_cues_off",
        ToolsHomeUiState(soundOn = true, volume = 0.7f, vibrate = false, flash = false),
    )

    /** PP-081: the app's notifications are off, so the section offers to turn them on (a tablet: no vibrator). */
    @Test
    fun toolsNotificationsOff() = check(
        "S7_tools_notifications_off",
        ToolsHomeUiState(soundOn = false, volume = 0.7f, canVibrate = false, notificationsOff = true),
    )

    /** Songs in the list, one playing: the Music row with its pause button. */
    @Test
    fun toolsMusic() = check(
        "S7_tools_music",
        ToolsHomeUiState(soundOn = true, volume = 0.7f, music = MusicSummary(songs = 5, playing = true, current = "Midnight Card Room")),
    )

    private fun check(name: String, state: ToolsHomeUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                ToolsHomeContent(state = state, onIntent = {}, onOpenTool = {}, versionName = "1.3.0", onAllowNotifications = {})
            }
        }
        val where = "$name on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden("screens", name, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
