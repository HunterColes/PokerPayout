package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.huntercoles.pokerpayout.core.design.PokerTheme
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.tools.presentation.MusicSummary
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeIntent
import com.huntercoles.pokerpayout.tools.presentation.ToolsHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the Tools tab's rows and Sound controls do when tapped, and what TalkBack hears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class ToolsHomeContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<ToolsHomeIntent>()
    private val opened = mutableListOf<NavigationDestination>()

    private fun show(state: ToolsHomeUiState) {
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ToolsHomeContent(
                    state = state,
                    onIntent = { intents += it },
                    onOpenTool = { opened += it },
                    versionName = "1.3.0",
                )
            }
        }
    }

    @Test
    fun eachRowOpensItsTool() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("Chip set").performClick()
        compose.onNodeWithText("Seat draw").performClick()
        compose.onNodeWithText("Odds").performScrollTo().performClick()
        compose.onNodeWithText("Side pots").performScrollTo().performClick()
        compose.onNodeWithText("Hand ranks").performScrollTo().performClick()
        compose.onNodeWithText("Split the prizes").performScrollTo().performClick()
        compose.onNodeWithText("History").performScrollTo().performClick()
        compose.onNodeWithText("Currency").performScrollTo().performClick()
        compose.onNodeWithText("Backup").performScrollTo().performClick()
        assertEquals(
            listOf(
                NavigationDestination.ChipCalculator,
                NavigationDestination.SeatDraw,
                NavigationDestination.OddsCalculator,
                NavigationDestination.SidePots,
                NavigationDestination.HandRanks,
                NavigationDestination.DealMaker,
                NavigationDestination.History,
                NavigationDestination.Currency,
                NavigationDestination.Backup,
            ),
            opened,
        )
    }

    @Test
    fun theToolsComeInGroupsInTheOrderOfANightAndEachGroupIsAHeading() {
        show(ToolsHomeUiState())
        listOf("Before the game", "During play", "End of the night", "Sound and music", "Settings").forEach { heading ->
            compose.onNode(hasText(heading, ignoreCase = true) and isHeading).performScrollTo().assertExists()
        }
        // Everything on the page, top to bottom (one entry per row: a row reads as one button)
        val page = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
        }
        val expected = listOf(
            "BEFORE THE GAME", "Chip set", "Seat draw",
            "DURING PLAY", "Odds", "Side pots", "Hand ranks",
            "END OF THE NIGHT", "Split the prizes", "History",
            "SOUND AND MUSIC", "Cue sounds", "Vibrate", "Music",
            "SETTINGS", "Currency", "Backup",
        )
        val at = expected.map { label -> page.indexOfFirst { it == label || it.startsWith("$label ") } }
        assertTrue("each is on the page: ${expected.zip(at)}", at.none { it < 0 })
        assertEquals("in this order: $page", at.sorted(), at)
        compose.onNodeWithText("A deal: stop early and share what's left by chips").performScrollTo().assertExists()
    }

    @Test
    fun theRemovedToolsAreGone() {
        show(ToolsHomeUiState())
        listOf("Outs & pot odds", "Shot clock", "Dealer's choice", "Equity quiz", "Deal maker").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun theCueSoundsSwitchVolumeAndTestButtonLiveOnCueSoundsNow() {
        // #79: one place for the cue sounds; the hub links to it
        show(ToolsHomeUiState(soundOn = true))
        compose.onNode(isSwitch and hasText("Cue sounds")).assertDoesNotExist()
        compose.onNode(hasContentDescription("Cue sound volume")).assertDoesNotExist()
        compose.onNodeWithText("Test chime").assertDoesNotExist()
    }

    @Test
    fun theFooterKeepsWhatTheOldAboutCardSaid() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("Free and open source", substring = true).assertExists()
        compose.onNodeWithText("No ads, no accounts, no tracking", substring = true).assertExists()
        compose.onNodeWithText("v1.3.0", substring = true).assertExists()
    }

    @Test
    fun theVibrateAndFlashRowsAreEachOneSwitch() {
        show(ToolsHomeUiState(vibrate = true, flash = true))
        compose.onNode(hasText("Vibrate") and isSwitch).performScrollTo().assertIsOn().performClick()
        compose.onNode(hasText("Flash the clock") and isSwitch).performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(ToolsHomeIntent.SetVibrate(false), ToolsHomeIntent.SetFlash(false)), intents)
    }

    @Test
    fun theQuietCuesStayLiveWithTheCueSoundsOff() {
        // For a muted phone or a quiet room: they don't rest with the cue sounds
        show(ToolsHomeUiState(soundOn = false, vibrate = true, flash = false))
        compose.onNode(hasText("Vibrate") and isSwitch).performScrollTo().assertIsOn().assertIsEnabled()
        compose.onNode(hasText("Flash the clock") and isSwitch).performScrollTo().assertIsOff().assertIsEnabled()
    }

    @Test
    fun aDeviceThatCannotVibrateHasNoVibrateRow() {
        show(ToolsHomeUiState(canVibrate = false))
        compose.onNode(hasText("Vibrate") and isSwitch).assertDoesNotExist()
        compose.onNode(hasText("Flash the clock") and isSwitch).assertExists()
    }

    @Test
    fun withNotificationsOffTheSectionOffersToTurnThemOn() {
        var allowed = 0
        compose.setContent {
            PokerTheme(reducedMotion = true) {
                ToolsHomeContent(
                    state = ToolsHomeUiState(notificationsOff = true),
                    onIntent = { intents += it },
                    onOpenTool = { opened += it },
                    versionName = "1.3.0",
                    onAllowNotifications = { allowed++ },
                )
            }
        }
        compose.onNodeWithText("Clock on the lock screen").performScrollTo().assertExists()
        compose.onNodeWithText("Turn on notifications").performScrollTo().performClick()
        assertEquals(1, allowed)
        assertEquals(emptyList<ToolsHomeIntent>(), intents)
    }

    @Test
    fun withNotificationsOnThereIsNothingToTurnOn() {
        show(ToolsHomeUiState(notificationsOff = false))
        compose.onNodeWithText("Turn on notifications").assertDoesNotExist()
        compose.onNodeWithText("Clock on the lock screen").assertDoesNotExist()
    }

    @Test
    fun theCueSoundsRowSaysOnOrOffAndThePackAndOpensCueSounds() {
        show(ToolsHomeUiState(soundOn = true))
        compose.onNodeWithText("Cue sounds").performScrollTo().assertExists()
        compose.onNodeWithText("On · Classic").assertExists()
        compose.onNodeWithText("Cue sounds").performClick()
        assertEquals(listOf<NavigationDestination>(NavigationDestination.CueSounds), opened)
    }

    @Test
    fun mutedTheCueSoundsRowSaysOff() {
        show(ToolsHomeUiState(soundOn = false))
        compose.onNodeWithText("Off · Classic").performScrollTo().assertExists()
    }

    @Test
    fun withNoSongsTheMusicRowSaysSoAndOpensTheMusic() {
        show(ToolsHomeUiState())
        compose.onNodeWithText("No songs yet").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Play music").assertDoesNotExist()
        compose.onNodeWithText("Music").performScrollTo().performClick()
        assertEquals(listOf<NavigationDestination>(NavigationDestination.Music), opened)
    }

    @Test
    fun withSongsTheMusicRowSaysWhatPlaysAndPlaysOrPauses() {
        show(ToolsHomeUiState(music = MusicSummary(songs = 3, playing = true, current = "Night Owl")))
        compose.onNodeWithText("Playing: Night Owl").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Pause music").performScrollTo().performClick()
        assertEquals(listOf<ToolsHomeIntent>(ToolsHomeIntent.ToggleMusic), intents)
    }

    @Test
    fun pausedTheMusicRowCountsTheSongs() {
        show(ToolsHomeUiState(music = MusicSummary(songs = 3, playing = false, current = "Night Owl")))
        compose.onNodeWithText("3 songs").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Play music").assertExists()
    }

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
