package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fold on Start (S1 v2) on a paused test clock: it plays once, 250 + 250 + 300 ms, then hands over
 * to the clock; under Reduce motion it cuts straight there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupFoldTest {

    @get:Rule
    val compose = createComposeRule()

    private val store = ViewModelStore()

    @After
    fun tearDown() = store.clear()

    @Test
    fun `the fold plays once in 800 ms, then hands over to the clock`() {
        val fixture = TournamentFixture(store)
        val setup = fixture.setupState()
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PokerTheme(reducedMotion = false) {
                SetupFold(setup, fixture.level(1, LEVEL_ONE), 16.dp) { finished++ }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(300)
        assertEquals("still folding at 300 ms", 0, finished)
        // The strip's line is on screen through the fold: it becomes the strip.
        compose.onNodeWithText(STRIP_START, substring = true).assertExists()

        compose.mainClock.advanceTimeBy(FOLD_MILLIS.toLong())
        assertEquals("done after 800 ms, once", 1, finished)
        compose.mainClock.advanceTimeBy(FOLD_MILLIS.toLong())
        assertEquals(1, finished)
    }

    @Test
    fun `under Reduce motion the fold cuts straight to the clock`() {
        val fixture = TournamentFixture(store)
        val setup = fixture.setupState()
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                SetupFold(setup, fixture.level(1, LEVEL_ONE), 16.dp) { finished++ }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(1, finished)
    }

    private companion object {
        const val LEVEL_ONE = 20 * 60
        const val STRIP_START = "9 players"
    }
}
