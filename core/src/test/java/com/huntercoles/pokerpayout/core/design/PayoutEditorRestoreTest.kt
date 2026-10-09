package com.huntercoles.pokerpayout.core.design

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.components.PayoutPreview
import com.huntercoles.pokerpayout.core.design.components.PayoutStructureContent
import com.huntercoles.pokerpayout.core.domain.model.PayoutRounding
import com.huntercoles.pokerpayout.core.domain.model.PayoutSettings
import com.huntercoles.pokerpayout.core.testing.Device
import com.huntercoles.pokerpayout.core.testing.ScreenConfig
import com.huntercoles.pokerpayout.core.testing.ScreenTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The payout editor keeps what the host changed but hasn't saved yet when the activity is
 * recreated (a tablet turned, the font size changed, the app brought back after Android reclaimed
 * it). Every other sheet with a draft already kept it; this one started over from the saved
 * structure.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PayoutEditorRestoreTest {
    @get:Rule
    val screen = ScreenTestRule(ScreenConfig(Device.Phone, 1.0f))

    @Test
    fun anUnsavedStructureSurvivesTheActivityBeingRecreated() {
        var saved: PayoutSettings? = null
        val restoration = StateRestorationTester(screen.compose)
        restoration.setContent {
            PokerTheme(reducedMotion = true) {
                PayoutStructureContent(
                    current = PayoutSettings(listOf(50, 30, 20), preset = null, rounding = PayoutRounding.ONE_DOLLAR),
                    preview = PayoutPreview(prizePoolCents = 35_000L, playerCount = 9),
                    onSave = { saved = it },
                    onDismiss = {},
                )
            }
        }
        screen.compose.onNodeWithContentDescription("Increase 3rd weight").performClick()
        screen.compose.onNodeWithText("$5").performClick()

        restoration.emulateSavedInstanceStateRestore()

        screen.compose.onNodeWithText("Save structure").performScrollTo().performClick()
        assertEquals(PayoutSettings(listOf(50, 30, 21), preset = null, rounding = PayoutRounding.FIVE_DOLLARS), saved)
    }
}
