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
import com.huntercoles.pokerpayout.tools.presentation.CueSoundsUiState
import com.huntercoles.pokerpayout.tools.presentation.MusicUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Music (S17) and Cue sounds (S18) inside the app's shell on every cell of the device matrix: text
 * fits and is never clipped at any scroll position, 48 dp targets that don't overlap. Goldens on
 * [DeviceMatrix.goldens]: `S17_music_empty` (a fresh install), `S17_music_playing` (five songs, one
 * playing, one whose file has gone, with the clock and quieter on breaks), `S17_music_editing` (move
 * and remove), `S18_cue_sounds` and `S18_cue_sounds_off` (the sound switched off). Every file gone
 * and the built-in songs (none ship yet) get the layout checks too.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MusicScreenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun musicEmpty() = music("S17_music_empty", MusicFixtures.empty)

    @Test
    fun musicPlaying() = music("S17_music_playing", MusicFixtures.playing)

    @Test
    fun musicEditing() = music("S17_music_editing", MusicFixtures.editing)

    @Test
    fun musicNothingPlayable() = music(name = null, MusicFixtures.nothingPlayable)

    @Test
    fun musicBuiltIn() = music(name = null, MusicFixtures.withBuiltIn)

    @Test
    fun cueSounds() = cueSounds("S18_cue_sounds", MusicFixtures.cueSounds)

    @Test
    fun cueSoundsOff() = cueSounds("S18_cue_sounds_off", MusicFixtures.cueSoundsOff)

    private fun music(name: String?, state: MusicUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                MusicContent(state, onIntent = {}, onBack = {}, onAddSongs = {})
            }
        }
        check(name ?: "S17 (layout only)", golden = name)
    }

    private fun cueSounds(name: String, state: CueSoundsUiState) {
        screen.compose.setContent {
            InAppShell(NavTab.Tools) {
                CueSoundsContent(state, onIntent = {}, onBack = {})
            }
        }
        check(name, golden = name)
    }

    private fun check(label: String, golden: String?) {
        val where = "$label on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        if (golden != null && config in DeviceMatrix.goldens) screen.compose.onRoot().captureGolden(GROUP, golden, config)
        screen.compose.forEachScrollPosition { position ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, $position")
            LayoutAssertions.assertTouchTargets(screen.compose, "$where, $position", strict = true)
        }
    }

    companion object {
        const val GROUP = "screens"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
