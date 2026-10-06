package com.huntercoles.pokerpayout.core.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.design.components.PokerAppShell
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import com.huntercoles.pokerpayout.core.navigation.NavTab

/**
 * A screen as the app shows it: inside [PokerAppShell] with [tab] selected (the bottom bar on a
 * phone held upright, the rail from 600 dp), in [PokerTheme] with motion off, as goldens need.
 */
@Composable
fun InAppShell(tab: NavTab, content: @Composable () -> Unit) {
    PokerTheme(reducedMotion = true) {
        PokerAppShell(items = pokerNavItems(), selectedIndex = tab.ordinal, onSelect = {}, content = content)
    }
}

private val verticalScroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
    SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)

/** Most pages a test scrolls one container before giving up: a guard against a runaway loop. */
private const val MAX_PAGES = 40

/** How much of a container one step scrolls, so each step overlaps the last a little. */
private const val PAGE_FRACTION = 0.8f

/**
 * Calls [check] with everything at the top, then again after each step down every vertically
 * scrolling container on screen that has somewhere to go, one container at a time, about a page
 * per step, to the end. The argument says where ("top", "scroller 2 at 840 px"). Use it with
 * [LayoutAssertions.assertVisibleTextUnclipped], which only judges text that is in view.
 */
fun ComposeTestRule.forEachScrollPosition(check: (position: String) -> Unit) {
    waitForIdle()
    check("top")
    val scrollers = onAllNodes(verticalScroller).fetchSemanticsNodes().size
    for (index in 0 until scrollers) {
        var pages = 0
        while (pages < MAX_PAGES) {
            val node = onAllNodes(verticalScroller)[index]
            val info = node.fetchSemanticsNode()
            val range = info.config[SemanticsProperties.VerticalScrollAxisRange]
            if (range.value() >= range.maxValue() - 1f) break
            node.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, info.size.height * PAGE_FRACTION)
            }
            waitForIdle()
            pages++
            val now = onAllNodes(verticalScroller)[index].fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            check("scroller ${index + 1} at ${now.toInt()} px")
        }
    }
}
