package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the screen on show asks of [PokerAppShell] around it. Two screens need more than a centred
 * column beside the tabs: the Tournament tab's two panes from 840 dp (Z4), which use the whole
 * width, and its table view (S3), which takes the whole window, tabs and system bars included.
 * Screens ask with [RequestShellChrome]; nothing changes for screens that don't.
 */
@Stable
class ShellChrome {
    /** No [ContentMaxWidth] cap: the screen lays out its own panes. */
    var fullWidth: Boolean by mutableStateOf(false)
        internal set

    /** No tabs and no insets: the screen fills the window (pair it with hiding the system bars). */
    var immersive: Boolean by mutableStateOf(false)
        internal set
}

/** The shell's [ShellChrome], or null outside a [PokerAppShell]. */
val LocalShellChrome = staticCompositionLocalOf<ShellChrome?> { null }

/** While composed, asks the shell for the full width and/or the whole window. */
@Composable
fun RequestShellChrome(fullWidth: Boolean = false, immersive: Boolean = false) {
    val chrome = LocalShellChrome.current ?: return
    DisposableEffect(chrome, fullWidth, immersive) {
        chrome.fullWidth = fullWidth
        chrome.immersive = immersive
        onDispose {
            chrome.fullWidth = false
            chrome.immersive = false
        }
    }
}
