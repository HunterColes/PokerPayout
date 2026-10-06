package com.huntercoles.pokerpayout.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.huntercoles.pokerpayout.core.testing.DeviceMatrix
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import com.huntercoles.pokerpayout.core.testing.captureGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One golden per component per screen in [DeviceMatrix.goldens]:
 * `core/src/test/screenshots/components/<Component>/<config>.png`. Each renders the component's
 * `@Preview` gallery, so the preview and the golden can't drift apart. `shell` is a whole screen
 * built from the components, captured at the device's full size.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentGoldenTest(private val config: ScreenConfig) {
    @get:Rule
    val screen = ScreenTestRule(config)

    @Test fun pokerTopBar() = golden("PokerTopBar")

    @Test fun pokerButton() = golden("PokerButton")

    @Test fun pokerPill() = golden("PokerPill")

    @Test fun toggleChip() = golden("ToggleChip")

    @Test fun pokerSegmentedControl() = golden("PokerSegmentedControl")

    @Test fun pokerField() = golden("PokerField")

    @Test fun pokerStepper() = golden("PokerStepper")

    @Test fun cardFace() = golden("CardFace")

    @Test fun pokerChip() = golden("PokerChip")

    @Test fun equityBar() = golden("EquityBar")

    @Test fun pokerSheet() = golden("PokerSheet")

    @Test fun undoSnackbar() = golden("UndoSnackbar")

    @Test fun pokerNavBar() = golden("PokerNavBar")

    @Test fun pokerNavRail() = golden("PokerNavRail")

    @Test
    fun shell() {
        screen.compose.setContent { ShellSample() }
        screen.compose.onRoot().captureGolden("components", "Shell", config)
    }

    private fun golden(name: String) {
        val gallery: @Composable () -> Unit = ComponentGallery.first { it.first == name }.second
        screen.compose.setContent { Box(Modifier.testTag(GALLERY)) { gallery() } }
        screen.compose.onNodeWithTag(GALLERY).captureGolden("components", name, config)
    }

    companion object {
        private const val GALLERY = "gallery"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configs(): List<Array<Any>> = DeviceMatrix.parameters(DeviceMatrix.goldens)
    }
}
