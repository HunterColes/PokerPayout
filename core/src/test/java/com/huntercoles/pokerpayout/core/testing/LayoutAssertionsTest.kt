package com.huntercoles.pokerpayout.core.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.components.mayTruncate
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The layout assertions catch each kind of breakage they claim to, and pass clean layouts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LayoutAssertionsTest {
    @get:Rule
    val screen = ScreenTestRule(ScreenConfig(Device.Phone, 1.0f))

    @Test
    fun cleanLayoutPasses() {
        show {
            Column {
                Text("Start clock", fontSize = 16.sp)
                Box(Modifier.size(48.dp).clickable {})
                Box(Modifier.size(48.dp).clickable {})
            }
        }
        LayoutAssertions.assertTextFits(screen.compose, "clean")
        LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "clean")
        LayoutAssertions.assertTouchTargets(screen.compose, "clean", strict = true)
    }

    @Test
    fun textTooWideForItsBoxFails() {
        show { Text("Knock out Theo", maxLines = 1, softWrap = false, modifier = Modifier.width(40.dp)) }
        assertFails("doesn't fit") { LayoutAssertions.assertTextFits(screen.compose, "narrow") }
    }

    @Test
    fun textTooTallForItsBoxFails() {
        show { Text("Knock out Theo", fontSize = 30.sp, modifier = Modifier.height(12.dp)) }
        assertFails("doesn't fit") { LayoutAssertions.assertTextFits(screen.compose, "short") }
    }

    @Test
    fun textCutOffByMaxLinesFails() {
        // Wraps inside the word, and maxLines hides the rest: "FAVOU", with no ellipsis to show it.
        show { Text("FAVOURITE", fontSize = 20.sp, maxLines = 1, modifier = Modifier.width(60.dp)) }
        assertFails("is cut off after 1 line(s)") { LayoutAssertions.assertTextFits(screen.compose, "squeezed pill") }
    }

    @Test
    fun wordBrokenAcrossLinesFails() {
        show { Text("Tournament", fontSize = 30.sp, modifier = Modifier.width(60.dp)) }
        assertFails("breaks the word 'Tournament'") { LayoutAssertions.assertTextFits(screen.compose, "broken") }
    }

    @Test
    fun ellipsizedTextFailsUnlessMarkedMayTruncate() {
        show { Text("Level 6 of 9 · running", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(60.dp)) }
        assertFails("ellipsized") { LayoutAssertions.assertTextFits(screen.compose, "ellipsis") }
    }

    @Test
    fun textMarkedMayTruncatePasses() {
        show {
            Text(
                "Level 6 of 9 · running",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(60.dp).semantics { mayTruncate = true },
            )
        }
        LayoutAssertions.assertTextFits(screen.compose, "subtitle")
    }

    @Test
    fun textClippedByAnAncestorFails() {
        show {
            // Away from the top edge, so the text is on screen and only its parent clips it.
            Box(Modifier.padding(top = 100.dp).height(10.dp).clipToBounds()) {
                Text("Paused", fontSize = 20.sp, modifier = Modifier.wrapContentHeight(unbounded = true))
            }
        }
        assertFails("is clipped") { LayoutAssertions.assertVisibleTextUnclipped(screen.compose, "clipped") }
    }

    @Test
    fun textOffTheSideOfTheScreenFails() {
        show { Text("Payouts", modifier = Modifier.offset(x = 400.dp)) }
        assertFails("runs off the side") { LayoutAssertions.assertTextFits(screen.compose, "offscreen") }
    }

    @Test
    fun smallTouchTargetFailsStrictly() {
        show { Box(Modifier.size(24.dp).clickable {}) }
        assertFails("24.0dp x 24.0dp touch target") {
            LayoutAssertions.assertTouchTargets(screen.compose, "small", strict = true)
        }
        // On its own, Compose's hit test expands it to 48 dp, so the lenient check passes.
        LayoutAssertions.assertTouchTargets(screen.compose, "small", strict = false)
    }

    @Test
    fun crowdedTouchTargetsOverlap() {
        show {
            Row {
                Box(Modifier.size(24.dp).clickable {})
                Box(Modifier.size(24.dp).clickable {})
            }
        }
        assertFails("overlapping touch targets") {
            LayoutAssertions.assertTouchTargets(screen.compose, "crowded", strict = false)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        screen.compose.setContent(content)
    }

    private fun assertFails(expected: String, check: () -> Unit) {
        val error = assertThrows(AssertionError::class.java) { check() }
        assertTrue("expected \"$expected\" in: ${error.message}", error.message.orEmpty().contains(expected))
    }
}
