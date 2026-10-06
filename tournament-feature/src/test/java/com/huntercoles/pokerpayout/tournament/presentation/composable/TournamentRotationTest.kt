package com.huntercoles.pokerpayout.tournament.presentation.composable

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.presentation.AppOrientation
import com.huntercoles.pokerpayout.core.presentation.LocalPhoneHold
import com.huntercoles.pokerpayout.core.presentation.PhoneHold
import com.huntercoles.pokerpayout.core.presentation.UPRIGHT_HOLD_MILLIS
import com.huntercoles.pokerpayout.tournament.presentation.TimerIntent
import com.huntercoles.pokerpayout.tournament.presentation.TimerViewModel
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rotation (PP-079, PP-088) on Robolectric screens: a phone held upright shows the clock (S2) and lets
 * the activity follow the phone's rotation once a clock exists; on its side it shows the table view
 * (S3); every other tab, and setup before the start, stays portrait; ⤢ forces landscape until ✕;
 * ✕ in a turned table view holds the clock upright only until the phone is held upright again
 * (PP-094 #2); tablets turn freely. The orientation is what the activity was asked for, through
 * [AppOrientation], as in MainActivity. How the phone is held comes from [held], not the sensors.
 * Rotating keeps the clock and the tab's own state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TournamentRotationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture
    private lateinit var timer: TimerViewModel
    private var onTournament by mutableStateOf(true)

    /** How the test holds the phone, whatever the screen shows: on its side until a test says otherwise. */
    private val held = MutableStateFlow(false)
    private val hold = object : PhoneHold {
        override fun upright(context: Context): Flow<Boolean> = held
    }

    @Before
    fun setUp() {
        fixture = TournamentFixture(store)
        timer = fixture.timerViewModel()
    }

    @After
    fun tearDown() = store.clear()

    /** The Tournament tab or another one, as the app's nav host swaps them. */
    private fun showApp(clockStarted: Boolean) {
        if (clockStarted) timer.acceptIntent(TimerIntent.NextBlindLevel) // a clock at level 2, paused
        val setup = fixture.setupState()
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                AppOrientation {
                    CompositionLocalProvider(LocalPhoneHold provides hold) {
                        if (onTournament) {
                            val state by timer.uiState.collectAsState()
                            var ui by rememberSaveable { mutableStateOf(TournamentUi.initial(clockStarted)) }
                            val actions = TournamentActions(onTimerIntent = timer::acceptIntent, updateUi = { ui = it(ui) })
                            TournamentContent(setup, state, ui, actions)
                        } else {
                            Text("Bank")
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val requested: Int get() = compose.activity.requestedOrientation

    private fun assertClock() = compose.onNodeWithContentDescription(TABLE_VIEW).assertIsDisplayed()

    private fun assertTableView() = compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertIsDisplayed()

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun `a phone held upright shows the clock and may turn once a clock exists`() {
        showApp(clockStarted = true)
        assertClock()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertDoesNotExist()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land")
    fun `a phone on its side shows the table view`() {
        showApp(clockStarted = true)
        assertTableView()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)

        // ✕ on a table view the phone was turned into: the clock, held upright while the phone stays on its side
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(UPRIGHT_HOLD_MILLIS * 3)
        compose.waitForIdle()
        assertClock()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)
    }

    /**
     * PP-094 #2: ✕ holds the clock upright for this turn only. Once the phone is held upright again it
     * may turn, and on its side it shows the table view again. Robolectric keeps the window landscape,
     * so the hold ending here is the phone turned upright and back on its side in one.
     */
    @Test
    @Config(qualifiers = "w780dp-h360dp-land")
    fun `closing a turned table view holds only this turn, and the next turn shows it again`() {
        showApp(clockStarted = true)
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).performClick()
        compose.waitForIdle()
        assertClock()

        // A pass through upright while turning doesn't count
        held.value = true
        compose.mainClock.advanceTimeBy(UPRIGHT_HOLD_MILLIS / 2)
        held.value = false
        compose.mainClock.advanceTimeBy(UPRIGHT_HOLD_MILLIS * 2)
        compose.waitForIdle()
        assertClock()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)

        // Held upright: the phone may turn again, and on its side it is the table view again
        held.value = true
        compose.mainClock.advanceTimeBy(UPRIGHT_HOLD_MILLIS + FRAME_MILLIS)
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)
        assertTableView()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land")
    fun `before the start the tab is setup, upright, even with the phone on its side`() {
        showApp(clockStarted = false)
        compose.onNodeWithText(START_CLOCK).assertExists()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertDoesNotExist()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun `other tabs stay portrait on a phone, and coming back lets it turn again`() {
        showApp(clockStarted = true)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)

        onTournament = false
        compose.waitForIdle()
        compose.onNodeWithText("Bank").assertIsDisplayed()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)

        onTournament = true
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun `the table-view button forces landscape until it is closed`() {
        showApp(clockStarted = true)
        compose.onNodeWithContentDescription(TABLE_VIEW).performClick()
        compose.waitForIdle()
        assertTableView()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, requested)

        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).performClick()
        compose.waitForIdle()
        assertClock()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, requested)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port")
    fun `a tablet turns freely on the Tournament tab and every other`() {
        showApp(clockStarted = true)
        assertClock()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requested)
        onTournament = false
        compose.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requested)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `a tablet on its side shows the two-pane clock, not the table view`() {
        showApp(clockStarted = true)
        assertClock()
        compose.onNodeWithContentDescription(EXIT_TABLE_VIEW).assertDoesNotExist()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requested)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun `setup before the start keeps a phone upright`() {
        showApp(clockStarted = false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requested)
    }

    /**
     * A rotation recreates the screen: the tab's own state comes back from its saved state (here the
     * open, unlocked panel), and the clock is the same ViewModel, not restarted.
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun `a rotation keeps the open panel and the same clock`() {
        timer.acceptIntent(TimerIntent.NextBlindLevel)
        timer.acceptIntent(TimerIntent.NextBlindLevel)
        val before = timer.uiState.value
        val setup = fixture.setupState()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PokerTheme(reducedMotion = true) {
                val state by timer.uiState.collectAsState()
                var ui by rememberSaveable { mutableStateOf(TournamentUi.initial(clockStarted = true)) }
                val actions = TournamentActions(onTimerIntent = timer::acceptIntent, updateUi = { ui = it(ui) })
                TournamentContent(setup, state, ui, actions)
            }
        }
        compose.onNodeWithText(STRIP_SETUP).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(UNLOCK).assertExists()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(UNLOCK).assertExists() // the panel is still open
        assertSame(before.timeline, timer.uiState.value.timeline)
        assertEquals(before.elapsedSeconds, timer.uiState.value.elapsedSeconds)
        assertEquals(3, timer.uiState.value.currentBlindLevel?.level)
        assertEquals(TournamentMode.Running, TournamentUi.initial(clockStarted = true).mode)
    }

    private companion object {
        const val TABLE_VIEW = "Table view"
        const val EXIT_TABLE_VIEW = "Exit table view"
        const val START_CLOCK = "Start clock"
        const val STRIP_SETUP = "Setup"
        const val UNLOCK = "Unlock to edit…"
        const val FRAME_MILLIS = 100L
    }
}
