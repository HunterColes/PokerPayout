package com.huntercoles.pokerpayout.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huntercoles.pokerpayout.core.design.PokerColors

/**
 * The app around its screens: the four tabs, the screen, and the one snackbar host.
 *
 * - **Phones held upright** (under 600 dp wide): the screen above [PokerNavBar].
 * - **From 600 dp** (tablets, foldables, phones on their side): [PokerNavRail] on the left and the
 *   screen beside it (PP-087).
 * - **The screen** is capped at [ContentMaxWidth] and centred, so single-column screens don't
 *   stretch across a tablet. Each screen draws its own [PokerTopBar], which pads for the status bar.
 * - **Edge to edge:** the shell pads for everything else: the gesture or button bar (under the bar,
 *   or beside and under the screen), display cutouts in landscape, and the keyboard. With the
 *   keyboard open the whole shell sits above it, as it did before edge to edge, so the bar stays
 *   reachable while typing.
 *
 * [selectedIndex] is the tab to show as selected, or -1 for none. The screen keeps its state when
 * the window crosses 600 dp, because it moves between the two layouts rather than being rebuilt.
 */
@Suppress("LongParameterList") // the shell's slots: tabs, selection, snackbars, screen
@Composable
fun PokerAppShell(
    items: List<PokerNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable () -> Unit,
) {
    val latestContent by rememberUpdatedState(content)
    val screen = remember { movableContentOf { latestContent() } }
    // The keyboard padding goes inside the width check: while the keyboard slides in, only the
    // layout changes frame by frame; the shell isn't recomposed (it would recompose the screen too,
    // and slow typing down while the keyboard opens).
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PokerColors.PokerBlack),
    ) {
        CompositionLocalProvider(LocalWidthClass provides widthClassOf(maxWidth)) {
            when (navLayoutFor(maxWidth)) {
                NavLayout.Rail -> Row(Modifier.fillMaxSize().imePadding()) {
                    PokerNavRail(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
                    ShellScreen(
                        screen = screen,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)),
                    )
                }
                NavLayout.BottomBar -> Column(Modifier.fillMaxSize().imePadding()) {
                    ShellScreen(
                        screen = screen,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    )
                    PokerNavBar(items = items, selectedIndex = selectedIndex, onSelect = onSelect)
                }
            }
        }
    }
}

/** The screen, centred at [ContentMaxWidth] at most, with snackbars along its bottom edge. */
@Composable
private fun ShellScreen(screen: @Composable () -> Unit, snackbarHostState: SnackbarHostState?, modifier: Modifier) {
    Box(modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth(),
        ) {
            screen()
        }
        if (snackbarHostState != null) {
            PokerSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = SnackbarMaxWidth),
            )
        }
    }
}

/** Material's widest snackbar; on a tablet it sits centred under the screen instead of spanning it. */
private val SnackbarMaxWidth = 600.dp
