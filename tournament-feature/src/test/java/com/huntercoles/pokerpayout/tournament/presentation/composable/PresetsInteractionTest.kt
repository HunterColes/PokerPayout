package com.huntercoles.pokerpayout.tournament.presentation.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.tournament.presentation.TournamentMode
import com.huntercoles.pokerpayout.tournament.presentation.TournamentUi
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetSheet
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsIntent
import com.huntercoles.pokerpayout.tournament.presentation.presets.PresetsUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * What the presets' controls send (PP-032): the Presets row on the setup page and in the mid-game
 * panel, and in the sheet the list (load, save, share, rename, delete), the save and rename forms
 * and the load question. Mid-game a preset can't be loaded, and the list says why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-port")
class PresetsInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var fixture: TournamentFixture
    private val sent = mutableListOf<PresetsIntent>()
    private var shared = 0
    private var state by mutableStateOf(PresetsFixture.list)

    private var savedZone: TimeZone? = null

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")) // "Last used" dates are the same on every machine
        fixture = TournamentFixture(store)
    }

    @After
    fun tearDown() {
        store.clear()
        savedZone?.let(TimeZone::setDefault)
    }

    /**
     * The sheet's title and body as [state] has them, on their own, as the Bank's sheet tests do. Not
     * inside [com.huntercoles.pokerpayout.core.design.components.PokerSheetContent]: its shadow clips
     * to a shape with only its top corners rounded, which Compose hit-tests as a path, and
     * Robolectric's legacy graphics can't intersect paths, so a touch there would miss. (On a phone
     * it lands; the device tour taps through the real sheet.)
     */
    private fun showSheet(start: PresetsUiState) {
        state = start
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                val sheet = state.sheet
                if (sheet != null) {
                    Column {
                        Text(presetsSheetTitle(state, sheet))
                        PresetsSheetBody(
                            state = state,
                            sheet = sheet,
                            suggestedName = PresetsFixture.SUGGESTED_NAME,
                            onIntent = { sent += it },
                            onShare = { shared++ },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.tap() {
        runCatching { performScrollTo() }
        performClick()
        compose.waitForIdle()
    }

    private val nameField get() = compose.onNode(hasSetTextAction() and hasContentDescription("Preset name"))

    @Test
    fun `the Presets row opens the presets, on the setup page and mid-game in the panel`() {
        val actions = TournamentActions(onPresetIntent = { sent += it })
        var ui by mutableStateOf(TournamentUi())
        var timer by mutableStateOf(fixture.ready)
        val setup = fixture.setupState()
        compose.setContent {
            PokerTheme(reducedMotion = true) { TournamentContent(setup, timer, ui, actions) }
        }
        compose.onNodeWithText("Load a saved setup, save this one or share it").assertExists()
        compose.onNode(hasText("Presets") and hasClickAction()).tap()
        assertEquals(listOf<PresetsIntent>(PresetsIntent.Open), sent)

        timer = fixture.running
        ui = TournamentUi(mode = TournamentMode.PanelOpen)
        compose.waitForIdle()
        compose.onNodeWithText("Loading waits for a new tournament", substring = true).assertExists()
        // The panel's shadow clips to a shape with only its bottom corners rounded: under Robolectric's
        // legacy graphics a touch inside it misses (and lands on the scrim behind), so the row's click
        // is run as TalkBack would run it
        compose.onNode(hasText("Presets") and hasClickAction())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf<PresetsIntent>(PresetsIntent.Open, PresetsIntent.Open), sent)
    }

    @Test
    fun `the list loads with a tap, and saves, shares, renames and deletes`() {
        showSheet(PresetsFixture.list)
        compose.onNodeWithText("Friday").tap()
        compose.onNodeWithText("Save as preset…").tap()
        compose.onNodeWithText("Share setup as text").tap()
        compose.onNodeWithContentDescription("More options for Deep stack").tap()
        compose.onNode(hasText("Rename…") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("More options for Turbo").tap()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.waitForIdle()

        val friday = PresetsFixture.friday.id
        val deep = PresetsFixture.deepStack.id
        val turbo = PresetsFixture.turbo.id
        val expected = listOf(PresetsIntent.Load(friday), PresetsIntent.StartSave, PresetsIntent.StartRename(deep))
        assertEquals(expected + PresetsIntent.Delete(turbo), sent)
        assertEquals(1, shared)
    }

    @Test
    fun `the list shows what each preset holds and when it was last used`() {
        showSheet(PresetsFixture.list)
        compose.onNodeWithText("$40 buy-in · 20-min levels · 5,000 chips · Standard · 3 paid").assertExists()
        compose.onNodeWithText("$60 buy-in · 30-min levels · 10,000 chips · Top-heavy · 4 paid · chip set").assertExists()
        compose.onNodeWithText("$20 buy-in · 10-min levels · 3,000 chips · Custom · 2 paid").assertExists()
        compose.onNodeWithText("Last used Oct 3, 2026").assertExists()
        compose.onAllNodesWithText("Last used", substring = true).assertCountEquals(PresetsFixture.all.size)
    }

    @Test
    fun `mid-game a preset can't be loaded, and the list says why`() {
        showSheet(PresetsFixture.list.copy(canLoad = false))
        compose.onNodeWithText("Loading is off while a game is on", substring = true).assertExists()
        compose.onNode(hasText("Friday") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Friday").tap()
        assertTrue("nothing loads", sent.isEmpty())

        // Saving and sharing still work
        compose.onNodeWithText("Save as preset…").tap()
        compose.onNodeWithText("Share setup as text").tap()
        assertEquals(listOf<PresetsIntent>(PresetsIntent.StartSave), sent)
        assertEquals(1, shared)
    }

    @Test
    fun `the save form saves under the typed name, with the chip set once it is set up`() {
        showSheet(PresetsFixture.list.copy(sheet = PresetSheet.Save))
        nameField.assertExists()
        compose.onNodeWithText(PresetsFixture.SUGGESTED_NAME).assertExists()
        compose.onNode(hasText("Include the chip set") and hasClickAction()).assertIsOn()
        compose.onNodeWithText("From Tools: 4 colours, 500 chips").assertExists()

        nameField.performTextReplacement("Saturday")
        compose.onNodeWithText("Save preset").tap()
        compose.onNode(hasText("Include the chip set") and hasClickAction()).tap()
        compose.onNodeWithText("Save preset").tap()
        compose.onNodeWithText("Cancel").tap()

        assertEquals(
            listOf(
                PresetsIntent.Save("Saturday", includeChipSet = true),
                PresetsIntent.Save("Saturday", includeChipSet = false),
                PresetsIntent.Open,
            ),
            sent,
        )
    }

    @Test
    fun `a name already saved says it replaces that preset, and a blank one can't be saved`() {
        showSheet(PresetsFixture.list.copy(sheet = PresetSheet.Save))
        nameField.performTextReplacement("  friday ")
        compose.waitForIdle()
        compose.onNodeWithText("Replaces the preset with this name.").assertExists()
        compose.onNodeWithText("Replace preset").assertIsEnabled()

        nameField.performTextReplacement("   ")
        compose.waitForIdle()
        compose.onNodeWithText("Save preset").assertIsNotEnabled()
    }

    @Test
    fun `rename refuses a name another preset has`() {
        val deep = PresetsFixture.deepStack.id
        showSheet(PresetsFixture.list.copy(sheet = PresetSheet.Rename(deep)))
        compose.onNodeWithText("Rename").assertIsNotEnabled() // unchanged

        nameField.performTextReplacement("Turbo")
        compose.waitForIdle()
        compose.onNodeWithText("Another preset has this name.").assertExists()
        compose.onNodeWithText("Rename").assertIsNotEnabled()

        nameField.performTextReplacement("Deep stack Saturday")
        compose.waitForIdle()
        compose.onNodeWithText("Rename").tap()
        assertEquals(listOf<PresetsIntent>(PresetsIntent.Rename(deep, "Deep stack Saturday")), sent)
    }

    @Test
    fun `the load question loads, or goes back to the list`() {
        val friday = PresetsFixture.friday.id
        showSheet(PresetsFixture.list.copy(sheet = PresetSheet.ConfirmLoad(friday)))
        compose.onNodeWithText("Load Friday?").assertExists()
        compose.onNodeWithText("The players and the Bank stay as they are", substring = true).assertExists()
        compose.onNodeWithText("Load preset").tap()
        compose.onNodeWithText("Keep mine").tap()
        assertEquals(listOf(PresetsIntent.ConfirmLoad(friday), PresetsIntent.Open), sent)
    }
}
