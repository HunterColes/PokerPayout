package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.design.components.PokerAppShell
import com.huntercoles.pokerpayout.core.design.components.PokerButton
import com.huntercoles.pokerpayout.core.design.components.pokerNavItems
import com.huntercoles.pokerpayout.core.design.components.showUndo
import com.huntercoles.pokerpayout.core.presentation.LocalTableKnockouts
import com.huntercoles.pokerpayout.core.presentation.TableKnockouts
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PP-135, as the app has it: the Tournament tab's table view inside the app's shell (which gives
 * the whole window to it, and its snackbars with it), a knockout recorded from the panel, the
 * panel gone, and the Undo snackbar showing over the clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w780dp-h360dp-land-xhdpi")
class TableKnockoutShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture

    /** Lives as long as the test, as a ViewModel outlives its panel. */
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val snackbars = SnackbarHostState()

    /** Records a knockout the way the Bank does: the snackbar from a scope that outlives the panel. */
    private val knockouts = object : TableKnockouts {
        @Composable
        override fun Panel(onClose: () -> Unit) {
            Box(Modifier.fillMaxSize()) {
                PokerButton(
                    text = "Record",
                    onClick = {
                        viewModelScope.launch { snackbars.showUndo("Player 10 is out in 10th", "Undo") }
                        onClose()
                    },
                )
            }
        }
    }

    @Before
    fun setUp() {
        fixture = TournamentFixture(store)
    }

    @After
    fun tearDown() {
        viewModelScope.cancel()
        store.clear()
    }

    @Test
    fun theUndoAfterAKnockoutShowsOverTheFullScreenClock() {
        val setup = fixture.setupState()
        val timer = fixture.running.copy(isTableView = true)
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                PokerAppShell(items = pokerNavItems(), selectedIndex = 0, onSelect = {}, snackbarHostState = snackbars) {
                    CompositionLocalProvider(LocalTableKnockouts provides knockouts) {
                        TournamentContent(setup, timer, TournamentUi(mode = TournamentMode.Running), TournamentActions())
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Knock out").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Record").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Player 10 is out in 10th").assertIsDisplayed()
        compose.onNodeWithText("UNDO").assertIsDisplayed()
    }
}
