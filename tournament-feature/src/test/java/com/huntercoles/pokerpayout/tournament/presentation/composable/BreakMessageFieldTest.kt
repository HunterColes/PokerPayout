package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Hardware Enter in the break note (found on the device tour): the field ran its Done action on the
 * key press and dropped focus; the window then gave focus to the first control, Reset, and the key
 * release clicked it. The field must hold focus through the press and leave on the release.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class BreakMessageFieldTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `hardware Enter leaves the break note on release, keeps the text and clicks nothing`() {
        var resetClicks = 0
        var saved = ""
        compose.setContent {
            var note by remember { mutableStateOf("") }
            Column {
                Button(onClick = { resetClicks++ }) { Text("Reset") }
                BreakMessageField(
                    message = note,
                    onChange = {
                        note = it
                        saved = it
                    },
                    isLocked = false
                )
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performTextInput("Last rebuy")

        field.performKeyInput { keyDown(Key.Enter) }
        field.assertIsFocused()

        field.performKeyInput { keyUp(Key.Enter) }
        field.assertIsNotFocused()
        assertEquals("Last rebuy", saved)
        assertEquals(0, resetClicks)
    }

    @Test
    fun `a late saved value doesn't undo typing, and an outside change shows once the field is left`() {
        var saved by mutableStateOf("")
        var typed = ""
        compose.setContent {
            Column {
                Button(onClick = {}) { Text("Elsewhere") }
                BreakMessageField(message = saved, onChange = { typed = it }, isLocked = false)
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.requestFocus()
        field.performTextInput("Last rebuy")
        assertEquals("Last rebuy", typed)

        // The ViewModel's copy arrives a step behind the keyboard: it must not overwrite the field.
        saved = "Last r"
        compose.waitForIdle()
        field.assertTextEquals("Break note", "Last rebuy", includeEditableText = true)

        // Not typing any more: an outside change replaces the text.
        compose.onNodeWithText("Elsewhere").requestFocus()
        field.assertIsNotFocused()
        saved = "Coffee"
        compose.waitForIdle()
        field.assertTextEquals("Break note", "Coffee", includeEditableText = true)
    }
}
