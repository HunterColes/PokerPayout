package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the screen on show asks of [PokerAppShell] around it: the whole window, tabs and system bars
 * included (the Tournament tab's table view, S3). Screens ask with [RequestShellChrome]; nothing
 * changes for screens that don't. (A screen that wants the full width beside the tabs uses
 * [fillShellWidth] instead.)
 */
@Stable
class ShellChrome {
    /** No tabs and no insets: the screen fills the window (pair it with hiding the system bars). */
    var immersive: Boolean by mutableStateOf(false)
        internal set
}

/** The shell's [ShellChrome], or null outside a [PokerAppShell]. */
val LocalShellChrome = staticCompositionLocalOf<ShellChrome?> { null }

/** While composed, asks the shell for the whole window. */
@Composable
fun RequestShellChrome(immersive: Boolean = false) {
    val chrome = LocalShellChrome.current ?: return
    DisposableEffect(chrome, immersive) {
        chrome.immersive = immersive
        onDispose { chrome.immersive = false }
    }
}
