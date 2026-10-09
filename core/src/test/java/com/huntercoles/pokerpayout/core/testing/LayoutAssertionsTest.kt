package com.huntercoles.pokerpayout.core.testing

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
            Column(Modifier.background(Color.Black)) {
                Text("Start clock", fontSize = 16.sp, color = Color.White)
                Box(Modifier.size(48.dp).clickable {}.named("Pause"))
                Box(Modifier.size(48.dp).clickable {}) { Text("Undo", color = Color.White) }
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
        show { Box(Modifier.size(24.dp).clickable {}.named("Info")) }
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
                Box(Modifier.size(24.dp).clickable {}.named("Rebuy"))
                Box(Modifier.size(24.dp).clickable {}.named("Add-on"))
            }
        }
        assertFails("overlapping touch targets") {
            LayoutAssertions.assertTouchTargets(screen.compose, "crowded", strict = false)
        }
    }

    @Test
    fun tapTargetWithoutANameFails() {
        show { Box(Modifier.size(48.dp).clickable {}) }
        assertFails("has no name TalkBack can read") {
            LayoutAssertions.assertTouchTargets(screen.compose, "unnamed", strict = true)
        }
    }

    @Test
    fun emptyFieldWithoutALabelFails() {
        var labelled by mutableStateOf(false)
        var value by mutableStateOf("")
        show {
            val label = if (labelled) Modifier.named("Buy-in") else Modifier
            BasicTextField(value = value, onValueChange = {}, modifier = Modifier.size(120.dp, 48.dp).then(label))
        }
        assertFails("an empty field without a label") {
            LayoutAssertions.assertTouchTargets(screen.compose, "unlabelled", strict = false)
        }
        // A label names it; so does a value ("Dana, edit box")
        labelled = true
        screen.compose.waitForIdle()
        LayoutAssertions.assertTouchTargets(screen.compose, "labelled", strict = false)
        labelled = false
        value = "Dana"
        screen.compose.waitForIdle()
        LayoutAssertions.assertTouchTargets(screen.compose, "with a value", strict = false)
    }

    @Test
    fun lowContrastTextFails() {
        // #666666 on #333333: about 2.2:1
        show { Box(Modifier.background(Color(0xFF333333))) { Text("Muted note", color = Color(0xFF666666), fontSize = 14.sp) } }
        assertFails("needs 4.50:1") { LayoutAssertions.assertTextFits(screen.compose, "muted") }
    }

    @Test
    fun seeThroughTextIsJudgedAsDrawn() {
        // White at 30% over black is #4D4D4D: about 2.5:1
        show { Box(Modifier.background(Color.Black)) { Text("Faded", color = Color.White.copy(alpha = 0.3f)) } }
        assertFails("needs 4.50:1") { LayoutAssertions.assertTextFits(screen.compose, "faded") }
    }

    @Test
    fun largeTextNeedsThreeToOne() {
        // #707070 on #1A1A1A: about 3.5:1, enough for 24 sp, not for 14 sp
        var size by mutableStateOf(24.sp)
        show { Box(Modifier.background(Color(0xFF1A1A1A))) { Text("42:17", color = Color(0xFF707070), fontSize = size) } }
        LayoutAssertions.assertTextFits(screen.compose, "large")
        size = 14.sp
        screen.compose.waitForIdle()
        assertFails("needs 4.50:1") { LayoutAssertions.assertTextFits(screen.compose, "small") }
    }

    @Test
    fun textInADisabledControlIsExempt() {
        show {
            Box(Modifier.background(Color(0xFF333333)).semantics { disabled() }) {
                Text("Locked", color = Color(0xFF666666))
            }
        }
        LayoutAssertions.assertTextFits(screen.compose, "disabled")
    }

    private fun Modifier.named(name: String) = semantics { contentDescription = name }

    private fun show(content: @Composable () -> Unit) {
        screen.compose.setContent(content)
    }

    private fun assertFails(expected: String, check: () -> Unit) {
        val error = assertThrows(AssertionError::class.java) { check() }
        assertTrue("expected \"$expected\" in: ${error.message}", error.message.orEmpty().contains(expected))
    }
}
