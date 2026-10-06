package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.LayoutAssertions
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout assertions for every component on every cell of the matrix (8 screens x 3 font scales):
 * no text clipped, ellipsized or pushed off screen; every touch target at least 48 x 48 dp and none
 * overlapping. Pictures only show what someone looks at; these fail on their own.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentLayoutTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test
    fun everyComponentFitsTheScreen() {
        val scroll = ScrollState(0)
        screen.compose.setContent {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                ComponentGallery.forEach { (_, gallery) -> gallery() }
            }
        }
        val where = "components on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        // The design system's own components lay out the full 48 dp box, so check strictly.
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        forEachScrollPosition(scroll) { offset ->
            LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "$where, scrolled to $offset px")
        }
    }

    @Test
    fun shellFitsTheScreen() {
        screen.compose.setContent { ShellSample() }
        val where = "shell on ${config.id}"
        LayoutAssertions.assertTextFits(screen.compose, where)
        LayoutAssertions.assertTouchTargets(screen.compose, where, strict = true)
        LayoutAssertions.assertVisibleTextUnclipped(screen.compose, where)
    }

    /** Runs [check] at the top, then a screen at a time down to the bottom. */
    private fun forEachScrollPosition(scroll: ScrollState, check: (Int) -> Unit) {
        val page = (screen.compose.activity.window.decorView.height / 2).coerceAtLeast(1)
        var offset = 0
        while (true) {
            screen.compose.runOnUiThread { runBlocking { scroll.scrollTo(offset) } }
            screen.compose.waitForIdle()
            check(scroll.value)
            if (scroll.value >= scroll.maxValue) break
            offset += page
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.all)
    }
}
