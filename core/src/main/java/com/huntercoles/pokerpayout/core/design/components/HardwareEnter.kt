package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Hardware Enter (a keyboard, a Chromebook, the emulator) runs [leave] when it is released, and keeps
 * both the press and the release away from the text field.
 *
 * Put it on every single-line text field. Without it the field runs its IME action on the press, and
 * if that clears focus, a window in keyboard mode moves focus to the first focusable control; the
 * release then clicks that control. On the Tournament tab that is Reset.
 */
fun Modifier.leaveOnHardwareEnter(leave: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
    if (isEnter && event.type == KeyEventType.KeyUp) leave()
    isEnter
}
